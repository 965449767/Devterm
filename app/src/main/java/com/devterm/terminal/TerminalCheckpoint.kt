package com.devterm.terminal

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CellState(
    val char: Char,
    val fgColor: Long,
    val bgColor: Long,
    val bold: Boolean,
    val dim: Boolean,
    val italic: Boolean,
    val underline: Int,
    val blink: Boolean,
    val reverse: Boolean,
    val conceal: Boolean,
    val strike: Boolean,
    val width: Int
)

data class CheckpointState(
    val rows: Int,
    val cols: Int,
    val cursorRow: Int,
    val cursorCol: Int,
    val cursorVisible: Boolean,
    val cursorStyle: Int,
    val scrollTop: Int,
    val scrollBottom: Int,
    val savedRow: Int,
    val savedCol: Int,
    val applicationCursorKeys: Boolean,
    val originMode: Boolean,
    val autoWrap: Boolean,
    val insertMode: Boolean,
    val bold: Boolean,
    val dim: Boolean,
    val italic: Boolean,
    val underline: Int,
    val blink: Boolean,
    val reverse: Boolean,
    val conceal: Boolean,
    val strike: Boolean,
    val fgColor: Long,
    val bgColor: Long,
    val screen: List<List<CellState>>,
    val scrollback: List<List<CellState>>
)

fun CheckpointState.toJson(): String {
    val obj = JSONObject()
    obj.put("v", 1)
    obj.put("rows", rows)
    obj.put("cols", cols)
    obj.put("cr", cursorRow)
    obj.put("cc", cursorCol)
    obj.put("cv", cursorVisible)
    obj.put("cs", cursorStyle)
    obj.put("st", scrollTop)
    obj.put("sb", scrollBottom)
    obj.put("sr", savedRow)
    obj.put("sc", savedCol)
    obj.put("ack", applicationCursorKeys)
    obj.put("om", originMode)
    obj.put("aw", autoWrap)
    obj.put("im", insertMode)
    obj.put("bd", bold)
    obj.put("dm", dim)
    obj.put("it", italic)
    obj.put("ul", underline)
    obj.put("bk", blink)
    obj.put("rv", reverse)
    obj.put("cn", conceal)
    obj.put("sk", strike)
    obj.put("fg", fgColor)
    obj.put("bg", bgColor)
    obj.put("screen", cellsToJson(screen))
    obj.put("scrollback", cellsToJson(scrollback))
    return obj.toString()
}

private fun cellsToJson(layers: List<List<CellState>>): JSONArray {
    val arr = JSONArray()
    for (row in layers) {
        val rowArr = JSONArray()
        for (cell in row) {
            val co = JSONObject()
            co.put("c", cell.char.toString())
            co.put("f", cell.fgColor)
            co.put("b", cell.bgColor)
            co.put("w", cell.width)
            if (cell.bold) co.put("bd", true)
            if (cell.dim) co.put("dm", true)
            if (cell.italic) co.put("it", true)
            if (cell.underline != 0) co.put("ul", cell.underline)
            if (cell.blink) co.put("bk", true)
            if (cell.reverse) co.put("rv", true)
            if (cell.conceal) co.put("cn", true)
            if (cell.strike) co.put("sk", true)
            rowArr.put(co)
        }
        arr.put(rowArr)
    }
    return arr
}

fun parseCheckpoint(json: String): CheckpointState? {
    return try {
        val obj = JSONObject(json)
        val v = obj.optInt("v", 0)
        if (v != 1) return null

        CheckpointState(
            rows = obj.getInt("rows"),
            cols = obj.getInt("cols"),
            cursorRow = obj.getInt("cr"),
            cursorCol = obj.getInt("cc"),
            cursorVisible = obj.getBoolean("cv"),
            cursorStyle = obj.getInt("cs"),
            scrollTop = obj.getInt("st"),
            scrollBottom = obj.getInt("sb"),
            savedRow = obj.getInt("sr"),
            savedCol = obj.getInt("sc"),
            applicationCursorKeys = obj.getBoolean("ack"),
            originMode = obj.getBoolean("om"),
            autoWrap = obj.getBoolean("aw"),
            insertMode = obj.getBoolean("im"),
            bold = obj.getBoolean("bd"),
            dim = obj.getBoolean("dm"),
            italic = obj.getBoolean("it"),
            underline = obj.getInt("ul"),
            blink = obj.getBoolean("bk"),
            reverse = obj.getBoolean("rv"),
            conceal = obj.getBoolean("cn"),
            strike = obj.getBoolean("sk"),
            fgColor = obj.getLong("fg"),
            bgColor = obj.getLong("bg"),
            screen = parseCells(obj.getJSONArray("screen")),
            scrollback = parseCells(obj.getJSONArray("scrollback"))
        )
    } catch (e: Exception) {
        null
    }
}

private fun parseCells(arr: JSONArray): List<List<CellState>> {
    val result = mutableListOf<List<CellState>>()
    for (i in 0 until arr.length()) {
        val rowArr = arr.getJSONArray(i)
        val row = mutableListOf<CellState>()
        for (j in 0 until rowArr.length()) {
            val co = rowArr.getJSONObject(j)
            val ch = co.getString("c")
            row.add(CellState(
                char = if (ch.isNotEmpty()) ch[0] else ' ',
                fgColor = co.getLong("f"),
                bgColor = co.getLong("b"),
                width = co.getInt("w"),
                bold = co.optBoolean("bd"),
                dim = co.optBoolean("dm"),
                italic = co.optBoolean("it"),
                underline = co.optInt("ul", 0),
                blink = co.optBoolean("bk"),
                reverse = co.optBoolean("rv"),
                conceal = co.optBoolean("cn"),
                strike = co.optBoolean("sk")
            ))
        }
        result.add(row)
    }
    return result
}

object TerminalCheckpoint {
    private const val FILE_NAME = "terminal_checkpoint.json"
    private const val VERSION_KEY = "checkpoint_version"
    private var checkpointVersion = 1

    fun save(terminalEmulator: TerminalEmulator, dir: File) {
        val state = terminalEmulator.captureCheckpoint()
        val json = state.toJson()
        val file = File(dir, FILE_NAME)
        file.parentFile?.mkdirs()
        file.writeText(json)
    }

    fun load(terminalEmulator: TerminalEmulator, dir: File): Boolean {
        val file = File(dir, FILE_NAME)
        if (!file.exists()) return false
        val json = file.readText()
        val state = parseCheckpoint(json) ?: return false
        terminalEmulator.restoreFromCheckpoint(state)
        file.delete()
        return true
    }
}
