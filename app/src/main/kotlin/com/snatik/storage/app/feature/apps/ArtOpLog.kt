package com.snatik.storage.app.feature.apps

import com.snatik.storage.core.apps.CompileMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One compile action's before/after footprint diff, kept for the session's history. */
data class ArtOp(
    val mode: CompileMode,
    val fromStatus: String?,
    val toStatus: String?,
    val beforeCode: Long,
    val afterCode: Long,
    val beforeTotal: Long,
    val afterTotal: Long,
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
