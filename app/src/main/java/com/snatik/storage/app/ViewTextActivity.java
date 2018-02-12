package com.snatik.storage.app;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.graphics.drawable.DrawerArrowDrawable;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

import com.snatik.storage.EncryptConfiguration;
import com.snatik.storage.Storage;

/**
 * Created by sromku on June, 2017.
 */
public class ViewTextActivity extends AppCompatActivity {

    public final static String EXTRA_FILE_NAME = "name";
    public final static String EXTRA_FILE_PATH = "path";

    private final static String IVX = "abcdefghijklmnop";
    private final static String SECRET_KEY = "secret1234567890";
    private final static byte[] SALT = "0000111100001111".getBytes();

    private TextView mContentView;
    private String mPath;
    private Storage mStorage;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String name = getIntent().getStringExtra(EXTRA_FILE_NAME);
        mPath = getIntent().getStringExtra(EXTRA_FILE_PATH);

        setContentView(R.layout.activity_view_text_file);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        DrawerArrowDrawable drawerDrawable = new DrawerArrowDrawable(this);
        drawerDrawable.setColor(getResources().getColor(android.R.color.white));
        drawerDrawable.setProgress(1f);
        toolbar.setNavigationIcon(drawerDrawable);

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(name);
        getSupportActionBar().setHomeButtonEnabled(true);

        mContentView = (TextView) findViewById(R.id.content);
        mStorage = new Storage(this);
        byte[] bytes = mStorage.readFile(mPath);
        mContentView.setText(new String(bytes));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.text_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.decrypt:
                mStorage.setEncryptConfiguration(new EncryptConfiguration.Builder()
                        .setEncryptContent(IVX, SECRET_KEY, SALT)
                        .build());
                byte[] bytes = mStorage.readFile(mPath);
                if (bytes != null) {
                    mContentView.setText(new String(bytes));
                } else {
                    Helper.showSnackbar("Failed to decrypt", mContentView);
                }
                break;
        }

        return super.onOptionsItemSelected(item);
    }
}
