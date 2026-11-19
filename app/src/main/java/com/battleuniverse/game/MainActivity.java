package com.battleuniverse.game;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

public final class MainActivity extends Activity {
    private GameSurfaceView gameSurface;
    private HudOverlay hud;
    private ContentPackInstaller installer;
    private ContentLoadingView loadingView;
    private final Handler mainHandler=new Handler(Looper.getMainLooper());
    private boolean gameStarted=false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersiveMode();
        if(ContentPackInstaller.isInstalled(this)) launchGame(true);
        else startContentSetup();
    }

    private void startContentSetup() {
        gameStarted=false;
        loadingView=new ContentLoadingView(this);
        setContentView(loadingView);
        loadingView.setProgress(0,0,"PREPARANDO INSTALAÇÃO...");
        if(installer!=null)installer.shutdown();
        installer=new ContentPackInstaller(this,new ContentPackInstaller.Listener() {
            @Override public void onProgress(long downloaded,long total,String status) {
                if(loadingView!=null)loadingView.setProgress(downloaded,total,status);
            }
            @Override public void onSuccess() {
                if(loadingView!=null)loadingView.setProgress(1,1,"DADOS ÍNTEGROS • REINICIANDO O JOGO...");
                mainHandler.postDelayed(()->recreate(),950);
            }
            @Override public void onFailure(String message) {
                if(loadingView!=null)loadingView.setFailure(message,
                    ()->startContentSetup(),()->launchGame(false));
            }
        });
        installer.start();
    }

    private void launchGame(boolean contentPackReady) {
        gameStarted=true;
        FrameLayout root=new FrameLayout(this);
        gameSurface=new GameSurfaceView(this);
        if(contentPackReady) {
            int[] settings=ContentPackInstaller.readIslandSettings(this);
            gameSurface.setContentPackData(settings[0],settings[1]);
        }
        hud=new HudOverlay(this,gameSurface,getSharedPreferences("battle_universe_profile",MODE_PRIVATE));
        root.addView(gameSurface,new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(hud,new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            |View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            |View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            |View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus)enterImmersiveMode();
    }
    @Override protected void onPause() {
        if(gameSurface!=null)gameSurface.onPause();
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if(gameSurface!=null)gameSurface.onResume();
        enterImmersiveMode();
        if(hud!=null)hud.invalidate();
    }
    @Override protected void onDestroy() {
        if(installer!=null)installer.shutdown();
        super.onDestroy();
    }
}
