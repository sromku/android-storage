package com.snatik.storage.app.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.design.widget.BottomSheetDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.snatik.storage.Storage;
import com.snatik.storage.app.R;

public class UpdateItemDialog extends DialogFragment {

    private final static String PATH = "path";
    private DialogListener mListener;

    public static UpdateItemDialog newInstance(String path) {
        UpdateItemDialog fragment = new UpdateItemDialog();
        Bundle args = new Bundle();
        args.putString(PATH, path);
        fragment.setArguments(args);
        return fragment;
    }

    public UpdateItemDialog() {
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        final Dialog dialog = new BottomSheetDialog(getActivity(), getTheme());
        final String path = getArguments().getString(PATH);
        boolean isDirectory = new Storage(getActivity()).getFile(path).isDirectory();
        View view = LayoutInflater.from(getActivity()).inflate(R.layout.update_item_dialog, null);
        dialog.setContentView(view);
        dialog.setCancelable(true);

        // title
        TextView title = (TextView) view.findViewById(R.id.title);
        title.setText(isDirectory ? getString(R.string.folder_options) : getString(R.string.file_options));

        View rename = view.findViewById(R.id.rename);
        View delete = view.findViewById(R.id.delete);
        View move = view.findViewById(R.id.move);
        View copy = view.findViewById(R.id.copy);

        rename.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                mListener.onOptionClick(R.id.rename, path);
            }
        });

        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                mListener.onOptionClick(R.id.delete, path);
            }
        });

        if (!isDirectory) {
            move.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dialog.dismiss();
                    mListener.onOptionClick(R.id.move, path);
                }
            });

            copy.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dialog.dismiss();
                    mListener.onOptionClick(R.id.copy, path);
                }
            });
        } else {
            move.setVisibility(View.GONE);
            copy.setVisibility(View.GONE);
        }

        // control dialog width on different devices
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogINterface) {
                int width = (int) getResources().getDimension(R.dimen.bottom_sheet_dialog_width);
                dialog.getWindow().setLayout(
                        width == 0 ? ViewGroup.LayoutParams.MATCH_PARENT : width,
                        ViewGroup.LayoutParams.MATCH_PARENT);
            }
        });

        return dialog;
    }

    public interface DialogListener {
        void onOptionClick(int which, String path);
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
