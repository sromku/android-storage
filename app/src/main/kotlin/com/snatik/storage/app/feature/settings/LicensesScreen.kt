package com.snatik.storage.app.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snatik.storage.app.R

/** One third-party component we ship, with the license it is distributed under. */
private data class License(
    val name: String,
    val detail: String,
    val license: String,
    val notice: String? = null,
)

// The library that carries a copyleft notice obligation is listed first, in full.
private val LIBRAW_NOTICE = """
LibRaw: Raw images processing library
Copyright (C) 2008-2021 LibRaw LLC (https://www.libraw.org, info@libraw.org)

LibRaw is free software; you can redistribute it and/or modify it under the
terms of one of two licenses as you choose:

  1. GNU Lesser General Public License, version 2.1
  2. Common Development and Distribution License (CDDL), version 1.0

Storage Studio compiles LibRaw from source; the source and both license texts
ship in the project repository under app/src/main/cpp/libraw, so the LibRaw
portion of the app can be rebuilt and replaced independently.

LibRaw uses code from dcraw.c by Dave Coffin (no restricted code is used),
the DCB demosaic and FBDD denoise by Jacek Gozdz (BSD 3-clause), the X3F
library by Roland Karlsson (BSD-style), and pieces of the Adobe DNG SDK 1.4
(MIT).
""".trim()

private val LICENSES = listOf(
    License(
        name = "LibRaw",
        detail = "0.21.4  ·  RAW decode and develop",
        license = "LGPL 2.1 or CDDL 1.0",
        notice = LIBRAW_NOTICE,
    ),
    License("Jetpack Compose", "Material 3, UI, foundation", "Apache 2.0"),
    License("AndroidX", "core, activity, lifecycle, navigation3, exifinterface, documentfile", "Apache 2.0"),
    License("Kotlin & kotlinx", "stdlib, coroutines, serialization", "Apache 2.0"),
    License("Koin", "Dependency injection", "Apache 2.0"),
    License("Coil", "Image and video loading", "Apache 2.0"),
    License("AndroidX Media3", "ExoPlayer and player UI", "Apache 2.0"),
    License("ZXing", "QR and barcode core", "Apache 2.0"),
    License("jadx", "Dex and APK inspection", "Apache 2.0"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.licenses_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.licenses_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
            LICENSES.forEach { LicenseCard(it) }
        }
    }
}

@Composable
private fun LicenseCard(item: License) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    item.license,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                item.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.notice != null) {
                Text(
                    stringResource(if (expanded) R.string.licenses_hide else R.string.licenses_show),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { expanded = !expanded }
                        .padding(top = 6.dp),
                )
                AnimatedVisibility(visible = expanded) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text(
                            item.notice,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }
        }
    }
}
