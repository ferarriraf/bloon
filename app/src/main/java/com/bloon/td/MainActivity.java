package com.bloon.td;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity {
    private GameView view;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new GameView(this);
        setContentView(view);
    }

    @Override protected void onResume() { super.onResume(); view.resume(); }
    @Override protected void onPause() { super.onPause(); view.pause(); }
}
