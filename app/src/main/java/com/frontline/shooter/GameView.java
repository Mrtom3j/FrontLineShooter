package com.frontline.shooter;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import java.util.*;

public class GameView extends SurfaceView implements SurfaceHolder.Callback {

    // ── Game States ──────────────────────────────────────────────────────────
    enum State { MENU, PLAYING, PAUSED, GAME_OVER }
    State state = State.MENU;

    // ── World / Camera ───────────────────────────────────────────────────────
    static final int WORLD_W = 2400, WORLD_H = 1800;
    float camX, camY;
    int screenW, screenH;

    // ── Game Objects ─────────────────────────────────────────────────────────
    Player player;
    List<Enemy>     enemies    = new ArrayList<>();
    List<Bullet>    bullets    = new ArrayList<>();
    List<Explosion> explosions = new ArrayList<>();
    List<Decal>     decals     = new ArrayList<>();
    List<Pickup>    pickups    = new ArrayList<>();
    List<Cover>     covers     = new ArrayList<>();
    List<float[]>   muzzleFlashes = new ArrayList<>(); // {wx,wy,life}

    // ── Wave System ──────────────────────────────────────────────────────────
    int wave = 0, score = 0, kills = 0;
    float waveTimer = 0;
    boolean spawning = false;
    Random rand = new Random();

    // ── Terrain ──────────────────────────────────────────────────────────────
    Bitmap terrainBmp;

    // ── Timing ───────────────────────────────────────────────────────────────
    long lastNano;
    int fps, frameCount;
    float fpsTimer;

    // ── Screen Shake ─────────────────────────────────────────────────────────
    float shakeAmt;

    // ── High Score (local storage) ───────────────────────────────────────────
    SharedPreferences prefs;
    int highScore;

    // ── Game Thread ──────────────────────────────────────────────────────────
    GameThread gameThread;

    // ─────────────────────────────────────────────────────────────────────────
    //  Controls state (set by MainActivity overlay)
    // ─────────────────────────────────────────────────────────────────────────
    // Joystick
    public float joystickDx = 0, joystickDy = 0;  // -1..1
    // Aim direction in world (set from right-side touch)
    public float aimAngle = 0;
    public boolean shooting = false;
    public volatile boolean reloadPressed = false;
    public volatile boolean medPressed   = false;
    public volatile int    weaponSelect  = -1; // 0,1,2 or -1
    public volatile boolean pausePressed = false;
    public volatile boolean startPressed = false;

    // ─────────────────────────────────────────────────────────────────────────
    //  Paint cache
    // ─────────────────────────────────────────────────────────────────────────
    Paint pTerrain, pText, pHUD, pHP, pBar, pBullet, pEnemy, pPlayer;

    public GameView(Context ctx, SharedPreferences prefs) {
        super(ctx);
        this.prefs = prefs;
        highScore  = prefs.getInt("highScore", 0);
        getHolder().addCallback(this);
        setFocusable(true);
        initPaints();
    }

