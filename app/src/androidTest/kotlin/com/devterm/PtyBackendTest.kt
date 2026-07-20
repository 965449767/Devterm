package com.devterm

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devterm.terminal.BackendCapabilities
import com.devterm.terminal.PtyBackend
import com.devterm.terminal.core.backend.Backend
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * PTY 后端测试（需要真机运行）。
 *
 * 这些测试需要：
 * 1. libpty.so 已编译并打包到 APK 中
 * 2. 设备有 /dev/ptmx 访问权限
 * 3. 设备支持 execve（部分厂商 ROM 可能限制）
 *
 * 如果 PtyBackend.isAvailable() 返回 false，测试会自动跳过。
 */
@RunWith(AndroidJUnit4::class)
class PtyBackendTest {

    @Test
    fun testPtyAvailability() {
        // 检查 PTY 是否可用
        val available = try {
            PtyBackend.isAvailable()
        } catch (e: UnsatisfiedLinkError) {
            false
        }
        // 这个测试只是打印 PTY 可用性信息
        println("PtyBackend available: $available")
    }

    @Test
    fun testPtyCapabilities() {
        assumeTrue(isPtyAvailable())

        val backend = PtyBackend("/system/bin/sh", emptyArray(), "/")
        val caps = backend.capabilities

        assertTrue("PTY 后端应标记 isPty=true", caps.isPty)
        assertFalse("PTY 不需要本地回显", caps.needsLocalEcho)
        assertTrue("PTY 应支持信号", caps.supportsSignals)
        assertTrue("PTY 应支持 resize", caps.supportsResize)
    }

    @Test
    fun testPtyProcessStart() {
        assumeTrue(isPtyAvailable())

        val latch = CountDownLatch(1)
        val outputCount = AtomicInteger(0)

        val backend = PtyBackend("/system/bin/sh", emptyArray(), "/")
        backend.start(object : Backend.BackendCallback {
            override fun onOutput(data: ByteArray) {
                outputCount.addAndGet(data.size)
                if (outputCount.get() > 0) {
                    latch.countDown()
                }
            }
            override fun onClosed() {}
        })

        // 等待最多 2 秒收到输出
        val received = latch.await(2, TimeUnit.SECONDS)
        backend.stop()

        assertTrue("应在 2 秒内收到 shell 输出", received)
    }

    @Test
    fun testPtyResize() {
        assumeTrue(isPtyAvailable())

        val backend = PtyBackend("/system/bin/sh", emptyArray(), "/")
        backend.start(object : Backend.BackendCallback {
            override fun onOutput(data: ByteArray) {}
            override fun onClosed() {}
        })

        // resize 不应抛出异常
        backend.resize(80, 24)
        backend.resize(120, 40)

        backend.stop()
    }

    @Test
    fun testPtyWrite() {
        assumeTrue(isPtyAvailable())

        val latch = CountDownLatch(1)
        val output = StringBuilder()

        val backend = PtyBackend("/system/bin/sh", emptyArray(), "/")
        backend.start(object : Backend.BackendCallback {
            override fun onOutput(data: ByteArray) {
                output.append(String(data))
                // 写入 echo 命令后应该能看到回显
                if (output.contains("hello_pty_test")) {
                    latch.countDown()
                }
            }
            override fun onClosed() {}
        })

        // 写入 echo 命令
        backend.write("echo hello_pty_test\n".toByteArray())

        val received = latch.await(3, TimeUnit.SECONDS)
        backend.stop()

        assertTrue("echo 命令输出应包含测试字符串", received)
    }

    @Test
    fun testPtySignal() {
        assumeTrue(isPtyAvailable())
        assumeTrue(PtyBackend.isAvailable())  // 再次确认

        val backend = PtyBackend("/system/bin/sh", emptyArray(), "/")
        backend.start(object : Backend.BackendCallback {
            override fun onOutput(data: ByteArray) {}
            override fun onClosed() {}
        })

        // 发送 SIGINT (Ctrl+C)，不应抛出异常
        // 注意：PtyBackend 可能通过 write("\u0003") 来模拟 Ctrl+C
        backend.write("\u0003".toByteArray())

        backend.stop()
    }

    @Test
    fun testPtyStop() {
        assumeTrue(isPtyAvailable())

        val backend = PtyBackend("/system/bin/sh", emptyArray(), "/")
        backend.start(object : Backend.BackendCallback {
            override fun onOutput(data: ByteArray) {}
            override fun onClosed() {}
        })

        // 多次调用 stop 不应抛出异常
        backend.stop()
        backend.stop()
    }

    @Test
    fun testBackendFactoryPrefersPty() {
        assumeTrue(isPtyAvailable())

        // 如果 PTY 可用，BackendFactory 应该返回 PtyBackend
        val factory = com.devterm.terminal.BackendFactory("", null)
        val backend = factory.create("/system/bin/sh", emptyArray(), "/")

        assertTrue("PTY 可用时应返回 PtyBackend",
            backend is PtyBackend || !PtyBackend.isAvailable())
    }

    /**
     * 检查 PTY 是否可用，用于跳过测试。
     */
    private fun isPtyAvailable(): Boolean {
        return try {
            PtyBackend.isAvailable()
        } catch (e: UnsatisfiedLinkError) {
            println("libpty.so 未加载，跳过 PTY 测试: ${e.message}")
            false
        } catch (e: Exception) {
            println("PTY 不可用，跳过测试: ${e.message}")
            false
        }
    }
}
