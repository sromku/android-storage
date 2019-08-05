package com.snatik.storage.app

import com.google.android.material.snackbar.Snackbar
import android.view.View

/**
 * Created by sromku on June, 2017.
 */
object Helper {

    fun showSnackbar(message: String, root: View) {
        Snackbar.make(root, message, Snackbar.LENGTH_SHORT).show()
    }

    fun fileExt(url: String): String? {
        var newUrl = url
        if (newUrl.indexOf("?") > -1) {
            newUrl = newUrl.substring(0, newUrl.indexOf("?"))
        }
        return if (newUrl.lastIndexOf(".") == -1) {
            null
        } else {
            var ext = newUrl.substring(newUrl.lastIndexOf(".") + 1)
            if (ext.indexOf("%") > -1) {
                ext = ext.substring(0, ext.indexOf("%"))
            }
            if (ext.indexOf("/") > -1) {
                ext = ext.substring(0, ext.indexOf("/"))
            }
            ext.toLowerCase()

        }
    }

}
