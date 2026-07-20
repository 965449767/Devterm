package com.devterm.terminal

import android.content.Context
import android.util.Log
import java.io.File

class NodeJsRuntime private constructor(
    val nodeDir: File,
    val binDir: File,
    val libDir: File
) {
    val nodeBinary: File get() = File(binDir, "node")
    val isReady: Boolean get() = nodeBinary.canExecute()

    private val homeDir: String get() = nodeDir.absolutePath.replace("/nodejs", "/home")
    private val npmGlobalBin: String get() = "${homeDir}/.npm-global/bin"

    fun envVars(shell: String): Map<String, String> = mapOf(
        "NODE_DIR" to nodeDir.absolutePath,
        "NODE_PATH" to "${libDir.absolutePath}/node_modules",
        "HOME" to homeDir,
        "TMPDIR" to nodeDir.absolutePath.replace("/nodejs", "/cache"),
        "LD_LIBRARY_PATH" to libDir.absolutePath,
        "PATH" to "/sbin:/system/sbin:/system/bin:/system/xbin",
        "SHELL" to shell,
        "PS1" to "$ ",
        "TERM" to "xterm-256color",
        "npm_config_prefix" to "${homeDir}/.npm-global"
    )

    companion object {
        private const val TAG = "DevTerm.NodeJs"
        private const val ASSET_PATH = "nodejs"
        private const val EXTRACT_VERSION = 7

        suspend fun extract(context: Context): NodeJsRuntime? {
            Log.i(TAG, "extract() called")
            val filesDir = context.filesDir
            Log.i(TAG, "filesDir=${filesDir.absolutePath}")
            val nodeDir = File(filesDir, "nodejs")
            val binDir = File(nodeDir, "bin")
            val libDir = File(nodeDir, "lib")
            val versionFile = File(nodeDir, ".extract_version")
            Log.i(TAG, "versionFile exists=${versionFile.exists()} nodeDir exists=${nodeDir.exists()} binDir/node canExecute=${File(binDir, "node").canExecute()}")

            // Re-extract if version marker missing/outdated
            if (versionFile.takeIf { it.exists() }?.readText()?.trim() == EXTRACT_VERSION.toString()) {
                if (binDir.resolve("node").canExecute()) {
                    Log.i(TAG, "Node.js already extracted (v$EXTRACT_VERSION) at ${binDir.absolutePath}")
                    return NodeJsRuntime(nodeDir, binDir, libDir)
                }
            }

            // Clean stale extraction
            if (nodeDir.exists()) {
                Log.i(TAG, "Cleaning stale extraction for v$EXTRACT_VERSION...")
                nodeDir.deleteRecursively()
            }

            nodeDir.mkdirs()
            binDir.mkdirs()
            libDir.mkdirs()

            File(filesDir, "cache").mkdirs()
            val homeDir = File(filesDir, "home")
            homeDir.mkdirs()
            File(homeDir, ".npm-global/bin").mkdirs()

            Log.i(TAG, "Extracting Node.js runtime...")

            // Extract node binary
            if (!extractAsset(context, "$ASSET_PATH/bin/node", File(binDir, "node"))) {
                Log.e(TAG, "Failed to extract node binary from assets")
                return null
            }
            Log.i(TAG, "Node binary extracted, setting executable...")

            // Try setExecutable, fallback to chmod
            val nodeFile = File(binDir, "node")
            if (!nodeFile.setExecutable(true, false)) {
                try {
                    Runtime.getRuntime().exec(arrayOf("chmod", "0755", nodeFile.absolutePath))
                        .waitFor()
                    Log.i(TAG, "chmod 755 succeeded")
                } catch (e: Exception) {
                    Log.e(TAG, "chmod failed: ${e.message}")
                }
            }

            if (!nodeFile.canExecute()) {
                Log.e(TAG, "node binary is not executable after all attempts")
                return null
            }

            // Extract npm shell wrappers
            val npmFile = File(binDir, "npm")
            if (!npmFile.exists()) {
                extractAsset(context, "$ASSET_PATH/bin/npm", npmFile)
                npmFile.setExecutable(true, false)
            }
            val npxFile = File(binDir, "npx")
            if (!npxFile.exists()) {
                extractAsset(context, "$ASSET_PATH/bin/npx", npxFile)
                npxFile.setExecutable(true, false)
            }

            // Extract npm node_modules
            val npmNodeModules = File(libDir, "node_modules/npm")
            if (!npmNodeModules.exists()) {
                extractAllAssets(context, "$ASSET_PATH/lib/node_modules/npm", npmNodeModules)
            }

            // Create .profile that defines node(), npm(), npx() shell functions
            // and adds npm global bin to PATH
            // Workaround: SELinux blocks execve on app_data_file, so run via system linker64
            val profileFile = File(homeDir, ".profile")
            val nodeReal = nodeFile.absolutePath
            val npmCliJs = "${libDir.absolutePath}/node_modules/npm/bin/npm-cli.js"
            val npxCliJs = "${libDir.absolutePath}/node_modules/npm/bin/npx-cli.js"
            val npmGlobalBinDir = "${homeDir.absolutePath}/.npm-global/bin"
            val funcBody = buildString {
                appendLine("node() { LD_LIBRARY_PATH=${libDir.absolutePath} /system/bin/linker64 ${nodeReal} \"\$@\"; }")
                appendLine("npm() { LD_LIBRARY_PATH=${libDir.absolutePath} /system/bin/linker64 ${nodeReal} ${npmCliJs} \"\$@\"; }")
                appendLine("npx() { LD_LIBRARY_PATH=${libDir.absolutePath} /system/bin/linker64 ${nodeReal} ${npxCliJs} \"\$@\"; }")
                appendLine("export PATH=\"\$HOME/.npm-global/bin:\$PATH\"")
                appendLine("export PS1='\$ '")
                appendLine("alias clear=\"printf '\\033[2J\\033[H'\"")
            }
            if (!profileFile.exists() || profileFile.readText().trim() != funcBody.trim()) {
                profileFile.writeText(funcBody)
                Log.i(TAG, "Created .profile at ${profileFile.absolutePath}")
            }

            // Extract lib .so files
            extractAllAssets(context, "$ASSET_PATH/lib", libDir)

            // Create SONAME symlinks (match NEEDED names from readelf)
            createSymlink(libDir, "libicui18n.so.78.3", "libicui18n.so.78")
            createSymlink(libDir, "libicuuc.so.78.3", "libicuuc.so.78")
            createSymlink(libDir, "libicudata.so.78.3", "libicudata.so.78")
            createSymlink(libDir, "libsqlite3.so.3.53.3", "libsqlite3.so")
            createSymlink(libDir, "libz.so.1.3.2", "libz.so.1")

            // Write version marker
            try {
                versionFile.writeText(EXTRACT_VERSION.toString())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write version marker: ${e.message}")
            }

            Log.i(TAG, "Node.js extraction complete, size: ${nodeFile.length()} bytes")

            // Diagnostics: test linker64 approach (this should work)
            Thread {
                try {
                    val pb = ProcessBuilder("/system/bin/linker64", nodeFile.absolutePath, "--version")
                    pb.environment()["LD_LIBRARY_PATH"] = libDir.absolutePath
                    pb.redirectErrorStream(true)
                    val proc = pb.start()
                    val out = proc.inputStream.bufferedReader().readText().trim()
                    val exit = proc.waitFor()
                    Log.i(TAG, "linker64 node --version: exit=$exit out=[$out]")
                } catch (e: Exception) {
                    Log.e(TAG, "linker64 diagnostic failed: ${e.message}")
                }
            }.apply { name = "NodeDiag"; start() }

            return NodeJsRuntime(nodeDir, binDir, libDir)
        }

        private fun extractAsset(context: Context, assetPath: String, targetFile: File): Boolean {
            try {
                context.assets.open(assetPath).use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (targetFile.exists() && targetFile.length() > 0) {
                    return true
                }
                Log.w(TAG, "extractAsset $assetPath -> ${targetFile.absolutePath}: zero size")
                return false
            } catch (e: Exception) {
                Log.e(TAG, "extractAsset $assetPath failed: ${e.message}")
                return false
            }
        }

        private fun createSymlink(baseDir: File, target: String, link: String) {
            try {
                val linkFile = File(baseDir, link)
                if (!linkFile.exists()) {
                    java.nio.file.Files.createSymbolicLink(
                        linkFile.toPath(),
                        java.nio.file.Paths.get(target)
                    )
                    Log.i(TAG, "Created symlink: $link -> $target")
                }
            } catch (e: Exception) {
                Log.w(TAG, "symlink $link -> $target failed: ${e.message}")
                // Fallback: copy file instead
                try {
                    val targetFile = File(baseDir, target)
                    if (targetFile.exists()) {
                        targetFile.copyTo(File(baseDir, link))
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "symlink fallback copy failed: ${e2.message}")
                }
            }
        }

        private fun extractAllAssets(context: Context, assetDir: String, targetDir: File) {
            try {
                val assets = context.assets.list(assetDir) ?: return
                for (asset in assets) {
                    val subAssetPath = "$assetDir/$asset"
                    val targetFile = File(targetDir, asset)
                    if (context.assets.list(subAssetPath)?.isNotEmpty() == true) {
                        targetFile.mkdirs()
                        extractAllAssets(context, subAssetPath, targetFile)
                    } else {
                        extractAsset(context, subAssetPath, targetFile)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "extractAllAssets $assetDir failed: ${e.message}")
            }
        }
    }
}
