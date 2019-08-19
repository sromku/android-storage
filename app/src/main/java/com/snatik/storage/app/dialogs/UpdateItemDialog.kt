package com.snatik.storage.app.dialogs

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.snatik.storage.Storage
import com.snatik.storage.app.R

class UpdateItemDialog : DialogFragment() {
    private var mListener: DialogListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val dialog = BottomSheetDialog(activity!!, theme)
        val path = arguments?.getString(PATH)
        val isDirectory = Storage(activity!!).getFile(path!!).isDirectory
        val view = LayoutInflater.from(activity).inflate(R.layout.update_item_dialog, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)

        // title
        val title = view.findViewById<View>(R.id.title) as TextView
        title.text = if (isDirectory) getString(R.string.folder_options) else getString(R.string.file_options)

        val rename = view.findViewById<View>(R.id.rename)
        val delete = view.findViewById<View>(R.id.delete)
        val move = view.findViewById<View>(R.id.move)
        val copy = view.findViewById<View>(R.id.copy)

        rename.setOnClickListener {
            dialog.dismiss()
            mListener!!.onOptionClick(R.id.rename, path)
        }

        delete.setOnClickListener {
            dialog.dismiss()
            mListener!!.onOptionClick(R.id.delete, path)
        }

        if (!isDirectory) {
            move.setOnClickListener {
                dialog.dismiss()
                mListener!!.onOptionClick(R.id.move, path)
            }

            copy.setOnClickListener {
                dialog.dismiss()
                mListener!!.onOptionClick(R.id.copy, path)
            }
        } else {
            move.visibility = View.GONE
            copy.visibility = View.GONE
        }

        // control dialog width on different devices
        dialog.setOnShowListener {
            val width = resources.getDimension(R.dimen.bottom_sheet_dialog_width).toInt()
            dialog.window!!.setLayout(
                    if (width == 0) ViewGroup.LayoutParams.MATCH_PARENT else width,
                    ViewGroup.LayoutParams.MATCH_PARENT)
        }

        return dialog
    }

    interface DialogListener {
        fun onOptionClick(which: Int, path: String?)
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

        fun newInstance(path: String): UpdateItemDialog {
            val fragment = UpdateItemDialog()
            val args = Bundle()
            args.putString(PATH, path)
            fragment.arguments = args
            return fragment
        }
    }


}
