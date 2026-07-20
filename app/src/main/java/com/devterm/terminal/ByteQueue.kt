package com.devterm.terminal

class ByteQueue(private val capacity: Int) {
    private val buffer = ByteArray(capacity)
    private var head = 0
    private var tail = 0
    private var count = 0
    private var closed = false

    @Synchronized
    fun write(data: ByteArray) {
        if (closed) return
        var written = 0
        while (written < data.size) {
            while (count >= capacity) {
                (this as Object).wait()
                if (closed) return
            }
            val avail = minOf(capacity - count, data.size - written)
            for (i in 0 until avail) {
                buffer[(tail + i) % capacity] = data[written + i]
            }
            tail = (tail + avail) % capacity
            count += avail
            written += avail
        }
        (this as Object).notifyAll()
    }

    @Synchronized
    fun read(buf: ByteArray): ByteArray? {
        while (count == 0) {
            if (closed) return null
            (this as Object).wait()
        }
        val avail = minOf(count, buf.size)
        val result = ByteArray(avail)
        for (i in 0 until avail) {
            result[i] = buffer[(head + i) % capacity]
        }
        head = (head + avail) % capacity
        count -= avail
        (this as Object).notifyAll()
        return result
    }

    @Synchronized
    fun close() {
        closed = true
        (this as Object).notifyAll()
    }
}
