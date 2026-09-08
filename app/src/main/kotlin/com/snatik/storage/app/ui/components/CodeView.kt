package com.snatik.storage.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.theme.MonoStyle

/**
 * Monospace lines with a number gutter. Optional [highlight] marks every match of a query.
 * [lineNumbers] maps display index to the original line number, for filtered views.
 */
@Composable
fun CodeView(
    lines: List<String>,
    wrap: Boolean,
    modifier: Modifier = Modifier,
    lineNumbers: List<Int>? = null,
    highlight: String? = null,
    truncated: Boolean = false,
    onLoadMore: (() -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
) {
    val maxNumber = lineNumbers?.lastOrNull() ?: lines.size
    val gutterWidth = (maxNumber.toString().length * 9 + 16).dp
    val horizontal = rememberScrollState()
    val lineStyle = MonoStyle.copy(fontSize = 13.sp, lineHeight = 19.sp)
    val gutterColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val mark = SpanStyle(background = MaterialTheme.colorScheme.tertiaryContainer, color = MaterialTheme.colorScheme.onTertiaryContainer)
    SelectionContainer(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().then(if (wrap) Modifier else Modifier.horizontalScroll(horizontal)),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            itemsIndexed(lines) { index, line ->
                Row(modifier = if (wrap) Modifier.fillMaxWidth() else Modifier) {
                    Text(
                        (lineNumbers?.getOrNull(index) ?: (index + 1)).toString(),
                        style = lineStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(gutterWidth).background(gutterColor).padding(end = 8.dp),
                    )
                    Text(
                        text = if (highlight.isNullOrEmpty()) AnnotatedString(line) else highlighted(line, highlight, mark),
                        style = lineStyle,
                        softWrap = wrap,
                        modifier = Modifier.padding(start = 12.dp, end = 16.dp).then(if (wrap) Modifier.weight(1f) else Modifier),
                    )
                }
            }
            if (truncated && onLoadMore != null) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        TextButton(onClick = onLoadMore) { Text(stringResource(R.string.load_more)) }
                    }
                }
            }
        }
    }
}

private fun highlighted(line: String, query: String, mark: SpanStyle): AnnotatedString = buildAnnotatedString {
    var from = 0
    while (true) {
        val at = line.indexOf(query, from, ignoreCase = true)
        if (at < 0) {
            append(line.substring(from))
            break
        }
        append(line.substring(from, at))
        pushStyle(mark)
        append(line.substring(at, at + query.length))
        pop()
        from = at + query.length
    }
}
