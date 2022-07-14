package com.jahrulnr.facerecognition.inc;

import android.annotation.SuppressLint;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;

public class SimilarityClassifier {
    private String id;
    private String title;
    private Float distance;
    private Object extra;

    public SimilarityClassifier(){}

    public SimilarityClassifier(
            String id, String title, Float distance, Object extra) {
        this();
        setId(id);
        setTitle(title);
        setDistance(distance);
        setExtra(extra);
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public void setDistance(Float distance) {
        this.distance = distance;
    }

    public Float getDistance() {
        return distance;
    }

    public void setExtra(Object extra) {
        try {
            this.extra = new Gson().toJson((float[][]) extra);
        }
        catch (RuntimeException e){
            if(e.getMessage().contains("ArrayList")){
                ArrayList extraList = (ArrayList) extra;
                extraList = (ArrayList) extraList.get(0);
                float[][] arrExtra = new float[1][extraList.size()];
                for(int i = 0; i < extraList.size(); i++){
                    arrExtra[0][i] = ((Double) extraList.get(i)).floatValue();
                }
                this.extra = new Gson().toJson(arrExtra);
            }else{
                e.printStackTrace();
            }
        }
    }

    public Object getExtra(){
        TypeToken<ArrayList> typeToken = new TypeToken<ArrayList>(){};
        return new Gson().fromJson((String) this.extra, typeToken.getType());
    }

    @SuppressLint("DefaultLocale")
    @Override
    public String toString() {
        String resultString = "";
        if (id != null) {
            resultString += "[" + getId() + "] ";
        }

        if (title != null) {
            resultString += getTitle() + " ";
        }

        if (distance != null) {
            resultString += String.format("(%.1f%%) ", getDistance() * 100.0f);
        }

        return resultString.trim();
    }
}
