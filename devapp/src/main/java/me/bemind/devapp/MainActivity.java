package me.bemind.devapp;

import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.sromku.simple.storage.SimpleStorage;
import com.sromku.simple.storage.Storage;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Storage storage = SimpleStorage.getExternalStorage(Environment.DIRECTORY_PICTURES);
        storage.createDirectory("DEVAPP1");
        storage.createFile("DEVAPP1","FileProva.txt","File di prova");


        Log.d("TAG","file scritto");
    }
}
