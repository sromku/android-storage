package com.snatik.storage.app

import android.app.Application
import com.snatik.storage.Storage

class StorageApp : Application() {

    val storage: Storage by lazy { Storage(this) }
}
