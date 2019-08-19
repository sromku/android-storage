package com.snatik.storage.app.dialogs

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import com.snatik.storage.Storage
import com.snatik.storage.app.R
import java.io.File

/**
 * Created by sromku on June, 2017.
 */
class RenameDialog : DialogFragment() {
    private var mListener: DialogListener? = null
    private var mStorage: Storage? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        mStorage = Storage(activity!!)

        val builder = AlertDialog.Builder(activity)

        val view = LayoutInflater.from(activity)
                .inflate(R.layout.rename_dialog, view as ViewGroup?, false)

        // if text is empty, disable the dialog positive button
        val currentNameText = view.findViewById<View>(R.id.current_name) as EditText
        val path = arguments?.getString(PATH)

        val file = mStorage!!.getFile(path!!)
        currentNameText.setText(file.name)
        val parent = file.parent

        val newNameText = view.findViewById<View>(R.id.new_name) as EditText
        newNameText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

            override fun afterTextChanged(editable: Editable?) {
                (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = editable != null && editable.length > 0
            }
        })

        builder.setTitle(R.string.rename)
        builder.setView(view)
        builder.setPositiveButton(R.string.label_save) { dialogInterface, i ->
            val newName = newNameText.text.toString()
            val toPath = if (parent == null) newName else parent + File.separator + newName
            mListener!!.onRename(file.path, toPath)
        }

        val dialog = builder.create()
        view.post { dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false }
        dialog.setCancelable(false)
        return dialog
    }

    interface DialogListener {
        fun onRename(fromPath: String, toPath: String)
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        try {
            mListener = activity as DialogListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$activity must implement DialogListener")
        }

    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    companion object {

        private val PATH = "path"

        fun newInstance(path: String): RenameDialog {
            val fragment = RenameDialog()
            val args = Bundle()
            args.putString(PATH, path)
            fragment.arguments = args
            return fragment
        }
    }

}
