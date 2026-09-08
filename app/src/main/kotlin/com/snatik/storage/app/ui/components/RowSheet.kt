package com.snatik.storage.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.theme.MonoStyle

/** All columns of one row, readable and selectable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RowSheet(columns: List<String>, row: List<String?>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp), modifier = Modifier.navigationBarsPadding()) {
            itemsIndexed(columns) { i, column ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(column, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    val value = row.getOrNull(i)
                    SelectionContainer {
                        Text(
                            value ?: stringResource(R.string.null_value),
                            style = MonoStyle.copy(fontSize = 13.sp),
                            fontStyle = if (value == null) FontStyle.Italic else FontStyle.Normal,
                            color = if (value == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
