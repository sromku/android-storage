package com.snatik.storage.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Path from the volume root to the current directory. The last crumb is the current one.
 */
@Composable
fun Breadcrumbs(rootLabel: String, rootPath: String, path: String, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val crumbs = remember(rootLabel, rootPath, path) {
        val relative = path.removePrefix(rootPath).trim('/')
        val parts = if (relative.isEmpty()) emptyList() else relative.split('/')
        buildList {
            add(rootLabel to rootPath)
            var current = rootPath.trimEnd('/')
            for (part in parts) {
                current = "$current/$part"
                add(part to current)
            }
        }
    }
    val state = rememberLazyListState()
    LaunchedEffect(crumbs.size) { state.animateScrollToItem(crumbs.lastIndex) }

    LazyRow(
        state = state,
        modifier = modifier.fillMaxWidth().height(40.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(crumbs, key = { _, crumb -> crumb.second }) { index, (label, crumbPath) ->
            val isLast = index == crumbs.lastIndex
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isLast) { onNavigate(crumbPath) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
                if (!isLast) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
