package com.example.lostfoundcampus;

import android.app.Application;

import com.example.lostfoundcampus.data.remote.SupabaseClient;

public class LostFoundApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SupabaseClient.init(this);
    }
}
