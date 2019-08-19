package com.snatik.storage.app.dialogs

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.snatik.storage.app.R

class AddItemsDialog : DialogFragment() {

    private var mListener: DialogListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val dialog = BottomSheetDialog(activity!!, theme)

        val view = LayoutInflater.from(activity).inflate(R.layout.add_items_dialog, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)

        view.findViewById<View>(R.id.new_folder).setOnClickListener {
            dialog.dismiss()
            mListener!!.onOptionClick(R.id.new_folder, null)
        }

        view.findViewById<View>(R.id.new_file).setOnClickListener {
            dialog.dismiss()
            mListener!!.onOptionClick(R.id.new_file, null)
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

        fun newInstance(): AddItemsDialog {
            return AddItemsDialog()
        }
    }


}
