package com.snatik.storage.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.theme.MonoStyle
import com.snatik.storage.core.data.Tabular

/**
 * A scrollable table for query results. Column widths follow the content of the first rows.
 */
@Composable
fun DataGrid(
    data: Tabular,
    modifier: Modifier = Modifier,
    onRowClick: ((Int) -> Unit)? = null,
    onHeaderClick: ((String) -> Unit)? = null,
    sortedBy: String? = null,
    sortDescending: Boolean = false,
) {
    val widths = remember(data) {
        data.columns.mapIndexed { i, name ->
            val longest = (listOf(name.length) + data.rows.take(60).map { (it.getOrNull(i) ?: "NULL").length }).max()
            (longest * 7.5f + 24f).coerceIn(72f, 260f).dp
        }
    }
    val horizontal = rememberScrollState()
    val nullText = stringResource(R.string.null_value)
    Column(modifier = modifier.horizontalScroll(horizontal)) {
        Row(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
            data.columns.forEachIndexed { i, name ->
                val marker = if (name == sortedBy) if (sortDescending) " ↓" else " ↑" else ""
                Text(
                    name + marker,
                    style = MonoStyle,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .width(widths[i])
                        .then(if (onHeaderClick != null) Modifier.clickable { onHeaderClick(name) } else Modifier)
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                )
            }
        }
        HorizontalDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(data.rows) { index, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (onRowClick != null) Modifier.clickable { onRowClick(index) } else Modifier)
                        .background(if (index % 2 == 1) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface),
                ) {
                    data.columns.indices.forEach { i ->
                        val value = row.getOrNull(i)
                        Text(
                            value ?: nullText,
                            style = MonoStyle,
                            fontStyle = if (value == null) FontStyle.Italic else FontStyle.Normal,
                            color = if (value == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(widths[i]).height(36.dp).padding(horizontal = 8.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
    }
}
