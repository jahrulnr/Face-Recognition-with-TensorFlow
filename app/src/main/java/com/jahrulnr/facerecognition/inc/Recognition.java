package com.jahrulnr.facerecognition.inc;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.view.PreviewView;

import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Recognition{

    private static final String TAG = "Recognition-ImageAnalysis.Analyzer";
    private Activity activity;
    private Firebase firebase;
    private FaceDetector faceDetector;
    private PreviewView tv;
    private ImageView iv;
    private EditText ev;
    private Bitmap bitmap, bitmap_detect;
    private Canvas canvas;
    private Paint dotPaint, linePaint;
    private float widthScaleFactor = 1.0f;
    private float heightScaleFactor = 1.0f;
    private InputImage fbImage;
    private int lens;

    private Interpreter tfLite;
    private String modelFile="mobile_face_net.tflite"; //model name
    private HashMap<String, SimilarityClassifier> registered = new HashMap<>(); //saved Faces
    boolean flipX=false;
    float[][] embeedings;
    int[] intValues;
    int OUTPUT_SIZE=192; //Output size of model
    int INPUT_SIZE =112;  //Input size for model
    float IMAGE_MEAN = 128.0f;
    float IMAGE_STD = 128.0f;
    boolean isModelQuantized=false;

    public boolean start=true;
    private String reco_name = "";
    public boolean developerMode = false;
    public float distance= 1.0f;
    public Recognition(Activity activity, PreviewView tv, ImageView iv, int lens) {
        firebase = new Firebase(activity);
        this.activity = activity;
        this.tv = tv;
        this.iv = iv;
        this.lens = lens;
        setup();
    }
    public Recognition(Activity activity, PreviewView tv, ImageView iv, EditText ev, int lens){
        this(activity, tv, iv, lens);
        this.ev = ev;
    }

    void setup(){
        if(developerMode){
            iv.setBackground(null);
        }
        else if(iv.getVisibility() == View.VISIBLE)
            activity.runOnUiThread(()->iv.setVisibility(View.GONE));
        this.registered = readFromServerSP();

        try{
            tfLite = new Interpreter(loadModelFile(activity, modelFile));
        } catch (IOException e) {
            e.printStackTrace();
        }

        initDetector();
    }

    public ImageAnalysis.Analyzer exec(){
        ImageAnalysis.Analyzer analyzer = null;
        if(lens == CameraSelector.LENS_FACING_FRONT)
            flipX = true;
        else
            flipX = false;
        try {
            analyzer = new Analyzer();
        }
        catch (OutOfMemoryError e){
            e.printStackTrace();
            if(bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            if(bitmap_detect != null && !bitmap_detect.isRecycled()) bitmap_detect.recycle();
            analyzer = new Analyzer();
        }
        return analyzer;
    }

    public class Analyzer implements ImageAnalysis.Analyzer{
        @SuppressLint("UnsafeOptInUsageError")
        @Override
        public void analyze(ImageProxy image) {
            if (image != null && image.getImage() != null) {
                fbImage = InputImage.fromMediaImage(image.getImage(), /*degreesToFirebaseRotation*/(image.getImageInfo().getRotationDegrees()));
                if(developerMode) initDrawingUtils();
                detectFaces(image);
            }
        }
    }

    private void detectFaces(ImageProxy imageProxy) {
        faceDetector
            .process(fbImage)
            .addOnSuccessListener(firebaseVisionFaces -> {
                if (!firebaseVisionFaces.isEmpty() && start) {
                    if(developerMode)
                        processFaces(firebaseVisionFaces);
                    if(firebaseVisionFaces.size() > 0) {
                        Face face = firebaseVisionFaces.get(0);
                        RectF boundingBox = new RectF(face.getBoundingBox());
                        @SuppressLint("UnsafeOptInUsageError")
                        Bitmap cropped_face = toBitmap(imageProxy.getImage());
                        if(cropped_face != null) {
                            cropped_face = getCropBitmapByCPU(
                                    rotateBitmap(cropped_face, imageProxy.getImageInfo().getRotationDegrees(), false, false), boundingBox);
                            if(flipX)
                                cropped_face = rotateBitmap(cropped_face, 0, flipX, false);
                            recognizeImage(getResizedBitmap(cropped_face, INPUT_SIZE, INPUT_SIZE));
                        }
                    }
                }
                else if(developerMode) {
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY);
                }
            })
            .addOnFailureListener(e -> {
                Log.i(TAG, e.toString());
            })
            .addOnCompleteListener(task -> imageProxy.close());
    }

    private void initDrawingUtils() {
        if(fbImage == null) return;
        bitmap = Bitmap.createBitmap(tv.getWidth(), tv.getHeight(), Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        dotPaint = new Paint();
        dotPaint.setColor(Color.RED);
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setStrokeWidth(2f);
        dotPaint.setAntiAlias(true);
        linePaint = new Paint();
        linePaint.setColor(Color.GREEN);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2f);
        widthScaleFactor = canvas.getWidth() / (fbImage.getWidth() * 1.0f);
        heightScaleFactor = canvas.getHeight() / (fbImage.getHeight() * 1.0f);
    }

    private void initDetector() {
        FaceDetectorOptions detectorOptions =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                        .build();
        faceDetector = FaceDetection.getClient(detectorOptions);
    }

    private MappedByteBuffer loadModelFile(Activity activity, String MODEL_FILE) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public Task<Void> insertToSP(String user) {
        start=false;
        SimilarityClassifier result = new SimilarityClassifier(
                "0", "", -1f, embeedings);
        registered.put(user, result);

        // save to local
        String jsonString = new Gson().toJson(registered);
        SharedPreferences sharedPreferences = activity.getSharedPreferences("user", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("map", jsonString);
        editor.apply();
        start=true;

        // save to firebase
        return firebase.save(firebase.user + "/" + user, registered.get(user));
    }

    //Load Faces from Shared Preferences.Json String to Recognition object
    private HashMap<String, SimilarityClassifier> readFromLocalSP(){
        HashMap<String, SimilarityClassifier> retrievedMap = new HashMap<String, SimilarityClassifier>();
        SharedPreferences sharedPreferences = activity.getSharedPreferences("user", MODE_PRIVATE);
        String defValue = new Gson().toJson(new HashMap<String, SimilarityClassifier>());
        String json=sharedPreferences.getString("map",defValue);
        TypeToken<HashMap<String, SimilarityClassifier>> token = new TypeToken<HashMap<String, SimilarityClassifier>>() {};
        retrievedMap = new Gson().fromJson(json,token.getType());
        return retrievedMap;
    }

    //Load Faces from Firebase to Recognition object
    private HashMap<String, SimilarityClassifier> readFromServerSP(){
        HashMap<String, SimilarityClassifier> retrievedMap = new HashMap<String, SimilarityClassifier>();
        firebase.read(firebase.user).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.getChildrenCount() > 0){
                    for(DataSnapshot data : snapshot.getChildren()){
                        SimilarityClassifier u = data.getValue(SimilarityClassifier.class);
                        retrievedMap.put(data.getKey(), u);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

        if(developerMode)
            Toast.makeText(activity, "Recognitions Loaded", Toast.LENGTH_SHORT).show();
        return retrievedMap;
    }

    public String reco_name(){
        return reco_name == null ? "" : reco_name;
    }
    public String reco_name(String set){
        return reco_name = set;
    }

    public Bitmap getRecognizeImage(){
        return bitmap_detect;
    }

    public void recognizeImage(Bitmap temp) {
        //Create ByteBuffer to store normalized image
        bitmap_detect = temp;
        //Create ByteBuffer to store normalized image
        ByteBuffer imgData = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4);
        imgData.order(ByteOrder.nativeOrder());
        intValues = new int[INPUT_SIZE * INPUT_SIZE];
        temp.getPixels(intValues, 0, temp.getWidth(), 0, 0, temp.getWidth(), temp.getHeight());
        imgData.rewind();

        for (int i = 0; i < INPUT_SIZE; ++i) {
            for (int j = 0; j < INPUT_SIZE; ++j) {
                int pixelValue = intValues[i * INPUT_SIZE + j];
                if (isModelQuantized) {
                    // Quantized model
                    imgData.put((byte) ((pixelValue >> 16) & 0xFF));
                    imgData.put((byte) ((pixelValue >> 8) & 0xFF));
                    imgData.put((byte) (pixelValue & 0xFF));
                } else { // Float model
                    imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                }
            }
        }
        //imgData is input to our model
        embeedings = new float[1][OUTPUT_SIZE]; //output of model will be stored in this variable
        tfLite.run(imgData, embeedings);

        float distance_local;
        if (registered.size() > 0) {
            final List<Pair<String, Float>> nearest = findNearest(embeedings[0]);//Find 2 closest matching face
            if (nearest.get(0) != null) {
                final String name = nearest.get(0).first; //get name and distance of closest matching face
                // label = name;
                distance_local = nearest.get(0).second;
                if (developerMode){
                    if(distance_local<distance) //If distance between Closest found face is more than 1.000 ,then output UNKNOWN face.
                        reco_name = ("Nearest: "+name +"\nDist: "+ String.format("%.3f",distance_local)+"\n2nd Nearest: "+nearest.get(1).first +"\nDist: "+ String.format("%.3f",nearest.get(1).second));
                    else
                        reco_name = ("Unknown "+"\nDist: "+String.format("%.3f",distance_local)+"\nNearest: "+name +"\nDist: "+ String.format("%.3f",distance_local)+"\n2nd Nearest: "+nearest.get(1).first +"\nDist: "+ String.format("%.3f",nearest.get(1).second));
                }
                else {
                    if(distance_local<distance) //If distance between Closest found face is more than 1.000 ,then output UNKNOWN face.
                        reco_name = (name);
                    else
                        reco_name = ("Unknown");
                }
            }
        }
    }

    //Compare Faces by distance between face embeddings
    private List<Pair<String, Float>> findNearest(float[] emb) {
        List<Pair<String, Float>> neighbour_list = new ArrayList<Pair<String, Float>>();
        Pair<String, Float> ret = null; //to get closest match
        Pair<String, Float> prev_ret = null; //to get second closest match
        for (Map.Entry<String, SimilarityClassifier> entry : registered.entrySet()){
            final String name = entry.getKey();
            final ArrayList knownEmb = (ArrayList) ((ArrayList) entry.getValue().getExtra()).get(0);
            float distance = 0;
            for (int j = 0; j < emb.length; j++) {
                float diff = emb[j] - ((Double) knownEmb.get(j)).floatValue();
                distance += diff*diff;
            }
            System.out.println("Distance original : " + distance);
            distance = (float) Math.sqrt(distance);
            distance = (float) (Math.pow(distance, 2)+distance*0.25);
            System.out.println("Distance:  " + distance);
            if (ret == null || distance < ret.second) {
                prev_ret=ret;
                ret = new Pair<>(name, distance);
            }
        }
        if(prev_ret==null) prev_ret=ret;
        neighbour_list.add(ret);
        neighbour_list.add(prev_ret);

        return neighbour_list;
    }

    private void processFaces(List<Face> faces) {
        for (Face face : faces) {
            try {
                drawContours(face.getContour(FaceContour.FACE).getPoints());
                drawContours(face.getContour(FaceContour.LEFT_EYEBROW_BOTTOM).getPoints());
                drawContours(face.getContour(FaceContour.RIGHT_EYEBROW_BOTTOM).getPoints());
                drawContours(face.getContour(FaceContour.LEFT_EYE).getPoints());
                drawContours(face.getContour(FaceContour.RIGHT_EYE).getPoints());
                drawContours(face.getContour(FaceContour.LEFT_EYEBROW_TOP).getPoints());
                drawContours(face.getContour(FaceContour.RIGHT_EYEBROW_TOP).getPoints());
                drawContours(face.getContour(FaceContour.LOWER_LIP_BOTTOM).getPoints());
                drawContours(face.getContour(FaceContour.LOWER_LIP_TOP).getPoints());
                drawContours(face.getContour(FaceContour.UPPER_LIP_BOTTOM).getPoints());
                drawContours(face.getContour(FaceContour.UPPER_LIP_TOP).getPoints());
                drawContours(face.getContour(FaceContour.NOSE_BRIDGE).getPoints());
                drawContours(face.getContour(FaceContour.NOSE_BOTTOM).getPoints());
            }
            catch (NullPointerException e){
                return;
            }
        }
        iv.setImageBitmap(bitmap);
    }

    private void drawContours(List<PointF> points) {
        int counter = 0;
        for (PointF point : points) {
            if (counter != points.size() - 1) {
                canvas.drawLine(translateX(point.x),
                        translateY(point.y),
                        translateX(points.get(counter + 1).x),
                        translateY(points.get(counter + 1).y),
                        linePaint);
            } else {
                canvas.drawLine(translateX(point.x),
                        translateY(point.y),
                        translateX(points.get(0).x),
                        translateY(points.get(0).y),
                        linePaint);
            }
            counter++;
            canvas.drawCircle(translateX(point.x), translateY(point.y), 6, dotPaint);
        }
    }

    private float translateY(float y) {
        return y * heightScaleFactor;
    }

    private float translateX(float x) {
        float scaledX = x * widthScaleFactor;
        if (lens == CameraSelector.LENS_FACING_FRONT) {
            return canvas.getWidth() - scaledX;
        } else {
            return scaledX;
        }
    }

    private static Bitmap rotateBitmap(
            Bitmap bitmap, int rotationDegrees, boolean flipX, boolean flipY) {
        if(bitmap == null) return null;
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        matrix.postScale(flipX ? -1.0f : 1.0f, flipY ? -1.0f : 1.0f);
        Bitmap temp = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        return temp;
    }

    private Bitmap getCropBitmapByCPU(Bitmap source, RectF cropRectF) {
        if(source == null || cropRectF == null || !start)
            return null;
        Bitmap resultBitmap = Bitmap.createBitmap((int) cropRectF.width(),
                (int) cropRectF.height(), Bitmap.Config.ARGB_8888);
        Canvas cavas = new Canvas(resultBitmap);

        // draw background
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setColor(Color.WHITE);
        cavas.drawRect(
                new RectF(0, 0, cropRectF.width(), cropRectF.height()),
                paint);

        Matrix matrix = new Matrix();
        matrix.postTranslate(-cropRectF.left, -cropRectF.top);
        cavas.drawBitmap(source, matrix, paint);

        return resultBitmap;
    }

    public Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        return Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
    }

    //IMPORTANT. If conversion not done ,the toBitmap conversion does not work on some devices.
    private static byte[] YUV_420_888toNV21(Image image) {

        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = width*height;
        int uvSize = width*height/4;

        byte[] nv21 = new byte[ySize + uvSize*2];

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer(); // Y
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer(); // U
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer(); // V

        int rowStride = image.getPlanes()[0].getRowStride();
        assert(image.getPlanes()[0].getPixelStride() == 1);

        int pos = 0;

        if (rowStride == width) { // likely
            yBuffer.get(nv21, 0, ySize);
            pos += ySize;
        }
        else {
            long yBufferPos = -rowStride; // not an actual position
            for (; pos<ySize; pos+=width) {
                yBufferPos += rowStride;
                yBuffer.position((int) yBufferPos);
                yBuffer.get(nv21, pos, width);
            }
        }

        rowStride = image.getPlanes()[2].getRowStride();
        int pixelStride = image.getPlanes()[2].getPixelStride();

        assert(rowStride == image.getPlanes()[1].getRowStride());
        assert(pixelStride == image.getPlanes()[1].getPixelStride());

        if (pixelStride == 2 && rowStride == width && uBuffer.get(0) == vBuffer.get(1)) {
            // maybe V an U planes overlap as per NV21, which means vBuffer[1] is alias of uBuffer[0]
            byte savePixel = vBuffer.get(1);
            try {
                vBuffer.put(1, (byte)~savePixel);
                if (uBuffer.get(0) == (byte)~savePixel) {
                    vBuffer.put(1, savePixel);
                    vBuffer.position(0);
                    uBuffer.position(0);
                    vBuffer.get(nv21, ySize, 1);
                    uBuffer.get(nv21, ySize + 1, uBuffer.remaining());
                    return nv21; // shortcut
                }
            }
            catch (ReadOnlyBufferException ex) {
                // unfortunately, we cannot check if vBuffer and uBuffer overlap
            }

            vBuffer.put(1, savePixel);
        }

        for (int row=0; row<height/2; row++) {
            for (int col=0; col<width/2; col++) {
                int vuPos = col*pixelStride + row*rowStride;
                nv21[pos++] = vBuffer.get(vuPos);
                nv21[pos++] = uBuffer.get(vuPos);
            }
        }

        return nv21;
    }

    private Bitmap toBitmap(Image image) {
        byte[] nv21=YUV_420_888toNV21(image);
        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);
        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }
}