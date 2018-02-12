package com.snatik.storage.app;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.snatik.storage.Storage;

import java.io.File;
import java.util.List;

/**
 * Created by sromku on June, 2017.
 */
public class FilesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<File> mFiles;
    private OnFileItemListener mListener;
    private Storage mStorage;

    public FilesAdapter(Context context) {
        mStorage = new Storage(context);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.file_line_view, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        final File file = mFiles.get(position);
        FileViewHolder fileViewHolder = (FileViewHolder) holder;
        fileViewHolder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mListener.onClick(file);
            }
        });
        fileViewHolder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                mListener.onLongClick(file);
                return true;
            }
        });
        fileViewHolder.mName.setText(file.getName());
        fileViewHolder.mIcon.setImageResource(file.isDirectory() ? R.drawable.ic_folder_primary_24dp : R.drawable
                .ic_file_primary_24dp);
        if (file.isDirectory()) {
            fileViewHolder.mSize.setVisibility(View.GONE);
        } else {
            fileViewHolder.mSize.setVisibility(View.VISIBLE);
            fileViewHolder.mSize.setText(mStorage.getReadableSize(file));
        }

    }

    @Override
    public int getItemCount() {
        return mFiles != null ? mFiles.size() : 0;
    }

    public void setFiles(List<File> files) {
        mFiles = files;
    }

    public void setListener(OnFileItemListener listener) {
        mListener = listener;
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {

        TextView mName;
        TextView mSize;
        ImageView mIcon;

        FileViewHolder(View v) {
            super(v);
            mName = (TextView) v.findViewById(R.id.name);
            mSize = (TextView) v.findViewById(R.id.size);
            mIcon = (ImageView) v.findViewById(R.id.icon);
        }
    }

    public interface OnFileItemListener {
        void onClick(File file);

        void onLongClick(File file);
    }
}
