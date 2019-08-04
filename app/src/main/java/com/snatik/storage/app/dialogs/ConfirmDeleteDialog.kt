package com.snatik.storage.app.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.snatik.storage.Storage
import com.snatik.storage.app.R

class ConfirmDeleteDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)

        val msg: String
        val path = arguments?.getString(PATH)
        val storage = Storage(activity!!)
        val file = storage.getFile(path!!)
        msg = if (file.isDirectory) {
            "You are about to delete the folder with all it's content for real."
        } else {
            "You are about to delete the file"
        }
        builder.setMessage(msg)
        builder.setPositiveButton(R.string.label_delete) { _, _ -> (activity as ConfirmListener).onConfirmDelete(path) }
        builder.setNegativeButton(R.string.label_cancel, null)
        return builder.create()
    }

    interface ConfirmListener {
        fun onConfirmDelete(path: String?)
    }

    companion object {

        private const val PATH = "path"

        fun newInstance(path: String): ConfirmDeleteDialog {
            val fragment = ConfirmDeleteDialog()
            val args = Bundle()
            args.putString(PATH, path)
            fragment.arguments = args
            return fragment
        }
    }
}
