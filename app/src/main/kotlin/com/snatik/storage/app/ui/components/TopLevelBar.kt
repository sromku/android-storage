package com.snatik.storage.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.snatik.storage.app.R
import com.snatik.storage.app.navigation.TopLevel

@Composable
fun TopLevelBar(current: TopLevel, onSelect: (TopLevel) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = current == TopLevel.STORAGE,
            onClick = { onSelect(TopLevel.STORAGE) },
            icon = { Icon(Icons.Default.Storage, contentDescription = null) },
            label = { Text(stringResource(R.string.tab_storage)) },
        )
        NavigationBarItem(
            selected = current == TopLevel.APPS,
            onClick = { onSelect(TopLevel.APPS) },
            icon = { Icon(Icons.Default.Apps, contentDescription = null) },
            label = { Text(stringResource(R.string.tab_apps)) },
        )
        NavigationBarItem(
            selected = current == TopLevel.DATA,
            onClick = { onSelect(TopLevel.DATA) },
            icon = { Icon(Icons.Default.Dataset, contentDescription = null) },
            label = { Text(stringResource(R.string.tab_data)) },
        )
    }
}
