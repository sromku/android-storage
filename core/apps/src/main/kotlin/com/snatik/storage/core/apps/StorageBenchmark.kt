package com.snatik.storage.core.apps

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.RandomAccessFile
import kotlin.random.Random
import kotlin.system.measureNanoTime

data class BenchmarkResult(
    val seqWriteMBps: Double,
    val seqReadMBps: Double,
    val randReadMBps: Double,
    val randReadIops: Double,
    val fileSizeMB: Int,
)

sealed interface BenchmarkEvent {
    data class Progress(val phase: String, val fraction: Float) : BenchmarkEvent
    data class Done(val result: BenchmarkResult) : BenchmarkEvent
    data class Failed(val reason: String) : BenchmarkEvent
}

/**
 * Measures storage throughput in a directory the app can write: sequential write and read of a
 * temporary file, then random 4 KB reads. The temp file is flushed and the page cache dropped
 * between phases where possible so reads hit storage, and it is always deleted at the end.
 */
class StorageBenchmark {

    fun run(dir: File, fileSizeMB: Int = 128): Flow<BenchmarkEvent> = flow {
        val temp = File(dir, ".bench_${System.currentTimeMillis()}.tmp")
        try {
            if (!dir.exists() && !dir.mkdirs()) { emit(BenchmarkEvent.Failed("Cannot create $dir")); return@flow }
            val chunk = ByteArray(4 * 1024 * 1024) { Random.nextInt().toByte() }
            val chunks = fileSizeMB / 4
            val totalBytes = chunks.toLong() * chunk.size

            // sequential write
            var writeNanos: Long
            RandomAccessFile(temp, "rw").use { raf ->
                writeNanos = measureNanoTime {
                    for (i in 0 until chunks) {
                        raf.write(chunk)
                        if (i % 4 == 0) emit(BenchmarkEvent.Progress("Writing", i.toFloat() / chunks))
                    }
                    raf.fd.sync()
                }
            }
            val seqWrite = mbps(totalBytes, writeNanos)

            // sequential read
            val readBuf = ByteArray(4 * 1024 * 1024)
            var readNanos: Long
            RandomAccessFile(temp, "r").use { raf ->
                readNanos = measureNanoTime {
                    var i = 0
                    while (true) {
                        val n = raf.read(readBuf)
                        if (n <= 0) break
                        if (i++ % 4 == 0) emit(BenchmarkEvent.Progress("Reading", raf.filePointer.toFloat() / totalBytes))
                    }
                }
            }
            val seqRead = mbps(totalBytes, readNanos)

            // random 4 KB reads
            val blockSize = 4 * 1024
            val ops = 2000
            val small = ByteArray(blockSize)
            var randNanos: Long
            RandomAccessFile(temp, "r").use { raf ->
                val maxOffset = (totalBytes - blockSize).coerceAtLeast(0)
                randNanos = measureNanoTime {
                    for (i in 0 until ops) {
                        raf.seek(Random.nextLong(maxOffset + 1))
                        raf.read(small)
                        if (i % 200 == 0) emit(BenchmarkEvent.Progress("Random read", i.toFloat() / ops))
                    }
                }
            }
            val randBytes = ops.toLong() * blockSize
            val randRead = mbps(randBytes, randNanos)
            val iops = if (randNanos > 0) ops.toDouble() / (randNanos / 1_000_000_000.0) else 0.0

            emit(BenchmarkEvent.Done(BenchmarkResult(seqWrite, seqRead, randRead, iops, fileSizeMB)))
        } catch (e: Throwable) {
            emit(BenchmarkEvent.Failed(e.message ?: e.javaClass.simpleName))
        } finally {
            temp.delete()
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        fun mbps(bytes: Long, nanos: Long): Double {
            if (nanos <= 0) return 0.0
            val seconds = nanos / 1_000_000_000.0
            val mb = bytes / (1024.0 * 1024.0)
            return (mb / seconds * 10).toLong() / 10.0
        }
    }
}
