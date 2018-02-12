package com.snatik.storage.app;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.snatik.storage.EncryptConfiguration;
import com.snatik.storage.Storage;
import com.snatik.storage.app.dialogs.AddItemsDialog;
import com.snatik.storage.app.dialogs.ConfirmDeleteDialog;
import com.snatik.storage.app.dialogs.NewFolderDialog;
import com.snatik.storage.app.dialogs.NewTextFileDialog;
import com.snatik.storage.app.dialogs.RenameDialog;
import com.snatik.storage.app.dialogs.UpdateItemDialog;
import com.snatik.storage.helpers.OrderType;
import com.snatik.storage.helpers.SizeUnit;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static com.snatik.storage.app.Helper.fileExt;

public class MainActivity extends AppCompatActivity implements
        FilesAdapter.OnFileItemListener,
        AddItemsDialog.DialogListener,
        UpdateItemDialog.DialogListener,
        NewFolderDialog.DialogListener,
        NewTextFileDialog.DialogListener,
        ConfirmDeleteDialog.ConfirmListener,
        RenameDialog.DialogListener {

    private static final int PERMISSION_REQUEST_CODE = 1000;
    private RecyclerView mRecyclerView;
    private FilesAdapter mFilesAdapter;
    private Storage mStorage;
    private TextView mPathView;
    private TextView mMovingText;
    private boolean mCopy;
    private View mMovingLayout;
    private int mTreeSteps = 0;
    private final static String IVX = "abcdefghijklmnop";
    private final static String SECRET_KEY = "secret1234567890";
    private final static byte[] SALT = "0000111100001111".getBytes();
    private String mMovingPath;
    private boolean mInternal = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mStorage = new Storage(getApplicationContext());

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler);
        mPathView = (TextView) findViewById(R.id.path);
        mMovingLayout = findViewById(R.id.moving_layout);
        mMovingText = (TextView) mMovingLayout.findViewById(R.id.moving_file_name);

        mMovingLayout.findViewById(R.id.accept_move).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mMovingLayout.setVisibility(View.GONE);
                if (mMovingPath != null) {

                    if (!mCopy) {
                        String toPath = getCurrentPath() + File.separator + mStorage.getFile(mMovingPath).getName();
                        if (!mMovingPath.equals(toPath)) {
                            mStorage.move(mMovingPath, toPath);
                            Helper.showSnackbar("Moved", mRecyclerView);
                            showFiles(getCurrentPath());
                        } else {
                            Helper.showSnackbar("The file is already here", mRecyclerView);
                        }
                    } else {
                        String toPath = getCurrentPath() + File.separator + "copy " + mStorage.getFile(mMovingPath)
                                .getName();
                        mStorage.copy(mMovingPath, toPath);
                        Helper.showSnackbar("Copied", mRecyclerView);
                        showFiles(getCurrentPath());
                    }
                    mMovingPath = null;
                }
            }
        });

        mMovingLayout.findViewById(R.id.decline_move).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mMovingLayout.setVisibility(View.GONE);
                mMovingPath = null;
            }
        });

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(layoutManager);
        mFilesAdapter = new FilesAdapter(getApplicationContext());
        mFilesAdapter.setListener(this);
        mRecyclerView.setAdapter(mFilesAdapter);

        findViewById(R.id.fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AddItemsDialog.newInstance().show(getFragmentManager(), "add_items");
            }
        });

        mPathView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPathMenu();
            }
        });

        // load files
        showFiles(mStorage.getExternalStorageDirectory());

        checkPermission();
    }

    private void showPathMenu() {
        PopupMenu popupmenu = new PopupMenu(this, mPathView);
        MenuInflater inflater = popupmenu.getMenuInflater();
        inflater.inflate(R.menu.path_menu, popupmenu.getMenu());

        popupmenu.getMenu().findItem(R.id.go_internal).setVisible(!mInternal);
        popupmenu.getMenu().findItem(R.id.go_external).setVisible(mInternal);

        popupmenu.show();

        popupmenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {

            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.go_up:
                        String previousPath = getPreviousPath();
                        mTreeSteps = 0;
                        showFiles(previousPath);
                        break;
                    case R.id.go_internal:
                        showFiles(mStorage.getInternalFilesDirectory());
                        mInternal = true;
                        break;
                    case R.id.go_external:
                        showFiles(mStorage.getExternalStorageDirectory());
                        mInternal = false;
                        break;
                }
                return true;
            }
        });
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

            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                String mimeType =  MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExt(file.getAbsolutePath()));
                Uri apkURI = FileProvider.getUriForFile(
                        this,
                        getApplicationContext()
                                .getPackageName() + ".provider", file);
                intent.setDataAndType(apkURI, mimeType);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                if (mStorage.getSize(file, SizeUnit.KB) > 500) {
                    Helper.showSnackbar("The file is too big for preview", mRecyclerView);
                    return;
                }
                Intent intent = new Intent(this, ViewTextActivity.class);
                intent.putExtra(ViewTextActivity.EXTRA_FILE_NAME, file.getName());
                intent.putExtra(ViewTextActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
                startActivity(intent);
            }

        }
    }

    @Override
    public void onLongClick(File file) {
        UpdateItemDialog.newInstance(file.getAbsolutePath()).show(getFragmentManager(), "update_item");
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
        int lastIndexOf = path.lastIndexOf(File.separator);
        if (lastIndexOf < 0) {
            Helper.showSnackbar("Can't go anymore", mRecyclerView);
            return getCurrentPath();
        }
        return path.substring(0, lastIndexOf);
    }

    @Override
    public void onOptionClick(int which, String path) {
        switch (which) {
            case R.id.new_file:
                NewTextFileDialog.newInstance().show(getFragmentManager(), "new_file_dialog");
                break;
            case R.id.new_folder:
                NewFolderDialog.newInstance().show(getFragmentManager(), "new_folder_dialog");
                break;
            case R.id.delete:
                ConfirmDeleteDialog.newInstance(path).show(getFragmentManager(), "confirm_delete");
                break;
            case R.id.rename:
                RenameDialog.newInstance(path).show(getFragmentManager(), "rename");
                break;
            case R.id.move:
                mMovingText.setText(getString(R.string.moving_file, mStorage.getFile(path).getName()));
                mMovingPath = path;
                mCopy = false;
                mMovingLayout.setVisibility(View.VISIBLE);
                break;
            case R.id.copy:
                mMovingText.setText(getString(R.string.copy_file, mStorage.getFile(path).getName()));
                mMovingPath = path;
                mCopy = true;
                mMovingLayout.setVisibility(View.VISIBLE);
                break;
        }
    }

    @Override
    public void onNewFolder(String name) {
        String currentPath = getCurrentPath();
        String folderPath = currentPath + File.separator + name;
        boolean created = mStorage.createDirectory(folderPath);
        if (created) {
            showFiles(currentPath);
            Helper.showSnackbar("New folder created: " + name, mRecyclerView);
        } else {
            Helper.showSnackbar("Failed create folder: " + name, mRecyclerView);
        }
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
        Helper.showSnackbar("New file created: " + name, mRecyclerView);
    }

    @Override
    public void onConfirmDelete(String path) {
        if (mStorage.getFile(path).isDirectory()) {
            mStorage.deleteDirectory(path);
            Helper.showSnackbar("Folder was deleted", mRecyclerView);
        } else {
            mStorage.deleteFile(path);
            Helper.showSnackbar("File was deleted", mRecyclerView);
        }
        showFiles(getCurrentPath());
    }

    @Override
    public void onRename(String fromPath, String toPath) {
        mStorage.rename(fromPath, toPath);
        showFiles(getCurrentPath());
        Helper.showSnackbar("Renamed", mRecyclerView);
    }

//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        MenuInflater inflater = getMenuInflater();
//        inflater.inflate(R.menu.main_menu, menu);
//        return true;
//    }
//
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        switch (item.getItemId()) {
//            case R.id.order:
//                break;
//            case R.id.filter:
//                break;
//        }
//
//        return super.onOptionsItemSelected(item);
//    }

    private void checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager
                .PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[]
            grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showFiles(mStorage.getExternalStorageDirectory());
        } else {
            finish();
        }
    }
}
