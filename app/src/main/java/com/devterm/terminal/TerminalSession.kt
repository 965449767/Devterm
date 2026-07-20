package com.devterm.terminal

import java.io.File

class TerminalSession(
    private val cwd: String? = null,
    private val filesDirPath: String = "/data/data/com.devterm/files",
    private val envVars: Map<String, String>? = null,
    private val nodeRuntime: NodeJsRuntime? = null
) {
    private var process: java.lang.Process? = null
    @Volatile
    private var isRunning = false
    private var readerThread: Thread? = null
    private var writerThread: Thread? = null
    private var waiterThread: Thread? = null

    var onOutput: ((ByteArray) -> Unit)? = null
    var onExit: ((Int) -> Unit)? = null

    private val probedShell: String by lazy { "/system/bin/sh" }

    fun start(
        rows: Int,
        cols: Int,
        terminalEmulator: TerminalEmulator
    ) {
        File("${filesDirPath}/home").mkdirs()
        File("${filesDirPath}/cache").mkdirs()

        val profileFile = File("${filesDirPath}/home", ".profile")
        if (!profileFile.exists()) {
            profileFile.writeText("alias clear=\"printf '\\033[2J\\033[H'\"\n")
        }

        val shell = probedShell
        val pb = ProcessBuilder(shell, "-i")
        val env = pb.environment()
        env["TERM"] = "xterm-256color"
        env["HOME"] = "${filesDirPath}/home"
        env["TMPDIR"] = "${filesDirPath}/cache"
        env["PATH"] = "/sbin:/system/sbin:/system/bin:/system/xbin"
        env["PS1"] = "$ "
        env["SHELL"] = shell
        env["LINES"] = rows.toString()
        env["COLUMNS"] = cols.toString()
        env["ENV"] = "${filesDirPath}/home/.profile"
        if (nodeRuntime?.isReady == true) {
            env.putAll(nodeRuntime.envVars(shell))
        }
        if (envVars != null) env.putAll(envVars)
        env["TERM"] = "xterm-256color"
        pb.directory(File(cwd ?: filesDirPath))
        pb.redirectErrorStream(true)

        process = try {
            pb.start()
        } catch (e: Exception) {
            return
        }

        isRunning = true
        initReaderThread(terminalEmulator)
        initWriterThread()
        initWaiterThread()
    }

    private fun initReaderThread(terminalEmulator: TerminalEmulator) {
        readerThread = Thread {
            val buf = ByteArray(8192)
            try {
                val inputStream = process?.inputStream ?: return@Thread
                while (isRunning) {
                    val n = inputStream.read(buf)
                    if (n <= 0) break
                    terminalEmulator.writeInput(buf, 0, n)
                }
            } catch (_: Exception) {}
            isRunning = false
        }.apply {
            name = "TermReader"
            start()
        }
    }

    private fun initWriterThread() {
        writerThread = Thread {
            val outputStream = process?.outputStream ?: return@Thread
            val buf = ByteArray(4096)
            try {
                while (isRunning) {
                    val data = writeQueue.read(buf) ?: break
                    outputStream.write(data)
                    outputStream.flush()
                }
            } catch (_: Exception) {}
        }.apply {
            name = "TermWriter"
            start()
        }
    }

    private fun initWaiterThread() {
        waiterThread = Thread {
            try {
                val proc = process ?: return@Thread
                val exitCode = proc.waitFor()
                isRunning = false
                writeQueue.close()
                onExit?.invoke(exitCode)
            } catch (_: Exception) {}
        }.apply {
            name = "TermWaiter"
            start()
        }
    }

    fun write(data: ByteArray) {
        writeQueue.write(data)
    }

    fun resize(rows: Int, cols: Int) {}

    fun startAutoCheckpoint(
        terminalEmulator: TerminalEmulator,
        intervalMs: Long = 30_000L
    ) {
        Thread {
            val checkpointDir = File(filesDirPath, "checkpoint")
            while (isRunning) {
                try {
                    Thread.sleep(intervalMs)
                    if (!isRunning) break
                    TerminalCheckpoint.save(terminalEmulator, checkpointDir)
                } catch (_: InterruptedException) {
                    break
                } catch (_: Exception) {}
            }
        }.apply { name = "TermCheckpoint"; isDaemon = true; start() }
    }

    fun destroy() {
        isRunning = false
        process?.destroy()
        process = null
        writeQueue.close()
    }

    val isAlive: Boolean get() = isRunning

    private val writeQueue = ByteQueue(4096)
}
