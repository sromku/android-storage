package com.snatik.storage.app.feature.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import com.snatik.storage.app.ui.components.TreeNode
import com.snatik.storage.app.ui.components.TreeView
import com.snatik.storage.core.fs.FileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

data class TreeUiState(val root: TreeNode? = null, val loading: Boolean = true, val error: String? = null)

class JsonTreeViewModel(private val path: String, private val fs: FileSystem) : ViewModel() {
    val name = path.substringAfterLast('/')
    private val _state = MutableStateFlow(TreeUiState())
    val state: StateFlow<TreeUiState> = _state.asStateFlow()
    init {
        viewModelScope.launch {
            try {
                val text = String(fs.readBytes(path, 0, 4 * 1024 * 1024))
                val root = withContext(Dispatchers.Default) { jsonToNode("root", "root", Json.parseToJsonElement(text)) }
                _state.update { it.copy(root = root, loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }
    private fun jsonToNode(id: String, key: String, el: JsonElement): TreeNode = when (el) {
        is JsonObject -> TreeNode(id, if (key == "root") "{ }" else "$key:", children = el.entries.map { (k, v) -> jsonToNode("$id/$k", k, v) })
        is JsonArray -> TreeNode(id, "$key [${el.size}]", children = el.mapIndexed { i, v -> jsonToNode("$id/$i", "[$i]", v) })
        is JsonNull -> TreeNode(id, "$key:", "null", TreeNode.ValueRole.LITERAL)
        is JsonPrimitive -> {
            val role = when { el.isString -> TreeNode.ValueRole.STRING; el.content == "true" || el.content == "false" -> TreeNode.ValueRole.LITERAL; else -> TreeNode.ValueRole.NUMBER }
            TreeNode(id, "$key:", if (el.isString) "\"${el.content}\"" else el.content, role)
        }
    }
}

class XmlTreeViewModel(private val path: String, private val fs: FileSystem) : ViewModel() {
    val name = path.substringAfterLast('/')
    private val _state = MutableStateFlow(TreeUiState())
    val state: StateFlow<TreeUiState> = _state.asStateFlow()
    init {
        viewModelScope.launch {
            try {
                val text = String(fs.readBytes(path, 0, 4 * 1024 * 1024))
                val root = withContext(Dispatchers.Default) {
                    val factory = DocumentBuilderFactory.newInstance().apply { runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", false) } }
                    val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(text)))
                    elementToNode("root", doc.documentElement)
                }
                _state.update { it.copy(root = TreeNode("wrap", "", children = listOf(root)), loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }
    private fun elementToNode(id: String, el: Element): TreeNode {
        val attrs = (0 until el.attributes.length).joinToString(" ") { i -> val a = el.attributes.item(i); "${a.nodeName}=\"${a.nodeValue}\"" }
        val label = "<${el.tagName}" + (if (attrs.isNotEmpty()) " $attrs" else "") + ">"
        val childElements = ArrayList<TreeNode>()
        var text: String? = null
        val kids = el.childNodes
        for (i in 0 until kids.length) {
            val n = kids.item(i)
            when (n.nodeType) {
                Node.ELEMENT_NODE -> childElements += elementToNode("$id/$i", n as Element)
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> n.nodeValue?.trim()?.takeIf { it.isNotEmpty() }?.let { text = it }
            }
        }
        return TreeNode(id, label, value = if (childElements.isEmpty()) text else null, valueColorRole = TreeNode.ValueRole.STRING, children = childElements)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JsonViewerScreen(path: String, onBack: () -> Unit, onViewAsText: () -> Unit, viewModel: JsonTreeViewModel = koinViewModel(parameters = { parametersOf(path) })) =
    TreeScreen(viewModel.name, viewModel.state.collectAsStateWithLifecycle().value, onBack, onViewAsText)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XmlViewerScreen(path: String, onBack: () -> Unit, onViewAsText: () -> Unit, viewModel: XmlTreeViewModel = koinViewModel(parameters = { parametersOf(path) })) =
    TreeScreen(viewModel.name, viewModel.state.collectAsStateWithLifecycle().value, onBack, onViewAsText)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TreeScreen(name: String, state: TreeUiState, onBack: () -> Unit, onViewAsText: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
                actions = { IconButton(onClick = onViewAsText) { Icon(Icons.Default.Notes, contentDescription = stringResource(R.string.view_as_text)) } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> EmptyState(Icons.Default.Block, stringResource(R.string.cannot_read_file), state.error)
                state.root != null -> TreeView(state.root, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
