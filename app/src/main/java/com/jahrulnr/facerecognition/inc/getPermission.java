package com.jahrulnr.facerecognition.inc;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

public class getPermission {
    public static final int REQUEST_CODE_PERMISSION = 101;
    public static final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.READ_EXTERNAL_STORAGE"};
    private static Activity activity;

    public getPermission(Activity activity){
        this.activity = activity;
    }

    public boolean check() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public boolean result(int requestCode){
        if (requestCode == REQUEST_CODE_PERMISSION) {
            if (!check()) {
                Toast.makeText(activity,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show();
                return false;
            }else{
                return true;
            }
        }
        return false;
    }
}
