package com.snatik.storage.app;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.TextView;

import com.snatik.storage.EncryptConfiguration;
import com.snatik.storage.Storage;
import com.snatik.storage.app.dialogs.AddItemsDialog;
import com.snatik.storage.app.dialogs.NewFolderDialog;
import com.snatik.storage.app.dialogs.NewTextFileDialog;
import com.snatik.storage.app.dialogs.UpdateItemDialog;
import com.snatik.storage.helpers.OrderType;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity implements
        FilesAdapter.OnFileItemListener,
        AddItemsDialog.DialogListener,
        UpdateItemDialog.DialogListener,
        NewFolderDialog.DialogListener,
        NewTextFileDialog.DialogListener {

    private RecyclerView mRecyclerView;
    private FilesAdapter mFilesAdapter;
    private Storage mStorage;
    private TextView mPathView;
    private int mTreeSteps = 0;
    private final static String IVX = "abcdefghijklmnop";
    private final static String SECRET_KEY = "secret1234567890";
    private final static byte[] SALT = "0000111100001111".getBytes();

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

        findViewById(R.id.fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AddItemsDialog.newInstance().show(getFragmentManager(), "add_items");
            }
        });

        // load files
        showFiles(mStorage.getRoot(Storage.StorageType.EXTERNAL));
    }

    private void showFiles(String path) {
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
            showFiles(path);
        } else {
            Intent intent = new Intent(this, ViewTextActivity.class);
            intent.putExtra(ViewTextActivity.EXTRA_FILE_NAME, file.getName());
            intent.putExtra(ViewTextActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
            startActivity(intent);
        }
    }

    @Override
    public void onLongClick(File file) {
        UpdateItemDialog.newInstance().show(getFragmentManager(), "update_item");
    }

    @Override
    public void onBackPressed() {
        if (mTreeSteps > 0) {
            String path = getPreviousPath();
            mTreeSteps--;
            showFiles(path);
            return;
        }
        super.onBackPressed();
    }

    private String getCurrentPath() {
        return mPathView.getText().toString();
    }

    private String getPreviousPath() {
        String path = getCurrentPath();
        return path.substring(0, path.lastIndexOf(File.separator));
    }

    @Override
    public void onListOptionClick(int which) {
        switch (which) {
            case R.id.new_file:
                NewTextFileDialog.newInstance().show(getFragmentManager(), "new_file");
                break;
            case R.id.new_folder:
                NewFolderDialog.newInstance().show(getFragmentManager(), "new_folder");
                break;
            case R.id.delete:
                UIHelper.showSnackbar("Delete", mRecyclerView);
                break;
            case R.id.rename:
                UIHelper.showSnackbar("Rename", mRecyclerView);
                break;
            case R.id.move:
                UIHelper.showSnackbar("Move", mRecyclerView);
                break;
            case R.id.copy:
                UIHelper.showSnackbar("Copy", mRecyclerView);
                break;
        }
    }

    @Override
    public void onNewFolder(String name) {
        String currentPath = getCurrentPath();
        String folderPath = currentPath + File.separator + name;
        mStorage.createDirectory(folderPath);
        showFiles(currentPath);
        UIHelper.showSnackbar("New folder created: " + name, mRecyclerView);
    }

    @Override
    public void onNewFile(String name, String content, boolean encrypted) {
        String currentPath = getCurrentPath();
        String folderPath = currentPath + File.separator + name;
        if (encrypted) {
            mStorage.setEncryptConfiguration(new EncryptConfiguration.Builder()
                    .setEncryptContent(IVX, SECRET_KEY, SALT)
                    .build());
        }
        mStorage.createFile(folderPath, content);
        showFiles(currentPath);
        UIHelper.showSnackbar("New file created: " + name, mRecyclerView);
    }
}
