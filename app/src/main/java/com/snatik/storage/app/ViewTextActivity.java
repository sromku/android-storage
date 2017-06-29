package com.snatik.storage.app;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.graphics.drawable.DrawerArrowDrawable;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.TextView;

import com.snatik.storage.Storage;

/**
 * Created by sromku on June, 2017.
 */
public class ViewTextActivity extends AppCompatActivity {

    public final static String EXTRA_FILE_NAME = "name";
    public final static String EXTRA_FILE_PATH = "path";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String name = getIntent().getStringExtra(EXTRA_FILE_NAME);
        String path = getIntent().getStringExtra(EXTRA_FILE_PATH);

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

        TextView textView = (TextView) findViewById(R.id.content);
        Storage storage = new Storage(this);
        byte[] bytes = storage.readFile(path);
        textView.setText(new String(bytes));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
