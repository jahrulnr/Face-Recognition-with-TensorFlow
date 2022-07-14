package com.jahrulnr.facerecognition;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Size;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.jahrulnr.facerecognition.inc.Firebase;
import com.jahrulnr.facerecognition.inc.Recognition;
import com.jahrulnr.facerecognition.inc.ThreadException;
import com.jahrulnr.facerecognition.inc.getPermission;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class Register  extends AppCompatActivity {
    getPermission perms;
    ImageAnalysis imageAnalysis;
    Recognition analyzer;
    CardView faceContainer;
    PreviewView tv_face;
    ImageView iv_face, iv_dev;
    ImageButton btnChangeCamera;
    EditText username;
    Button btnUlang, btnLogin, btnRegister;

    int lens = CameraSelector.LENS_FACING_FRONT; //Default Back Camera
    CameraSelector cameraSelector;
    ProcessCameraProvider cameraProvider;
    ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private static final Size size = new Size(200, 200);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler(new ThreadException(this));
        setContentView(R.layout._register_activity);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!perms.check()) {
            ActivityCompat.requestPermissions(this, getPermission.REQUIRED_PERMISSIONS, getPermission.REQUEST_CODE_PERMISSION);
        }

        perms = new getPermission(this);
        faceContainer = findViewById(R.id.faceContainer);
        tv_face = findViewById(R.id.face_tv_face);
        iv_face = findViewById(R.id.face_iv_face);
        iv_dev = findViewById(R.id.face_iv_dev);
        username = findViewById(R.id.ev_username);

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
            startActivity(new Intent(this, MainActivity.class));
            overridePendingTransition(0, 0);
            finish();
        });
        btnRegister.setOnClickListener(v -> {
            if(username.getText().length() == 0){
                Toast.makeText(this, "Username tidak boleh kosong.", Toast.LENGTH_SHORT).show();
                return;
            }
            else if(username.getText().length() < 5){
                Toast.makeText(this, "Username minimal 5 karakter.", Toast.LENGTH_SHORT).show();
                return;
            }
            else if(analyzer.getRecognizeImage() == null){
                Toast.makeText(this, "Wajah tidak terdeteksi.", Toast.LENGTH_SHORT).show();
                return;
            }

            new Firebase(this).read("data").addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if(snapshot.hasChild(username.getText().toString())){
                        Toast.makeText(Register.this, "Username sudah ada.", Toast.LENGTH_SHORT).show();
                        return;
                    }else{
                        analyzer.insertToServerSP(username.getText().toString()).addOnSuccessListener(command -> {
                            Toast.makeText(Register.this, "Akun berhasil dibuat. Silakan login.", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(Register.this, MainActivity.class));
                            overridePendingTransition(0, 0);
                            finish();
                        }).addOnFailureListener(command -> Toast.makeText(Register.this, "Akun gagal dibuat.", Toast.LENGTH_SHORT).show());
                        return;
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {

                }
            });
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
        analyzer = new Recognition(this, tv_face, iv_face, lens);

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                Preview preview = new Preview.Builder().setTargetResolution(size).build();
                preview.setSurfaceProvider(tv_face.getSurfaceProvider());
                cameraProvider = cameraProviderFuture.get();
                cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(lens)
                        .build();
                imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(size)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(command -> {
                    command.run();
                    if(analyzer.getRecognizeImage() != null){
                        runOnUiThread(() -> {
                            System.err.println("analyzer.reco_name() "+analyzer.reco_name());
                            iv_face.setImageBitmap(analyzer.getRecognizeImage());
                            imageAnalysis.clearAnalyzer();
                            cameraProvider.unbindAll();
                        });
                    }
                }, analyzer.exec());
                cameraProvider.bindToLifecycle( this, cameraSelector, imageAnalysis, preview);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        System.out.println("onResume");
    }
}
