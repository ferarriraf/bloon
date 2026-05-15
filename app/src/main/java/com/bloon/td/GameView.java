package com.bloon.td;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private Thread thread;
    private volatile boolean running;
    private Game game;
    private long lastNanos;

    public GameView(Context ctx) {
        super(ctx);
        getHolder().addCallback(this);
        setFocusable(true);
        game = new Game();
    }

    public void resume() {
        running = true;
        thread = new Thread(this, "GameLoop");
        thread.start();
    }

    public void pause() {
        running = false;
        if (thread != null) {
            try { thread.join(); } catch (InterruptedException ignored) {}
            thread = null;
        }
    }

    @Override public void run() {
        lastNanos = System.nanoTime();
        SurfaceHolder h = getHolder();
        while (running) {
            long now = System.nanoTime();
            float dt = Math.min(0.05f, (now - lastNanos) / 1_000_000_000f);
            lastNanos = now;
            game.update(dt);
            if (!h.getSurface().isValid()) continue;
            Canvas c = null;
            try {
                c = h.lockCanvas();
                if (c != null) game.draw(c);
            } finally {
                if (c != null) h.unlockCanvasAndPost(c);
            }
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        game.onTouch(e);
        return true;
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {}
    @Override public void surfaceChanged(SurfaceHolder holder, int fmt, int w, int h) {
        game.resize(w, h);
    }
    @Override public void surfaceDestroyed(SurfaceHolder holder) {}
}