    void initPaints() {
        pTerrain = new Paint();
        pText    = new Paint(Paint.ANTI_ALIAS_FLAG);
        pText.setTypeface(Typeface.MONOSPACE);
        pText.setColor(Color.WHITE);
        pHUD     = new Paint(Paint.ANTI_ALIAS_FLAG);
        pHP      = new Paint(Paint.ANTI_ALIAS_FLAG);
        pBar     = new Paint(Paint.ANTI_ALIAS_FLAG);
        pBullet  = new Paint(Paint.ANTI_ALIAS_FLAG);
        pEnemy   = new Paint(Paint.ANTI_ALIAS_FLAG);
        pPlayer  = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Surface callbacks
    // ═════════════════════════════════════════════════════════════════════════
    @Override public void surfaceCreated(SurfaceHolder h) {
        screenW = getWidth();  screenH = getHeight();
        buildTerrain();
        buildCovers();
        gameThread = new GameThread(this, h);
        gameThread.running = true;
        gameThread.start();
        lastNano = System.nanoTime();
    }

    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {
        screenW = w; screenH = hh;
    }

    @Override public void surfaceDestroyed(SurfaceHolder h) {
        if (gameThread != null) {
            gameThread.running = false;
            try { gameThread.join(1000); } catch (InterruptedException ignored) {}
        }
        if (terrainBmp != null && !terrainBmp.isRecycled()) terrainBmp.recycle();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Terrain & Covers
    // ═════════════════════════════════════════════════════════════════════════
    void buildTerrain() {
        // Build a smaller scaled terrain (1/3 scale) to save memory
        int tw = WORLD_W/3, th = WORLD_H/3;
        Bitmap src = Bitmap.createBitmap(tw, th, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(src);
        Paint p = new Paint();

        // Base dirt
        p.setColor(Color.rgb(62,52,38)); c.drawRect(0,0,tw,th,p);

        // Dirt patches
        p.setAntiAlias(true);
        for (int i=0;i<400;i++) {
            int px=rand.nextInt(tw), py=rand.nextInt(th);
            int pw=10+rand.nextInt(40), ph=7+rand.nextInt(27);
            float bright=0.7f+rand.nextFloat()*0.4f;
            p.setColor(Color.rgb((int)(62*bright),(int)(52*bright),(int)(38*bright)));
            c.drawOval(px,py,px+pw,py+ph,p);
        }

        // Gravel paths
        p.setColor(Color.rgb(80,70,55));
        for (int y=117;y<th;y+=233) c.drawRect(0,y,tw,y+40,p);
        for (int x=117;x<tw;x+=233) c.drawRect(x,0,x+40,th,p);

        // Craters
        p.setColor(Color.rgb(40,33,24));
        for (int i=0;i<15;i++) {
            int cx=rand.nextInt(tw), cy=rand.nextInt(th), cr=13+rand.nextInt(27);
            c.drawOval(cx-cr,cy-cr/2,cx+cr,cy+cr/2,p);
        }

        // Border
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(10);
        p.setColor(Color.rgb(90,80,60));
        c.drawRect(10,10,tw-10,th-10,p);
        p.setStyle(Paint.Style.FILL);

        terrainBmp = Bitmap.createScaledBitmap(src, WORLD_W, WORLD_H, false);
        src.recycle();
    }

    void buildCovers() {
        covers.clear();
        int[][] walls = {
            {300,300,120,20},{700,200,20,100},{1100,500,150,20},
            {500,700,20,120},{900,800,120,20},{1500,300,20,150},
            {200,900,150,20},{1800,600,20,120},{1200,1200,200,20},
            {600,1300,20,150},{1600,1000,150,20},{400,1500,20,120},
            {1000,400,120,20},{1400,1400,20,150},{800,1600,150,20},
            {1800,1400,120,20},{300,1100,20,150},{1100,100,150,20},
            {2000,300,20,200},{100,500,200,20}
        };
        for (int[] w:walls) covers.add(new Cover(w[0],w[1],w[2],w[3]));
        int[][] barrels = {
            {500,400},{900,600},{1300,400},{600,900},
            {1100,1100},{1700,800},{400,1300},{1900,1200}
        };
        for (int[] b:barrels)
            for (int i=0;i<3;i++) covers.add(new Cover(b[0]+i*30,b[1],22,22));
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Game Loop
    // ═════════════════════════════════════════════════════════════════════════
    void tick() {
        long now = System.nanoTime();
        float dt = Math.min((now - lastNano)/1e9f, 0.05f);
        lastNano = now;

        frameCount++; fpsTimer += dt;
        if (fpsTimer >= 1f) { fps = frameCount; frameCount = 0; fpsTimer = 0; }

        // Consume one-shot button presses
        if (startPressed) {
            startPressed = false;
            if (state == State.MENU || state == State.GAME_OVER) startGame();
        }
        if (pausePressed) {
            pausePressed = false;
            if (state == State.PLAYING) state = State.PAUSED;
            else if (state == State.PAUSED) state = State.PLAYING;
        }

        if (state == State.PLAYING) update(dt);
    }

    void update(float dt) {
        shakeAmt = Math.max(0, shakeAmt - dt*8);

        // Movement from joystick
        float dx = joystickDx, dy = joystickDy;
        float nx = player.x + dx * player.speed * dt;
        float ny = player.y + dy * player.speed * dt;
        nx = Math.max(60, Math.min(WORLD_W-60, nx));
        ny = Math.max(60, Math.min(WORLD_H-60, ny));
        if (!collidesWithCovers(nx, player.y, 18)) player.x = nx;
        if (!collidesWithCovers(player.x, ny, 18)) player.y = ny;

        player.angle = aimAngle;

        // Camera
        camX = player.x - screenW/2f;
        camY = player.y - screenH/2f;
        camX = Math.max(0, Math.min(WORLD_W - screenW, camX));
        camY = Math.max(0, Math.min(WORLD_H - screenH, camY));

        // Weapon switch
        if (weaponSelect >= 0 && weaponSelect < 3) {
            player.weaponIdx = weaponSelect;
            player.ammo      = player.weapons[weaponSelect].maxAmmo;
            player.maxAmmo   = player.weapons[weaponSelect].maxAmmo;
            player.reloading = false;
            weaponSelect = -1;
        }

        // Reload
        if (reloadPressed) { reloadPressed = false; startReload(); }

        // Med pack
        if (medPressed) {
            medPressed = false;
            if (player.medPacks > 0) {
                player.hp = Math.min(player.maxHp, player.hp+40);
                player.medPacks--;
            }
        }

        // Shooting
        player.shootTimer  -= dt;
        player.reloadTimer -= dt;
        if (player.reloadTimer <= 0 && player.reloading) {
            player.reloading = false;
            player.ammo = player.maxAmmo;
        }
        if (shooting && !player.reloading && player.shootTimer <= 0 && player.ammo > 0) {
            playerShoot();
        }

        // Bullets
        Iterator<Bullet> bi = bullets.iterator();
        while (bi.hasNext()) {
            Bullet b = bi.next(); b.update(dt);
            if (b.dead || b.x<0||b.x>WORLD_W||b.y<0||b.y>WORLD_H) { bi.remove(); continue; }
            if (collidesWithCovers(b.x,b.y,4)) {
                decals.add(new Decal(b.x,b.y,2,Color.argb(120,180,160,120)));
                bi.remove(); continue;
            }
            if (b.fromPlayer) {
                for (Enemy en : enemies) {
                    if (!en.dead && dist(b.x,b.y,en.x,en.y)<en.size+6) {
                        en.hit(b.damage);
                        if (en.dead) { score+=en.scoreVal; kills++; spawnDrops(en); }
                        decals.add(new Decal(b.x,b.y,5,Color.argb(160,180,20,20)));
                        b.dead=true; break;
                    }
                }
            } else {
                if (dist(b.x,b.y,player.x,player.y)<18) {
                    player.hp -= b.damage; shakeAmt=4;
                    decals.add(new Decal(b.x,b.y,4,Color.argb(140,200,30,30)));
                    b.dead=true;
                    if (player.hp<=0) { endGame(); }
                }
            }
            if (!b.dead && bi.hasNext() == false) {} // keep
        }

        // Enemies
        enemies.removeIf(en -> en.dead && en.deathTimer<=0);
        for (Enemy en : enemies) en.update(dt, player, bullets, covers);

        // Explosions
        explosions.removeIf(ex -> ex.life<=0);
        for (Explosion ex : explosions) ex.update(dt);

        muzzleFlashes.removeIf(f -> f[2]<=0);
        for (float[] f : muzzleFlashes) f[2] -= dt*10;

        if (decals.size()>600) decals.subList(0,200).clear();

        // Pickups
        Iterator<Pickup> pi = pickups.iterator();
        while (pi.hasNext()) {
            Pickup pk=pi.next(); pk.life-=dt;
            if (pk.life<=0) { pi.remove(); continue; }
            if (dist(pk.x,pk.y,player.x,player.y)<30) { pk.apply(player); pi.remove(); }
        }

        // Wave management
        boolean allDead = enemies.stream().allMatch(en->en.dead);
        if (allDead && !spawning) {
            waveTimer-=dt;
            if (waveTimer<=0) startWave();
        }
    }

    void endGame() {
        state = State.GAME_OVER;
        if (score > highScore) {
            highScore = score;
            prefs.edit().putInt("highScore", highScore).apply();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Shooting
    // ═════════════════════════════════════════════════════════════════════════
    void playerShoot() {
        Weapon w = player.weapons[player.weaponIdx];
        if (player.ammo<=0) { startReload(); return; }
        player.ammo--; player.shootTimer=w.fireRate;

        float ax = player.x+(float)Math.cos(aimAngle)*22;
        float ay = player.y+(float)Math.sin(aimAngle)*22;
        muzzleFlashes.add(new float[]{ax, ay, 1f});

        if (w.type == Weapon.Type.RIFLE) {
            float spread = 0.04f;
            float a = aimAngle + (rand.nextFloat()-0.5f)*spread;
            bullets.add(new Bullet(player.x,player.y,a,900,w.damage,true));
        } else if (w.type == Weapon.Type.SHOTGUN) {
            shakeAmt=2;
            for (int i=0;i<8;i++) {
                float a = aimAngle+(rand.nextFloat()-0.5f)*0.45f;
                bullets.add(new Bullet(player.x,player.y,a,700,w.damage,true));
            }
        } else if (w.type == Weapon.Type.GRENADE) {
            Bullet g = new Bullet(player.x,player.y,aimAngle,400,0,true);
            g.isGrenade=true; g.fuseTimer=1.5f;
            bullets.add(g);
        }
        if (player.ammo==0) startReload();
    }

    void startReload() {
        if (!player.reloading) {
            player.reloading=true;
            player.reloadTimer=player.weapons[player.weaponIdx].reloadTime;
        }
    }

    void explodeGrenade(float x, float y) {
        shakeAmt=10;
        explosions.add(new Explosion(x,y,120));
        decals.add(new Decal(x,y,80,Color.argb(180,40,30,20)));
        for (Enemy en:enemies) {
            float d=dist(x,y,en.x,en.y);
            if (d<150) { en.hit((int)(80*(1-d/150))); if(en.dead){score+=en.scoreVal;kills++;spawnDrops(en);} }
        }
        if (dist(x,y,player.x,player.y)<150) { player.hp-=25; if(player.hp<=0)endGame(); }
        for (int i=0;i<12;i++) {
            float a=(float)(rand.nextFloat()*Math.PI*2);
            bullets.add(new Bullet(x,y,a,600,15,true));
        }
    }

    // ─── Waves ───────────────────────────────────────────────────────────────
    void startWave() {
        wave++; spawning=true;
        int count=5+wave*3;
        List<float[]> spts=getSpawnPoints();
        for (int i=0;i<count;i++) {
            float[] sp=spts.get(rand.nextInt(spts.size()));
            Enemy.Type type;
            if (wave%3==0&&i==0) type=Enemy.Type.BOSS;
            else if (wave>=3&&rand.nextFloat()<0.25f) type=Enemy.Type.HEAVY;
            else if (rand.nextFloat()<0.3f) type=Enemy.Type.FAST;
            else type=Enemy.Type.GRUNT;
            enemies.add(new Enemy(sp[0],sp[1],type));
        }
        spawning=false; waveTimer=3f;
    }

    List<float[]> getSpawnPoints() {
        List<float[]> pts=new ArrayList<>();
        int m=80;
        for (int i=0;i<20;i++) {
            pts.add(new float[]{m+rand.nextFloat()*(WORLD_W-m*2),(float)m});
            pts.add(new float[]{m+rand.nextFloat()*(WORLD_W-m*2),(float)(WORLD_H-m)});
            pts.add(new float[]{(float)m, m+rand.nextFloat()*(WORLD_H-m*2)});
            pts.add(new float[]{(float)(WORLD_W-m), m+rand.nextFloat()*(WORLD_H-m*2)});
        }
        pts.removeIf(p->dist(p[0],p[1],player.x,player.y)<300);
        if (pts.isEmpty()) pts.add(new float[]{100,100});
        return pts;
    }

    void spawnDrops(Enemy en) {
        if (rand.nextFloat()<0.3f)
            pickups.add(new Pickup(en.x+rand.nextInt(30)-15, en.y+rand.nextInt(30)-15,
                rand.nextFloat()<0.5f?Pickup.Type.AMMO:Pickup.Type.HEALTH));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
    boolean collidesWithCovers(float x, float y, float r) {
        for (Cover c:covers) if(x+r>c.x&&x-r<c.x+c.w&&y+r>c.y&&y-r<c.y+c.h) return true;
        return false;
    }

    float dist(float x1,float y1,float x2,float y2) {
        float dx=x2-x1,dy=y2-y1; return (float)Math.sqrt(dx*dx+dy*dy);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Rendering
    // ═════════════════════════════════════════════════════════════════════════
    void render(Canvas canvas) {
        if (screenW==0||screenH==0) return;

        if (state==State.MENU)     { drawMenu(canvas); return; }
        if (state==State.GAME_OVER){ drawGameOver(canvas); return; }

        // Clear
        canvas.drawColor(Color.BLACK);

        canvas.save();
        // Screen shake
        if (shakeAmt>0.1f) {
            canvas.translate((rand.nextFloat()-0.5f)*shakeAmt*2, (rand.nextFloat()-0.5f)*shakeAmt*2);
        }
        // Camera
        canvas.translate(-camX,-camY);

        // Terrain
        if (terrainBmp!=null) canvas.drawBitmap(terrainBmp,0,0,pTerrain);

        // Decals
        Paint dp=new Paint(Paint.ANTI_ALIAS_FLAG);
        for (Decal d:decals) { dp.setColor(d.color); canvas.drawCircle(d.x,d.y,d.size/2f,dp); }

        // Covers
        for (Cover c:covers) c.draw(canvas);

        // Pickups
        for (Pickup pk:pickups) pk.draw(canvas);

        // Bullets
        for (Bullet b:bullets) b.draw(canvas);

        // Explosions
        for (Explosion ex:explosions) ex.draw(canvas);

        // Muzzle flashes
        Paint mp=new Paint(Paint.ANTI_ALIAS_FLAG);
        for (float[] f:muzzleFlashes) {
            int alpha=(int)(f[2]*255);
            float sz=f[2]*30;
            mp.setColor(Color.argb(Math.min(255,alpha),255,220,80));
            canvas.drawCircle(f[0],f[1],sz/2,mp);
        }

        // Enemies
        for (Enemy en:enemies) en.draw(canvas);

        // Player
        player.draw(canvas);

        canvas.restore();

        // Vignette
        drawVignette(canvas);

        // HUD
        drawHUD(canvas);

        if (state==State.PAUSED) drawPaused(canvas);
    }

    void drawVignette(Canvas c) {
        RadialGradient g=new RadialGradient(screenW/2f,screenH/2f,Math.max(screenW,screenH)*0.75f,
            new int[]{Color.TRANSPARENT,Color.argb(160,0,0,0)},
            new float[]{0.4f,1.0f}, Shader.TileMode.CLAMP);
        Paint p=new Paint(); p.setShader(g);
        c.drawRect(0,0,screenW,screenH,p);
    }

    void drawHUD(Canvas c) {
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.MONOSPACE);

        int bw=220,bh=18,bx=20,by=screenH-70;

        // HP bar bg
        p.setColor(Color.argb(200,20,5,5));
        c.drawRoundRect(bx-2,by-2,bx+bw+2,by+bh+2,6,6,p);
        // HP fill
        float hpPct=Math.max(0,player.hp/(float)player.maxHp);
        p.setColor(hpPct>0.5f?Color.rgb(50,220,80):hpPct>0.25f?Color.rgb(240,180,30):Color.rgb(220,40,40));
        c.drawRoundRect(bx,by,bx+(int)(bw*hpPct),by+bh,4,4,p);
        p.setColor(Color.WHITE); p.setTextSize(20);
        c.drawText("HP "+Math.max(0,player.hp)+"/"+player.maxHp, bx+6, by+bh-2, p);

        // Ammo
        Weapon w=player.weapons[player.weaponIdx];
        p.setTextSize(30);
        p.setColor(player.reloading?Color.rgb(240,180,30):Color.WHITE);
        String ammoStr=player.reloading?"RELOADING...":player.ammo+" / "+player.maxAmmo;
        c.drawText(ammoStr, screenW-350f, screenH-30f, p);
        p.setTextSize(20); p.setColor(Color.rgb(180,220,180));
        c.drawText("["+((char)('1'+player.weaponIdx))+"] "+w.name, screenW-350f, screenH-60f, p);

        // Score/Wave/Kills
        p.setTextSize(24); p.setColor(Color.rgb(220,220,180));
        c.drawText("WAVE "+wave, screenW/2f-50, 36, p);
        p.setTextSize(20); p.setColor(Color.rgb(180,200,180));
        c.drawText("SCORE: "+score, 20, 36, p);
        c.drawText("KILLS: "+kills, 20, 60, p);
        c.drawText("BEST:  "+highScore, 20, 84, p);

        // Med packs
        if (player.medPacks>0) {
            p.setColor(Color.rgb(60,200,80)); p.setTextSize(20);
            c.drawText("MED x"+player.medPacks, bx, by-14, p);
        }

        // Enemies remaining
        long alive=enemies.stream().filter(en->!en.dead).count();
        p.setTextSize(20);
        if (alive>0) {
            p.setColor(Color.rgb(220,80,80));
            c.drawText("ENEMIES: "+alive, screenW/2f-60, 60, p);
        } else if (wave>0) {
            p.setColor(Color.rgb(80,255,120));
            c.drawText("NEXT WAVE IN "+(int)Math.ceil(waveTimer)+"s", screenW/2f-90, 60, p);
        }

        // Reload bar
        if (player.reloading) {
            float prog=1f-player.reloadTimer/player.weapons[player.weaponIdx].reloadTime;
            int rbw=200, rbx=screenW/2-rbw/2;
            p.setColor(Color.argb(200,30,30,30));
            c.drawRoundRect(rbx,screenH-110f,rbx+rbw,screenH-96f,5,5,p);
            p.setColor(Color.rgb(255,200,50));
            c.drawRoundRect(rbx,screenH-110f,rbx+rbw*prog,screenH-96f,5,5,p);
        }
    }

    void drawMenu(Canvas c) {
        c.drawColor(Color.rgb(15,18,12));
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.MONOSPACE);

        // Grid
        p.setColor(Color.argb(80,30,40,25));
        for (int x=0;x<screenW;x+=40) c.drawLine(x,0,x,screenH,p);
        for (int y=0;y<screenH;y+=40) c.drawLine(0,y,screenW,y,p);

        // Title
        p.setTextSize(80); p.setColor(Color.rgb(180,30,30));
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText("FRONTLINE", screenW/2f+3, 180+3, p);
        p.setColor(Color.rgb(220,40,40));
        c.drawText("FRONTLINE", screenW/2f, 180, p);
        p.setTextSize(22); p.setColor(Color.rgb(140,180,140));
        c.drawText("COMBAT ZONE", screenW/2f, 220, p);

        // Divider
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(Color.argb(120,180,30,30));
        c.drawLine(screenW/2f-200,235,screenW/2f+200,235,p);
        p.setStyle(Paint.Style.FILL);

        // Info box
        p.setColor(Color.argb(200,20,28,18));
        c.drawRoundRect(screenW/2f-260,250,screenW/2f+260,480,12,12,p);
        p.setColor(Color.argb(100,60,90,50)); p.setStyle(Paint.Style.STROKE);
        c.drawRoundRect(screenW/2f-260,250,screenW/2f+260,480,12,12,p);
        p.setStyle(Paint.Style.FILL);

        p.setTextSize(20); p.setColor(Color.rgb(200,220,180));
        String[] lines={"JOYSTICK  ─  Move","RIGHT PAD  ─  Aim & Shoot","FIRE BTN   ─  Shoot","R          ─  Reload","MED        ─  Use Med Pack","1/2/3      ─  Switch Weapon"};
        for (int i=0;i<lines.length;i++) c.drawText(lines[i],screenW/2f,278+i*36,p);

        // Best score
        p.setTextSize(20); p.setColor(Color.rgb(255,200,80));
        c.drawText("BEST SCORE: "+highScore, screenW/2f, 495, p);

        // Start button
        float t=(System.currentTimeMillis()%800)/800f;
        float pulse=0.6f+0.4f*(float)Math.sin(t*Math.PI*2);
        p.setColor(Color.argb((int)(200*pulse),180,30,30));
        c.drawRoundRect(screenW/2f-130,510,screenW/2f+130,570,12,12,p);
        p.setColor(Color.rgb(255,80,80)); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2);
        c.drawRoundRect(screenW/2f-130,510,screenW/2f+130,570,12,12,p);
        p.setStyle(Paint.Style.FILL); p.setTextSize(26); p.setColor(Color.WHITE);
        c.drawText("TAP TO START", screenW/2f, 548, p);
    }

    void drawGameOver(Canvas c) {
        c.drawColor(Color.rgb(10,5,5));
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.MONOSPACE);
        p.setTextAlign(Paint.Align.CENTER);

        p.setTextSize(56); p.setColor(Color.rgb(80,5,5));
        c.drawText("MISSION FAILED", screenW/2f+3, screenH/2f-120+3, p);
        p.setColor(Color.rgb(220,30,30));
        c.drawText("MISSION FAILED", screenW/2f, screenH/2f-120, p);

        p.setTextSize(24); p.setColor(Color.rgb(200,180,150));
        c.drawText("WAVE REACHED:  "+wave, screenW/2f, screenH/2f-50, p);
        c.drawText("FINAL SCORE:   "+score, screenW/2f, screenH/2f-18, p);
        c.drawText("KILLS:         "+kills, screenW/2f, screenH/2f+14, p);
        p.setColor(Color.rgb(255,200,80));
        c.drawText("BEST SCORE:    "+highScore, screenW/2f, screenH/2f+50, p);

        float pulse=0.5f+0.5f*(float)Math.sin(System.currentTimeMillis()/500.0);
        p.setColor(Color.argb((int)(255*pulse),255,150,150));
        p.setTextSize(22);
        c.drawText("TAP TO RETRY", screenW/2f, screenH/2f+110, p);
    }

    void drawPaused(Canvas c) {
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.argb(160,0,0,0));
        c.drawRect(0,0,screenW,screenH,p);
        p.setTypeface(Typeface.MONOSPACE); p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(50); p.setColor(Color.WHITE);
        c.drawText("PAUSED", screenW/2f, screenH/2f, p);
        p.setTextSize(20); p.setColor(Color.rgb(180,200,180));
        c.drawText("Tap PAUSE to resume", screenW/2f, screenH/2f+50, p);
    }

    // ─── Start / Reset ────────────────────────────────────────────────────────
    void startGame() {
        enemies.clear(); bullets.clear(); explosions.clear();
        decals.clear(); pickups.clear(); muzzleFlashes.clear();
        wave=0; score=0; kills=0; waveTimer=0;
        player=new Player(WORLD_W/2f, WORLD_H/2f);
        state=State.PLAYING;
        camX=player.x-screenW/2f; camY=player.y-screenH/2f;
        buildCovers(); startWave();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Inner classes
    // ═════════════════════════════════════════════════════════════════════════

    // ─── Player ──────────────────────────────────────────────────────────────
    class Player {
        float x,y,angle;
        int hp=120,maxHp=120,medPacks=2;
        float speed=200;
        int weaponIdx=0,ammo,maxAmmo;
        boolean reloading;
        float shootTimer,reloadTimer;
        Weapon[] weapons={
            new Weapon("ASSAULT RIFLE",Weapon.Type.RIFLE,30,30,0.12f,1.5f,28),
            new Weapon("SHOTGUN",Weapon.Type.SHOTGUN,6,6,0.7f,2.5f,18),
            new Weapon("GRENADE",Weapon.Type.GRENADE,4,4,1.2f,3.0f,0)
        };
        Player(float x,float y){this.x=x;this.y=y;ammo=weapons[0].maxAmmo;maxAmmo=weapons[0].maxAmmo;}

        void draw(Canvas c) {
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            c.save(); c.translate(x,y); c.rotate((float)Math.toDegrees(angle));
            // Shadow
            p.setColor(Color.argb(60,0,0,0));
            c.drawOval(-14,-10,14,10,p);
            // Body
            p.setColor(Color.rgb(50,80,50));
            c.drawCircle(0,0,12,p);
            p.setColor(Color.rgb(70,100,70));
            c.drawCircle(-2,-2,9,p);
            // Vest
            p.setColor(Color.rgb(60,55,40));
            c.drawRect(-7,-5,7,5,p);
            // Gun
            p.setColor(Color.rgb(40,40,40));
            c.drawRoundRect(4,-3,26,3,3,3,p);
            c.restore();
        }
    }

    // ─── Enemy ───────────────────────────────────────────────────────────────
    class Enemy {
        enum Type{GRUNT,FAST,HEAVY,BOSS}
        Type type; float x,y,angle;
        int hp,maxHp,damage,scoreVal,size;
        float speed,shootTimer,shootRate;
        boolean dead; float deathTimer=0.8f;
        int bodyColor,armorColor;

        Enemy(float x,float y,Type t){
            this.x=x;this.y=y;type=t;
            switch(t){
                case GRUNT:hp=50;maxHp=50;speed=100;shootRate=1.5f;damage=12;scoreVal=10;size=14;bodyColor=Color.rgb(140,30,30);armorColor=Color.rgb(80,20,20);break;
                case FAST: hp=30;maxHp=30;speed=180;shootRate=2.0f;damage=8; scoreVal=15;size=11;bodyColor=Color.rgb(180,80,20);armorColor=Color.rgb(120,50,10);break;
                case HEAVY:hp=150;maxHp=150;speed=60;shootRate=1.0f;damage=20;scoreVal=25;size=18;bodyColor=Color.rgb(80,80,180);armorColor=Color.rgb(50,50,120);break;
                case BOSS: hp=500;maxHp=500;speed=80;shootRate=0.5f;damage=30;scoreVal=100;size=28;bodyColor=Color.rgb(120,20,120);armorColor=Color.rgb(80,10,80);break;
            }
            shootTimer=rand.nextFloat();
        }

        void hit(int dmg){
            hp-=dmg;
            if(hp<=0&&!dead){
                dead=true;
                decals.add(new Decal(x,y,size+8,Color.argb(180,150,10,10)));
                explosions.add(new Explosion(x,y,type==Type.BOSS?80:30));
            }
        }

        void update(float dt,Player pl,List<Bullet> bullets,List<Cover> covers){
            if(dead){deathTimer-=dt;return;}
            float adx=pl.x-x,ady=pl.y-y;
            float d=dist(x,y,pl.x,pl.y);
            angle=(float)Math.atan2(ady,adx);
            if(d>180){
                float mvx=(adx/d)*speed*dt, mvy=(ady/d)*speed*dt;
                float nx2=x+mvx,ny2=y+mvy;
                if(!collidesWithCovers(nx2,y,size-2))x=nx2;
                if(!collidesWithCovers(x,ny2,size-2))y=ny2;
            }
            shootTimer-=dt;
            if(shootTimer<=0&&d<600){
                shootTimer=shootRate+rand.nextFloat()*0.5f;
                float spread=type==Type.BOSS?0.1f:0.25f;
                float a=angle+(rand.nextFloat()-0.5f)*spread;
                bullets.add(new Bullet(x,y,a,500,damage,false));
                if(type==Type.BOSS){
                    bullets.add(new Bullet(x,y,a+0.2f,500,damage,false));
                    bullets.add(new Bullet(x,y,a-0.2f,500,damage,false));
                }
            }
        }

        void draw(Canvas c){
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            c.save(); c.translate(x,y);
            if(dead){
                p.setColor(Color.argb((int)(deathTimer/0.8f*180),100,10,10));
                c.drawOval(-size,-size/2f,size,size/2f,p);
                c.restore();return;
            }
            c.rotate((float)Math.toDegrees(angle));
            p.setColor(Color.argb(70,0,0,0));
            c.drawOval(-size-2,-size/2f,size+2,size/2f,p);
            p.setColor(bodyColor); c.drawCircle(0,0,size,p);
            p.setColor(armorColor); c.drawCircle(0,0,size-3,p);
            // Gun
            p.setColor(Color.rgb(30,30,30));
            c.drawRoundRect(size-2,-3,size*2+2,3,2,2,p);
            // HP bar
            if(hp<maxHp){
                int bw=size*2+6; float bx2=-bw/2f,by2=-size-12;
                p.setColor(Color.rgb(40,40,40));
                c.drawRect(bx2,by2,bx2+bw,by2+5,p);
                float pct=(float)hp/maxHp;
                p.setColor(pct>0.5f?Color.rgb(50,200,60):pct>0.25f?Color.rgb(220,180,30):Color.rgb(220,40,40));
                c.drawRect(bx2,by2,bx2+(int)(bw*pct),by2+5,p);
                // Boss ring
                if(type==Type.BOSS){
                    p.setColor(Color.argb(180,255,200,0));
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2);
                    c.drawCircle(0,0,size+4,p);
                    p.setStyle(Paint.Style.FILL);
                }
            }
            c.restore();
        }
    }

    // ─── Bullet ──────────────────────────────────────────────────────────────
    class Bullet {
        float x,y,vx,vy,life=3f,fuseTimer;
        int damage; boolean fromPlayer,dead,isGrenade;
        int color;
        Bullet(float x,float y,float angle,float speed,int damage,boolean fromPlayer){
            this.x=x;this.y=y;vx=(float)Math.cos(angle)*speed;vy=(float)Math.sin(angle)*speed;
            this.damage=damage;this.fromPlayer=fromPlayer;
            color=fromPlayer?Color.rgb(255,230,80):Color.rgb(255,80,80);
        }
        void update(float dt){
            x+=vx*dt;y+=vy*dt;life-=dt;if(life<=0)dead=true;
            if(isGrenade){fuseTimer-=dt;if(fuseTimer<=0){explodeGrenade(x,y);dead=true;}vx*=0.98f;vy+=60*dt;}
        }
        void draw(Canvas c){
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            if(isGrenade){
                p.setColor(Color.rgb(60,80,40)); c.drawCircle(x,y,5,p);
                p.setColor(Color.argb((int)(255*(fuseTimer%0.3f)/0.3f),255,180,0));
                c.drawCircle(x,y-8,3,p);
            } else {
                p.setColor(color); p.setStrokeWidth(fromPlayer?2.5f:2f); p.setStyle(Paint.Style.STROKE);
                float d=(float)Math.sqrt(vx*vx+vy*vy); float dx2=vx/d,dy2=vy/d; int len=fromPlayer?14:10;
                c.drawLine(x,y,x-dx2*len,y-dy2*len,p);
                p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(60,Color.red(color),Color.green(color),Color.blue(color)));
                c.drawCircle(x,y,4,p);
            }
        }
    }

    // ─── Explosion ───────────────────────────────────────────────────────────
    static class Explosion {
        float x,y,life,maxLife,radius;
        Explosion(float x,float y,float r){this.x=x;this.y=y;radius=r;life=maxLife=0.5f;}
        void update(float dt){life-=dt;}
        void draw(Canvas c){
            float t=life/maxLife; int r=(int)(radius*(1.5f-t)); int alpha=(int)(t*255);
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.argb(Math.min(255,alpha),255,230,80));
            c.drawCircle(x,y,r/2f,p);
            p.setColor(Color.argb(Math.min(255,alpha/2),255,120,20));
            c.drawCircle(x,y,r,p);
            p.setColor(Color.argb(Math.min(255,(int)(alpha*0.4f)),60,55,50));
            c.drawCircle(x,y,r+5,p);
        }
    }

    // ─── Decal ───────────────────────────────────────────────────────────────
    static class Decal {
        float x,y; int size,color;
        Decal(float x,float y,int s,int c){this.x=x;this.y=y;size=s;color=c;}
    }

    // ─── Cover ───────────────────────────────────────────────────────────────
    static class Cover {
        float x,y,w,h;
        Cover(float x,float y,float w,float h){this.x=x;this.y=y;this.w=w;this.h=h;}
        void draw(Canvas c){
            boolean barrel=(w<=30&&h<=30);
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            if(barrel){
                p.setColor(Color.rgb(100,80,40)); c.drawOval(x,y,x+w,y+h,p);
                p.setColor(Color.rgb(140,110,60)); c.drawOval(x+3,y+3,x+w-3,y+h-3,p);
                p.setColor(Color.rgb(60,50,30)); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f);
                c.drawOval(x,y,x+w,y+h,p); p.setStyle(Paint.Style.FILL);
            } else {
                p.setColor(Color.rgb(100,90,60)); c.drawRoundRect(x,y,x+w,y+h,6,6,p);
                p.setColor(Color.rgb(120,110,75));
                if(w>h){float seg=h; for(float i=x;i<x+w;i+=seg+2) c.drawRoundRect(i,y+2,i+seg,y+h-2,4,4,p);}
                else{float seg=w/3f; for(float i=y;i<y+h;i+=seg+2) c.drawRoundRect(x+2,i,x+w-2,i+seg,4,4,p);}
                p.setColor(Color.rgb(70,60,40)); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f);
                c.drawRoundRect(x,y,x+w,y+h,6,6,p); p.setStyle(Paint.Style.FILL);
                p.setColor(Color.argb(60,0,0,0)); c.drawRoundRect(x+2,y+h-4,x+w-2,y+h+2,3,3,p);
            }
        }
    }

    // ─── Pickup ──────────────────────────────────────────────────────────────
    class Pickup {
        enum Type{HEALTH,AMMO}
        float x,y,life=15f; Type type;
        Pickup(float x,float y,Type t){this.x=x;this.y=y;this.type=t;}
        void apply(Player pl){
            if(type==Type.HEALTH){pl.hp=Math.min(pl.maxHp,pl.hp+30);pl.medPacks++;}
            else{pl.ammo=Math.min(pl.weapons[pl.weaponIdx].maxAmmo,pl.ammo+pl.weapons[pl.weaponIdx].maxAmmo/2);}
        }
        void draw(Canvas c){
            float pulse=0.8f+0.2f*(float)Math.sin(System.currentTimeMillis()/200.0);
            int sz=(int)(20*pulse);
            int col=type==Type.HEALTH?Color.rgb(60,220,80):Color.rgb(60,140,255);
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.argb(60,Color.red(col),Color.green(col),Color.blue(col)));
            c.drawCircle(x,y,sz,p);
            p.setColor(col); c.drawCircle(x,y,10,p);
            p.setColor(Color.WHITE); p.setTextSize(16); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.MONOSPACE);
            c.drawText(type==Type.HEALTH?"+":"A",x,y+6,p);
            p.setColor(Color.argb(120,Color.red(col),Color.green(col),Color.blue(col)));
            c.drawRect(x-12,y+13,x-12+24*(life/15f),y+16,p);
        }
    }

    // ─── Weapon ──────────────────────────────────────────────────────────────
    static class Weapon {
        enum Type{RIFLE,SHOTGUN,GRENADE}
        String name; Type type; int ammo,maxAmmo,damage;
        float fireRate,reloadTime;
        Weapon(String n,Type t,int a,int ma,float fr,float rt,int d){
            name=n;type=t;ammo=a;maxAmmo=ma;fireRate=fr;reloadTime=rt;damage=d;
        }
    }
}
