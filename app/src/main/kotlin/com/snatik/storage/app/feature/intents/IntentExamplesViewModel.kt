package com.snatik.storage.app.feature.intents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.core.intents.IntentSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Resolves how many components on this device would react to each example intent. */
class IntentExamplesViewModel(private val sender: IntentSender) : ViewModel() {

    /** Example name -> number of matching components, once resolved. */
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    init {
        viewModelScope.launch {
            val counts = withContext(Dispatchers.IO) {
                IntentExamples.all.associate { ex ->
                    ex.name to runCatching { sender.resolve(ex.spec).size }.getOrDefault(0)
                }
            }
            _counts.value = counts
        }
    }
}
