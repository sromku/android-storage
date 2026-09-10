package com.snatik.storage.app.feature.viewer

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.components.EmptyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File

data class VectorRenderState(val loading: Boolean = true, val bitmap: ImageBitmap? = null, val error: String? = null)

class VectorRenderViewModel(private val path: String, private val context: Context) : ViewModel() {
    val name = path.substringAfterLast('/')
    private val _state = MutableStateFlow(VectorRenderState())
    val state: StateFlow<VectorRenderState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val bmp = withContext(Dispatchers.IO) { render() }
                _state.value = VectorRenderState(loading = false, bitmap = bmp.asImageBitmap())
            } catch (e: Throwable) {
                _state.value = VectorRenderState(loading = false, error = friendly(e))
            }
        }
    }

    // The framework's Drawable.createFromXml needs a compiled resource parser, so we parse the
    // <vector> ourselves and paint its paths with androidx.core PathParser — no resource system.
    private fun render(): Bitmap {
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", false) }
        }.newDocumentBuilder().parse(File(path))
        val root = doc.documentElement
        require(root.tagName == "vector") { "Not a <vector> drawable" }
        val vpW = root.getAttribute("android:viewportWidth").toFloatOrNull() ?: 24f
        val vpH = root.getAttribute("android:viewportHeight").toFloatOrNull() ?: 24f
        val alpha = root.getAttribute("android:alpha").toFloatOrNull() ?: 1f
        val scale = 1024f / maxOf(vpW, vpH)
        val bw = (vpW * scale).toInt().coerceIn(1, 4096)
        val bh = (vpH * scale).toInt().coerceIn(1, 4096)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.scale(bw / vpW, bh / vpH)
        var painted = 0
        renderChildren(root, canvas, alpha) { painted++ }
        if (painted == 0) throw IllegalStateException("No drawable paths found (gradients or resource references aren't supported)")
        return bmp
    }

    private fun renderChildren(parent: org.w3c.dom.Element, canvas: android.graphics.Canvas, alpha: Float, onPaint: () -> Unit) {
        val kids = parent.childNodes
        for (i in 0 until kids.length) {
            val n = kids.item(i)
            if (n.nodeType != org.w3c.dom.Node.ELEMENT_NODE) continue
            val el = n as org.w3c.dom.Element
            when (el.tagName) {
                "group" -> {
                    canvas.save()
                    val tx = el.f("android:translateX"); val ty = el.f("android:translateY")
                    val px = el.f("android:pivotX"); val py = el.f("android:pivotY")
                    val sx = el.f("android:scaleX", 1f); val sy = el.f("android:scaleY", 1f)
                    val rot = el.f("android:rotation")
                    canvas.translate(tx + px, ty + py)
                    canvas.rotate(rot)
                    canvas.scale(sx, sy)
                    canvas.translate(-px, -py)
                    renderChildren(el, canvas, alpha, onPaint)
                    canvas.restore()
                }
                "path" -> drawPath(el, canvas, alpha, onPaint)
            }
        }
    }

    private fun drawPath(el: org.w3c.dom.Element, canvas: android.graphics.Canvas, groupAlpha: Float, onPaint: () -> Unit) {
        val data = el.getAttribute("android:pathData")
        if (data.isBlank()) return
        val path = runCatching { androidx.core.graphics.PathParser.createPathFromPathData(data) }.getOrNull() ?: return
        path.fillType = if (el.getAttribute("android:fillType").equals("evenOdd", true)) android.graphics.Path.FillType.EVEN_ODD else android.graphics.Path.FillType.WINDING
        parseColor(el.getAttribute("android:fillColor"))?.let { c ->
            val a = el.f("android:fillAlpha", 1f) * groupAlpha
            canvas.drawPath(path, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.FILL; color = c; this.alpha = (android.graphics.Color.alpha(c) * a).toInt()
            })
            onPaint()
        }
        parseColor(el.getAttribute("android:strokeColor"))?.let { c ->
            val sw = el.f("android:strokeWidth"); if (sw <= 0) return@let
            val a = el.f("android:strokeAlpha", 1f) * groupAlpha
            canvas.drawPath(path, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE; strokeWidth = sw; color = c; this.alpha = (android.graphics.Color.alpha(c) * a).toInt()
            })
            onPaint()
        }
    }

    private fun org.w3c.dom.Element.f(attr: String, default: Float = 0f): Float =
        getAttribute(attr).removeSuffix("dp").removeSuffix("dip").toFloatOrNull() ?: default

    /** Parse #RGB/#ARGB literal colors; resource references (@color/...) and gradients are skipped. */
    private fun parseColor(v: String): Int? {
        if (v.isBlank() || !v.startsWith("#")) return null
        return runCatching { android.graphics.Color.parseColor(v) }.getOrNull()
    }

    private fun friendly(e: Throwable): String = e.message ?: e.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VectorRenderScreen(path: String, onBack: () -> Unit, viewModel: VectorRenderViewModel = koinViewModel(parameters = { parametersOf(path) })) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up)) } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                state.loading -> CircularProgressIndicator()
                state.error != null -> EmptyState(Icons.Default.Block, stringResource(R.string.vector_failed), state.error)
                state.bitmap != null -> {
                    var scale by remember { mutableFloatStateOf(1f) }
                    var ox by remember { mutableFloatStateOf(0f) }
                    var oy by remember { mutableFloatStateOf(0f) }
                    Box(
                        modifier = Modifier.fillMaxSize().checkerboard()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(0.5f, 12f)
                                    ox += pan.x; oy += pan.y
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = state.bitmap!!,
                            contentDescription = viewModel.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(24.dp)
                                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = ox, translationY = oy),
                        )
                    }
                }
            }
        }
    }
}

/** A light/dark checkerboard so transparent regions of the drawable are visible. */
private fun Modifier.checkerboard(cell: Float = 24f): Modifier = this.drawBehind {
    val light = Color(0xFF3A3F46); val dark = Color(0xFF2B2F35)
    drawRect(dark)
    var y = 0f; var row = 0
    while (y < size.height) {
        var x = 0f; var col = 0
        while (x < size.width) {
            if ((row + col) % 2 == 0) drawRect(light, topLeft = Offset(x, y), size = Size(cell, cell))
            x += cell; col++
        }
        y += cell; row++
    }
}
