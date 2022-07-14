package com.jahrulnr.facerecognition;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

public class Launcher extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout._launcher_activity);
        Handler handler = new Handler();
        handler.postDelayed(() -> {
            Intent intent =new Intent(Launcher.this, MainActivity.class);
            startActivity(intent);
            overridePendingTransition(0, 0);
            finish();
        },2500);
    }
}
