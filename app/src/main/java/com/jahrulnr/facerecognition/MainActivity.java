package com.jahrulnr.facerecognition;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.jahrulnr.facerecognition.inc.Recognition;
import com.jahrulnr.facerecognition.trash.ThreadException;
import com.jahrulnr.facerecognition.inc.getPermission;

import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    getPermission perms;
    ImageAnalysis imageAnalysis;
    Recognition analyzer;
    CardView faceContainer;
    PreviewView tv_face;
    ImageView iv_face, iv_dev;
    EditText username;
    ImageButton btnChangeCamera;
    Button btnUlang, btnLogin, btnRegister;

    int lens = CameraSelector.LENS_FACING_FRONT; //Default Back Camera
    CameraSelector cameraSelector;
    ProcessCameraProvider cameraProvider;
    ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private static final Size size = new Size(200, 200);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        System.out.println("onCreate");
        Thread.setDefaultUncaughtExceptionHandler(new ThreadException(this));
        setContentView(R.layout._activity_main);
        perms = new getPermission(this);
    }

    @Override
    protected void onStart() {
        super.onStart();

        System.out.println("onStart");
        faceContainer = findViewById(R.id.faceContainer);
        tv_face = findViewById(R.id.face_tv_face);
        iv_face = findViewById(R.id.face_iv_face);
        iv_dev = findViewById(R.id.face_iv_dev);
        username = findViewById(R.id.ev_username);

        if (!perms.check()) {
            ActivityCompat.requestPermissions(this, getPermission.REQUIRED_PERMISSIONS, getPermission.REQUEST_CODE_PERMISSION);
        }

        setButton();
        tv_face.post(this::initCamera);
    }

    void setButton(){
        btnChangeCamera = findViewById(R.id.btn_changeCamera);
        btnUlang = findViewById(R.id.btn_ulang);
        btnLogin = findViewById(R.id.btn_login);
        btnRegister = findViewById(R.id.btn_register);
        btnChangeCamera.setOnClickListener(v -> changeCamera());
        btnUlang.setOnClickListener(v -> initCamera());
        btnLogin.setOnClickListener(v -> {
            if(analyzer.reco_name().length() > 0){
                username.setText(analyzer.reco_name());
            }
            if(analyzer.getRecognizeImage() != null) {
                iv_dev.setImageBitmap(analyzer.getRecognizeImage());
                iv_dev.setVisibility(View.VISIBLE);
            }
        });
        btnRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, Register.class));
            overridePendingTransition(0, 0);
            finish();
        });
    }

    private void changeCamera(){
        if(lens == CameraSelector.LENS_FACING_FRONT)
            lens = CameraSelector.LENS_FACING_BACK;
        else
            lens = CameraSelector.LENS_FACING_FRONT;
        cameraProvider.unbindAll();
        initCamera();
    }

    private void initCamera() {
        analyzer = new Recognition(this, tv_face, iv_face, username, lens);
        username.setText("");

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(tv_face.getSurfaceProvider());
                cameraProvider = cameraProviderFuture.get();
                cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(lens)
                        .build();
                imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(size)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) //Latest frame is shown
                        .build();
                imageAnalysis.setAnalyzer(command -> {
                    command.run();
                    if(analyzer.reco_name().length() > 0 && !analyzer.reco_name().equals("Unknown")){
                        runOnUiThread(() -> {
                            System.err.println("analyzer.reco_name() "+analyzer.reco_name());
                            username.setText(analyzer.reco_name());
                            analyzer.reco_name("");
                            imageAnalysis.clearAnalyzer();
                            cameraProvider.unbindAll();
                        });
                    }
                }, analyzer.exec());
                cameraProvider.bindToLifecycle( this, cameraSelector, imageAnalysis, preview);
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this in Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(!perms.result(requestCode)){
            Toast.makeText(this, "Aplikasi ini membutuhkan izin kamera.", Toast.LENGTH_SHORT).show();
            onStart();
        }
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setMessage("Keluar dari aplikasi?")
                .setPositiveButton("Ya", (dialog, which) -> {
                    if(imageAnalysis != null) {
                        imageAnalysis.clearAnalyzer();
                        cameraProvider.unbindAll();
                    }
                    finish();
                })
                .setNegativeButton("Tidak", null)
                .create()
                .show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(imageAnalysis != null){
            imageAnalysis.clearAnalyzer();
            cameraProvider.unbindAll();
        }
        System.out.println("onPause");
    }

    @Override
    protected void onResume() {
        super.onResume();
//        initCamera();
        System.out.println("onResume");
    }
}