package com.snatik.storage.app.feature.apps

import com.snatik.storage.core.apps.CompileMode
import com.snatik.storage.core.apps.DexoptFileResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One compile action's result: the status change, footprint diff, per-dex detail and raw output. */
data class ArtOp(
    val mode: CompileMode,
    val fromStatus: String?,
    val toStatus: String?,
    /** Summed dex2oat artifact sizes reported by the compiler (before → after). */
    val artifactBefore: Long,
    val artifactAfter: Long,
    /** StorageStats app total (before → after). */
    val beforeTotal: Long,
    val afterTotal: Long,
    val files: List<DexoptFileResult>,
    val finalStatus: String?,
    val raw: String,
    val millis: Long,
    val ts: Long = System.currentTimeMillis(),
)

/** Session-scoped log of ART operations per package, shared by the ART screen and its history screen. */
class ArtOpLog {
    private val flows = HashMap<String, MutableStateFlow<List<ArtOp>>>()

    private fun flowFor(pkg: String) = flows.getOrPut(pkg) { MutableStateFlow(emptyList()) }

    fun ops(pkg: String): StateFlow<List<ArtOp>> = flowFor(pkg)

    fun add(pkg: String, op: ArtOp) {
        val f = flowFor(pkg)
        f.value = listOf(op) + f.value
    }
}
