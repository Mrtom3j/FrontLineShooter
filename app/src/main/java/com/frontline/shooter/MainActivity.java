package com.frontline.shooter;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {

    GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full-screen immersive
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                             WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );

        SharedPreferences prefs = getSharedPreferences("FrontlinePrefs", MODE_PRIVATE);
        gameView = new GameView(this, prefs);

        // Root frame (game underneath, UI overlay on top)
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        root.addView(gameView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ─── HUD overlay ─────────────────────────────────────────────────────
        FrameLayout hud = new FrameLayout(this);
        root.addView(hud, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int sw = getResources().getDisplayMetrics().widthPixels;
        int sh = getResources().getDisplayMetrics().heightPixels;
        int dp = (int) getResources().getDisplayMetrics().density;

        // ── Joystick (bottom-left) ────────────────────────────────────────────
        JoystickView joystick = new JoystickView(this);
        int jSize = 220 * dp;
        FrameLayout.LayoutParams jParams = new FrameLayout.LayoutParams(jSize, jSize);
        jParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.LEFT;
        jParams.leftMargin  = 20 * dp;
        jParams.bottomMargin = 20 * dp;
        hud.addView(joystick, jParams);
        joystick.setListener((dx, dy) -> {
            gameView.joystickDx = dx;
            gameView.joystickDy = dy;
        });

        // ── Aim pad (bottom-right, large area) ───────────────────────────────
        AimPadView aimPad = new AimPadView(this);
        int apW = 260 * dp, apH = 200 * dp;
        FrameLayout.LayoutParams apParams = new FrameLayout.LayoutParams(apW, apH);
        apParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.RIGHT;
        apParams.rightMargin  = 180 * dp;
        apParams.bottomMargin = 20 * dp;
        hud.addView(aimPad, apParams);
        aimPad.setListener((angle, shoot) -> {
            gameView.aimAngle = angle;
            gameView.shooting = shoot;
        });

        // ── FIRE button ──────────────────────────────────────────────────────
        Button btnFire = makeBtn("🔥 FIRE", Color.argb(200,200,30,30));
        int bW = 110*dp, bH = 70*dp;
        FrameLayout.LayoutParams fireP = new FrameLayout.LayoutParams(bW, bH);
        fireP.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.RIGHT;
        fireP.rightMargin  = 20 * dp;
        fireP.bottomMargin = 110 * dp;
        hud.addView(btnFire, fireP);
        btnFire.setOnTouchListener((v, ev) -> {
            if (ev.getAction()==MotionEvent.ACTION_DOWN) gameView.shooting=true;
            else if (ev.getAction()==MotionEvent.ACTION_UP||ev.getAction()==MotionEvent.ACTION_CANCEL)
                gameView.shooting=false;
            return true;
        });

        // ── RELOAD button ─────────────────────────────────────────────────────
        Button btnReload = makeBtn("↺ RELOAD", Color.argb(180,200,160,30));
        FrameLayout.LayoutParams reloadP = new FrameLayout.LayoutParams(bW, bH);
        reloadP.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.RIGHT;
        reloadP.rightMargin  = 20 * dp;
        reloadP.bottomMargin = 30 * dp;
        hud.addView(btnReload, reloadP);
        btnReload.setOnClickListener(v -> gameView.reloadPressed = true);

        // ── MED PACK button ───────────────────────────────────────────────────
        Button btnMed = makeBtn("+ MED", Color.argb(180,30,160,60));
        FrameLayout.LayoutParams medP = new FrameLayout.LayoutParams(bW, bH);
        medP.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.RIGHT;
        medP.rightMargin  = 20 * dp;
        medP.bottomMargin = 190 * dp;
        hud.addView(btnMed, medP);
        btnMed.setOnClickListener(v -> gameView.medPressed = true);

        // ── Weapon buttons (top-right) ────────────────────────────────────────
        String[] wNames = {"1 RIFLE","2 SHOTGUN","3 GRENADE"};
        int[] wColors   = {
            Color.argb(160,60,120,60),
            Color.argb(160,120,60,60),
            Color.argb(160,80,60,120)
        };
        int wBW=120*dp, wBH=44*dp;
        for (int i=0;i<3;i++) {
            final int wi=i;
            Button wb = makeBtn(wNames[i], wColors[i]);
            wb.setTextSize(12);
            FrameLayout.LayoutParams wp = new FrameLayout.LayoutParams(wBW, wBH);
            wp.gravity = android.view.Gravity.TOP | android.view.Gravity.RIGHT;
            wp.rightMargin = 10 * dp;
            wp.topMargin   = (10 + i * (wBH/dp + 6)) * dp;
            hud.addView(wb, wp);
            wb.setOnClickListener(v -> gameView.weaponSelect = wi);
        }

        // ── PAUSE button (top-left) ───────────────────────────────────────────
        Button btnPause = makeBtn("⏸ PAUSE", Color.argb(140,60,60,80));
        btnPause.setTextSize(13);
        FrameLayout.LayoutParams pauseP = new FrameLayout.LayoutParams(100*dp, 44*dp);
        pauseP.gravity = android.view.Gravity.TOP | android.view.Gravity.LEFT;
        pauseP.leftMargin = 10*dp;
        pauseP.topMargin  = 10*dp;
        hud.addView(btnPause, pauseP);
        btnPause.setOnClickListener(v -> {
            if (gameView.state == GameView.State.MENU || gameView.state == GameView.State.GAME_OVER)
                gameView.startPressed = true;
            else gameView.pausePressed = true;
        });

        // ── START/RETRY overlay tap on game view ──────────────────────────────
        gameView.setOnClickListener(v -> {
            if (gameView.state == GameView.State.MENU || gameView.state == GameView.State.GAME_OVER)
                gameView.startPressed = true;
        });

        setContentView(root);
    }

    private Button makeBtn(String text, int bgColor) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setTypeface(android.graphics.Typeface.MONOSPACE);
        b.setBackgroundColor(bgColor);
        b.setPadding(4, 4, 4, 4);
        b.setAllCaps(false);
        return b;
    }

    @Override protected void onPause()  { super.onPause(); }
    @Override protected void onResume() { super.onResume(); }
}
