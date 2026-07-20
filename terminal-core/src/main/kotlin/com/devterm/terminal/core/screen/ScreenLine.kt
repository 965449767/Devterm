package com.devterm.terminal.core.screen

class ScreenLine(
    val chars: CharArray,
    val fg: IntArray,
    val bg: IntArray,
    val flags: ByteArray
)
