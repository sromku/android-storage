package com.snatik.storage.app;

import android.os.Bundle;
import android.os.SystemClock;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import com.snatik.storage.SimpleStorage;
import com.snatik.storage.Storage;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Storage storage = SimpleStorage.getExternalStorage();
                String dir =  "my_dir_" + SystemClock.elapsedRealtime();
                boolean wasCreated = storage.createDirectory("my_dir_" + SystemClock.elapsedRealtime(), true);
                if (wasCreated) {
                    Snackbar.make(view, dir + " was created successfully", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                }
            }
        });
    }

}
