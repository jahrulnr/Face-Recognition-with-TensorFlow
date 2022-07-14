package com.jahrulnr.facerecognition.inc;

import android.app.Activity;
import android.net.Uri;

import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

public class Firebase {
    final private String dbHost = "https://facedetector-e9e13-default-rtdb.asia-southeast1.firebasedatabase.app/";
    final private String stHost = "gs://facedetector-e9e13.appspot.com";

    // config
    public final String user = "user";

    Activity activity;
    FirebaseDatabase database;
    DatabaseReference reference;
    StorageReference storageReference;
    private String TAG = this.getClass().getName();

    public Firebase(Activity activity){
        this.activity = activity;
        database = FirebaseDatabase.getInstance(dbHost);
        storageReference = FirebaseStorage.getInstance(stHost).getReference();
    }

    public DatabaseReference getReference() {
        return reference.getRef();
    }

    private void setReference(String reference){
        this.reference = database.getReference().child(reference);
    }

    private void setReference(String reference, String child){
        this.reference = database.getReference(reference).child(child);
    }

    private void setStorageReference(String child){
        storageReference.child(child);
    }

    public DatabaseReference read(String reference){
        setReference(reference);
        return this.reference;
    }

    public Task<Void> save(String reference, Object value){
        setReference(reference);
        return this.reference.setValue(value);
    }

    public UploadTask upload(Uri uri){
        return storageReference.putFile(uri);
    }
}
