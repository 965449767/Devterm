package com.devterm.terminal.core.backend

interface Backend {
    fun start(callback: BackendCallback)
    fun write(data: ByteArray)
    fun resize(cols: Int, rows: Int)
    fun stop()
}

interface BackendCallback {
    fun onOutput(data: ByteArray)
    fun onExit(exitCode: Int)
    fun onTitleChanged(title: String)
}
