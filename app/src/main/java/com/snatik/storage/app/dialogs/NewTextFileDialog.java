package com.snatik.storage.app.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;

import com.snatik.storage.app.R;

/**
 * Created by sromku on June, 2017.
 */
public class NewTextFileDialog extends DialogFragment {

    private NewTextFileDialog.DialogListener mListener;

    public static NewTextFileDialog newInstance() {
        NewTextFileDialog fragment = new NewTextFileDialog();
        return fragment;
    }

    public NewTextFileDialog() {
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        final View view = LayoutInflater.from(getActivity())
                .inflate(R.layout.new_file_dialog, (ViewGroup) getView(), false);

        // if text is empty, disable the dialog positive button
        final EditText nameEditText = (EditText) view.findViewById(R.id.name);
        final EditText contentEditText = (EditText) view.findViewById(R.id.content);
        final CheckBox encryptCheckbox = (CheckBox) view.findViewById(R.id.checkbox);

        nameEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(editable.length() > 0 && contentEditText.getText().length() > 0);
            }
        });

        contentEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(editable.length() > 0 && nameEditText.getText().length() > 0);
            }
        });

        builder.setTitle(R.string.new_file);
        builder.setView(view);
        builder.setPositiveButton(R.string.label_save, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                mListener.onNewFile(nameEditText.getText().toString(), contentEditText.getText().toString(), encryptCheckbox.isChecked());
            }
        });

        final AlertDialog dialog = builder.create();
        view.post(new Runnable() {
            @Override
            public void run() {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            }
        });
        dialog.setCancelable(false);
        return dialog;
    }

    public interface DialogListener {
        void onNewFile(String name, String content, boolean encrypt);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (DialogListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement DialogListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

}
