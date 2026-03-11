package com.frontline.shooter;

import android.graphics.Canvas;
import android.view.SurfaceHolder;

public class GameThread extends Thread {
    public volatile boolean running = false;
    private final GameView gameView;
    private final SurfaceHolder holder;

    public GameThread(GameView view, SurfaceHolder holder) {
        this.gameView = view;
        this.holder   = holder;
    }

    @Override
    public void run() {
        while (running) {
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) {
                    synchronized (holder) {
                        gameView.tick();
                        gameView.render(canvas);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas); }
                    catch (Exception ignored) {}
                }
            }

            // ~60 fps cap
            try { Thread.sleep(16); } catch (InterruptedException ignored) {}
        }
    }
}
