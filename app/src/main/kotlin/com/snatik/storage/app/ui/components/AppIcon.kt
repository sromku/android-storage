package com.snatik.storage.app.ui.components

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.asImage
import coil3.compose.AsyncImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options

/** Coil model for an installed package's launcher icon. */
data class AppIconModel(val packageName: String)

class AppIconFetcher(private val context: Context, private val model: AppIconModel) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val drawable = context.packageManager.getApplicationIcon(model.packageName)
        return ImageFetchResult(image = drawable.asImage(), isSampled = false, dataSource = DataSource.DISK)
    }

    class Factory(private val context: Context) : Fetcher.Factory<AppIconModel> {
        override fun create(data: AppIconModel, options: Options, imageLoader: ImageLoader): Fetcher = AppIconFetcher(context, data)
    }
}

class AppIconKeyer : Keyer<AppIconModel> {
    override fun key(data: AppIconModel, options: Options): String = "appicon:${data.packageName}"
}

@Composable
fun AppIcon(packageName: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    AsyncImage(
        model = AppIconModel(packageName),
        contentDescription = null,
        modifier = modifier.size(size).clip(RoundedCornerShape(size / 4)),
    )
}
