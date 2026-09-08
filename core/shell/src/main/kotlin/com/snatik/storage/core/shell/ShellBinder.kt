package com.snatik.storage.core.shell

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

/**
 * The wire protocol between the app and the process Shizuku starts for us, written by hand so
 * the project stays Kotlin only. One transaction starts a command and hands back pipes for its
 * streams, another waits for its exit code.
 */
internal object ShellBinderProtocol {
    const val DESCRIPTOR = "com.snatik.storage.core.shell.ShellService"
    const val TRANSACTION_EXEC = IBinder.FIRST_CALL_TRANSACTION + 1
    const val TRANSACTION_WAIT = IBinder.FIRST_CALL_TRANSACTION + 2
    const val TRANSACTION_KILL = IBinder.FIRST_CALL_TRANSACTION + 3
    const val TRANSACTION_PING = IBinder.FIRST_CALL_TRANSACTION + 4

    /** Shizuku asks the service to go away with this code (its AIDL declares destroy() = 16777114). */
    const val TRANSACTION_DESTROY = IBinder.FIRST_CALL_TRANSACTION + 16777114
}

/**
 * Runs inside the Shizuku-spawned process as the shell uid. Shizuku instantiates this class by
 * name, so it must stay public with a no-argument constructor.
 */
class ShellUserService : Binder() {

    private class Job(val process: Process)

    private val jobs = ConcurrentHashMap<Int, Job>()
    private val ids = AtomicInteger(1)

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        when (code) {
            ShellBinderProtocol.TRANSACTION_PING -> {
                data.enforceInterface(ShellBinderProtocol.DESCRIPTOR)
                reply?.writeNoException()
                reply?.writeInt(android.os.Process.myUid())
                return true
            }
            ShellBinderProtocol.TRANSACTION_EXEC -> {
                data.enforceInterface(ShellBinderProtocol.DESCRIPTOR)
                val command = data.readString().orEmpty()
                val hasStdin = data.readInt() == 1
                val stdinFd = if (hasStdin) data.readFileDescriptor() else null
                val process = ProcessBuilder("sh", "-c", command).start()
                val id = ids.getAndIncrement()
                jobs[id] = Job(process)
                val stdout = ParcelFileDescriptor.createPipe()
                val stderr = ParcelFileDescriptor.createPipe()
                pump(process.inputStream, ParcelFileDescriptor.AutoCloseOutputStream(stdout[1]))
                pump(process.errorStream, ParcelFileDescriptor.AutoCloseOutputStream(stderr[1]))
                if (stdinFd != null) {
                    pump(ParcelFileDescriptor.AutoCloseInputStream(stdinFd), process.outputStream)
                } else {
                    process.outputStream.close()
                }
                reply?.writeNoException()
                reply?.writeInt(id)
                if (reply != null) {
                    stdout[0].writeToParcel(reply, Parcelable.PARCELABLE_WRITE_RETURN_VALUE)
                    stderr[0].writeToParcel(reply, Parcelable.PARCELABLE_WRITE_RETURN_VALUE)
                }
                return true
            }
            ShellBinderProtocol.TRANSACTION_WAIT -> {
                data.enforceInterface(ShellBinderProtocol.DESCRIPTOR)
                val id = data.readInt()
                val job = jobs.remove(id)
                val exit = job?.process?.waitFor() ?: -1
                reply?.writeNoException()
                reply?.writeInt(exit)
                return true
            }
            ShellBinderProtocol.TRANSACTION_KILL -> {
                data.enforceInterface(ShellBinderProtocol.DESCRIPTOR)
                val id = data.readInt()
                jobs.remove(id)?.process?.destroyForcibly()
                reply?.writeNoException()
                return true
            }
            ShellBinderProtocol.TRANSACTION_DESTROY -> {
                jobs.values.forEach { it.process.destroyForcibly() }
                exitProcess(0)
            }
            else -> return super.onTransact(code, data, reply, flags)
        }
    }

    private fun pump(input: InputStream, output: OutputStream) {
        Thread {
            try {
                input.use { i -> output.use { o -> i.copyTo(o, 64 * 1024) } }
            } catch (_: Exception) {
                // The reader went away; nothing to do.
            }
        }.apply { isDaemon = true }.start()
    }
}

/** Client side: talks to a [ShellUserService] over the binder Shizuku handed us. */
class RemoteShellExecutor(private val binder: IBinder, override val tier: PrivilegeTier = PrivilegeTier.SHIZUKU) : ShellExecutor {

    val isAlive: Boolean get() = binder.isBinderAlive

    /** Uid of the remote process, 2000 for shell. */
    fun ping(): Int {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(ShellBinderProtocol.DESCRIPTOR)
            binder.transact(ShellBinderProtocol.TRANSACTION_PING, data, reply, 0)
            reply.readException()
            return reply.readInt()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    override suspend fun start(command: String): ShellProcess = withContext(Dispatchers.IO) {
        val stdinPipe = ParcelFileDescriptor.createPipe()
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(ShellBinderProtocol.DESCRIPTOR)
            data.writeString(command)
            data.writeInt(1)
            data.writeFileDescriptor(stdinPipe[0].fileDescriptor)
            binder.transact(ShellBinderProtocol.TRANSACTION_EXEC, data, reply, 0)
            reply.readException()
            val id = reply.readInt()
            val stdout = ParcelFileDescriptor.CREATOR.createFromParcel(reply)
            val stderr = ParcelFileDescriptor.CREATOR.createFromParcel(reply)
            RemoteShellProcess(binder, id, stdinPipe[1], stdout, stderr)
        } finally {
            stdinPipe[0].close()
            data.recycle()
            reply.recycle()
        }
    }
}

private class RemoteShellProcess(
    private val binder: IBinder,
    private val id: Int,
    stdinFd: ParcelFileDescriptor,
    stdoutFd: ParcelFileDescriptor,
    stderrFd: ParcelFileDescriptor,
) : ShellProcess {
    override val stdin: OutputStream = ParcelFileDescriptor.AutoCloseOutputStream(stdinFd)
    override val stdout: InputStream = ParcelFileDescriptor.AutoCloseInputStream(stdoutFd)
    override val stderr: InputStream = ParcelFileDescriptor.AutoCloseInputStream(stderrFd)

    override suspend fun waitFor(): Int = runInterruptible(Dispatchers.IO) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(ShellBinderProtocol.DESCRIPTOR)
            data.writeInt(id)
            binder.transact(ShellBinderProtocol.TRANSACTION_WAIT, data, reply, 0)
            reply.readException()
            reply.readInt()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    override fun kill() {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(ShellBinderProtocol.DESCRIPTOR)
            data.writeInt(id)
            binder.transact(ShellBinderProtocol.TRANSACTION_KILL, data, reply, 0)
        } catch (_: Exception) {
        } finally {
            data.recycle()
            reply.recycle()
        }
        runCatching { stdin.close() }
    }
}
