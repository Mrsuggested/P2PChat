package com.example.p2pchat;

import android.app.Application;
import android.util.Log;

public class App extends Application {
    private static final String TAG = "P2PChat";
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "P2P Chat 启动 - 端到端加密通信系统");
    }
}
