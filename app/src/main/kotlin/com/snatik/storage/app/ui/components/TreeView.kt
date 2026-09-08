package com.snatik.storage.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snatik.storage.app.ui.theme.MonoStyle

/** A node in a document tree, shared by the JSON and XML viewers. */
data class TreeNode(
    val id: String,
    val label: String,
    val value: String? = null,
    val valueColorRole: ValueRole = ValueRole.PLAIN,
    val children: List<TreeNode> = emptyList(),
) {
    val hasChildren: Boolean get() = children.isNotEmpty()
    enum class ValueRole { PLAIN, STRING, NUMBER, LITERAL }
}

private class FlatRow(val node: TreeNode, val depth: Int)

@Composable
fun TreeView(root: TreeNode, modifier: Modifier = Modifier, initiallyExpandedDepth: Int = 2) {
    val expanded = remember(root.id) {
        mutableStateOf(collectExpanded(root, initiallyExpandedDepth, HashSet(), 0))
    }
    val rows = remember(root, expanded.value) { flatten(root, expanded.value) }
    LazyColumn(modifier = modifier) {
        items(rows.size, key = { rows[it].node.id }) { i ->
            Row(rows[i], expanded)
        }
    }
}

@Composable
private fun Row(row: FlatRow, expanded: androidx.compose.runtime.MutableState<Set<String>>) {
    val node = row.node
    val isOpen = node.id in expanded.value
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = node.hasChildren) {
                expanded.value = if (isOpen) expanded.value - node.id else expanded.value + node.id
            }
            .padding(start = (8 + row.depth * 16).dp, top = 3.dp, bottom = 3.dp, end = 12.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.Top,
    ) {
        if (node.hasChildren) {
            Icon(
                if (isOpen) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        } else {
            androidx.compose.foundation.layout.Spacer(Modifier.size(18.dp))
        }
        SelectionContainer {
            androidx.compose.foundation.layout.Row {
                Text(node.label, style = MonoStyle.copy(color = MaterialTheme.colorScheme.primary), fontWeight = FontWeight.Medium)
                node.value?.let { v ->
                    val color = when (node.valueColorRole) {
                        TreeNode.ValueRole.STRING -> MaterialTheme.colorScheme.tertiary
                        TreeNode.ValueRole.NUMBER -> MaterialTheme.colorScheme.secondary
                        TreeNode.ValueRole.LITERAL -> MaterialTheme.colorScheme.error
                        TreeNode.ValueRole.PLAIN -> MaterialTheme.colorScheme.onSurface
                    }
                    Text("  $v", style = MonoStyle.copy(color = color), maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                if (node.hasChildren && !isOpen) {
                    Text("  ${node.children.size}", style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                }
            }
        }
    }
}

private fun flatten(root: TreeNode, expanded: Set<String>): List<FlatRow> {
    val out = ArrayList<FlatRow>()
    fun walk(node: TreeNode, depth: Int) {
        out += FlatRow(node, depth)
        if (node.id in expanded) node.children.forEach { walk(it, depth + 1) }
    }
    root.children.forEach { walk(it, 0) }
    return out
}

private fun collectExpanded(node: TreeNode, maxDepth: Int, acc: MutableSet<String>, depth: Int): Set<String> {
    if (depth < maxDepth && node.hasChildren) {
        acc += node.id
        node.children.forEach { collectExpanded(it, maxDepth, acc, depth + 1) }
    }
    return acc
}
