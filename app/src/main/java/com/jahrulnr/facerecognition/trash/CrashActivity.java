package com.jahrulnr.facerecognition.trash;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class CrashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        new AlertDialog.Builder(this)
                .setTitle("Error!")
                .setMessage(savedInstanceState.getString("error"))
                .create();
    }
}
