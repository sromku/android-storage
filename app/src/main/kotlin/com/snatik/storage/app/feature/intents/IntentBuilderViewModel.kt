package com.snatik.storage.app.feature.intents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.navigation.Route
import com.snatik.storage.core.intents.Extra
import com.snatik.storage.core.intents.ExtraType
import com.snatik.storage.core.intents.IntentPresets
import com.snatik.storage.core.intents.IntentSender
import com.snatik.storage.core.intents.IntentSpec
import com.snatik.storage.core.intents.ResolvedTarget
import com.snatik.storage.core.intents.SendAs
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BuilderForm(
    val sendAs: SendAs = SendAs.ACTIVITY,
    val action: String = "",
    val data: String = "",
    val type: String = "",
    val categories: String = "",
    val packageName: String = "",
    val className: String = "",
    val flags: Int = 0,
    val extras: List<Extra> = emptyList(),
) {
    fun toSpec() = IntentSpec(
        action = action.ifBlank { null },
        data = data.ifBlank { null },
        type = type.ifBlank { null },
        categories = categories.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        packageName = packageName.ifBlank { null },
        className = className.ifBlank { null },
        flags = flags,
        extras = extras.filter { it.key.isNotBlank() },
        sendAs = sendAs,
    )

    companion object {
        fun from(spec: IntentSpec) = BuilderForm(
            sendAs = spec.sendAs,
            action = spec.action.orEmpty(),
            data = spec.data.orEmpty(),
            type = spec.type.orEmpty(),
            categories = spec.categories.joinToString(", "),
            packageName = spec.packageName.orEmpty(),
            className = spec.className.orEmpty(),
            flags = spec.flags,
            extras = spec.extras,
        )
    }
}

data class BuilderUiState(
    val form: BuilderForm = BuilderForm(),
    val targets: List<ResolvedTarget>? = null,
    val presetId: Long? = null,
    val presetName: String = "",
    val saving: Boolean = false,
)

class IntentBuilderViewModel(route: Route.IntentBuilder, private val sender: IntentSender, private val presets: IntentPresets) : ViewModel() {

    private val _state = MutableStateFlow(BuilderUiState())
    val state: StateFlow<BuilderUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            presets.load()
            val preset = route.presetId?.let { id -> presets.presets.value.firstOrNull { it.id == id } }
            val spec = preset?.spec ?: route.specJson?.let { runCatching { IntentSpec.fromJson(it) }.getOrNull() }
            if (spec != null) _state.update { it.copy(form = BuilderForm.from(spec), presetId = preset?.id, presetName = preset?.name.orEmpty()) }
            resolve()
        }
    }

    fun update(form: BuilderForm) = _state.update { it.copy(form = form, targets = null) }
    fun toggleFlag(bit: Int) = update(_state.value.form.let { it.copy(flags = it.flags xor bit) })
    fun addExtra() = update(_state.value.form.let { it.copy(extras = it.extras + Extra("", ExtraType.STRING, "")) })
    fun updateExtra(index: Int, extra: Extra) = update(_state.value.form.let { f -> f.copy(extras = f.extras.mapIndexed { i, e -> if (i == index) extra else e }) })
    fun removeExtra(index: Int) = update(_state.value.form.let { f -> f.copy(extras = f.extras.filterIndexed { i, _ -> i != index }) })

    fun resolve() {
        val targets = runCatching { sender.resolve(_state.value.form.toSpec()) }.getOrDefault(emptyList())
        _state.update { it.copy(targets = targets) }
    }

    fun send() {
        viewModelScope.launch {
            try {
                _messages.send("sent:" + sender.send(_state.value.form.toSpec()))
            } catch (e: Exception) {
                _messages.send(e.message ?: e.toString())
            }
        }
    }

    fun chooserIntent(title: String) = sender.chooser(_state.value.form.toSpec(), title)

    fun sendTo(target: ResolvedTarget) {
        val form = _state.value.form.copy(packageName = target.packageName, className = target.className)
        viewModelScope.launch {
            try {
                _messages.send("sent:" + sender.send(form.toSpec()))
            } catch (e: Exception) {
                _messages.send(e.message ?: e.toString())
            }
        }
    }

    fun setSaving(saving: Boolean) = _state.update { it.copy(saving = saving) }
    fun setPresetName(name: String) = _state.update { it.copy(presetName = name) }

    fun savePreset() {
        val s = _state.value
        if (s.presetName.isBlank()) return
        viewModelScope.launch {
            val preset = presets.save(s.presetName.trim(), s.form.toSpec(), s.presetId)
            _state.update { it.copy(presetId = preset.id, saving = false) }
            _messages.send("saved")
        }
    }
}
