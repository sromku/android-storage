package com.snatik.storage.app;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.widget.TextView;

import com.snatik.storage.Storage;
import com.snatik.storage.helpers.OrderType;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity implements FilesAdapter.OnFileItemListener {

    private RecyclerView mRecyclerView;
    private FilesAdapter mFilesAdapter;
    private Storage mStorage;
    private TextView mPathView;
    private int mTreeSteps = 0;
    private String mLastPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mStorage = new Storage(getApplicationContext());

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler);
        mPathView = (TextView) findViewById(R.id.path);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(layoutManager);
        mFilesAdapter = new FilesAdapter();
        mFilesAdapter.setListener(this);
        mRecyclerView.setAdapter(mFilesAdapter);

        presentFiles(mStorage.getRoot(Storage.StorageType.EXTERNAL));

        // TODO - on fab click - add folder, add text file, add sample bitmap
    }

    private void presentFiles(String path) {
        mPathView.setText(path);
        List<File> files = mStorage.getFiles(path);
        if (files != null) {
            Collections.sort(files, OrderType.NAME.getComparator());
        }
        mFilesAdapter.setFiles(files);
        mFilesAdapter.notifyDataSetChanged();
    }


    @Override
    public void onClick(File file) {
        if (file.isDirectory()) {
            mTreeSteps++;
            String path = file.getAbsolutePath();
            presentFiles(path);
        }
    }

    @Override
    public void onLongClick(File file) {
        // TODO - show options - delete / move / rename
    }

    @Override
    public void onBackPressed() {
        if (mTreeSteps > 0) {
            String path = mPathView.getText().toString();
            path = path.substring(0, path.lastIndexOf(File.separator));
            mTreeSteps--;
            presentFiles(path);
            return;
        }
        super.onBackPressed();
    }
}
