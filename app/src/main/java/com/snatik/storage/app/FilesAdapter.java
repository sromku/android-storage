package com.snatik.storage.app;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;
import java.util.List;

/**
 * Created by sromku on June, 2017.
 */
public class FilesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<File> mFiles;

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.file_line_view, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        File file = mFiles.get(position);
        FileViewHolder fileViewHolder = (FileViewHolder) holder;
        fileViewHolder.mName.setText(file.getName() + (file.isDirectory() ? " (folder)" : ""));
    }

    @Override
    public int getItemCount() {
        return mFiles != null ? mFiles.size() : 0;
    }


    public void setFiles(List<File> files) {
        mFiles = files;
    }

    public static class FileViewHolder extends RecyclerView.ViewHolder {

        public TextView mName;

        public FileViewHolder(View v) {
            super(v);
            mName = (TextView) v.findViewById(R.id.name);
        }
    }
}
