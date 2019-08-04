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
        var url = url
        if (url.indexOf("?") > -1) {
            url = url.substring(0, url.indexOf("?"))
        }
        if (url.lastIndexOf(".") == -1) {
            return null
        } else {
            var ext = url.substring(url.lastIndexOf(".") + 1)
            if (ext.indexOf("%") > -1) {
                ext = ext.substring(0, ext.indexOf("%"))
            }
            if (ext.indexOf("/") > -1) {
                ext = ext.substring(0, ext.indexOf("/"))
            }
            return ext.toLowerCase()

        }
    }

}
