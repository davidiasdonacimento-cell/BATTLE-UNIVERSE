package com.battleuniverse.game;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

public final class MainActivity extends Activity {
    private GameSurfaceView gameSurface;
    private HudOverlay hud;

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersiveMode();
        SharedPreferences prefs=getSharedPreferences("battle_universe_profile",MODE_PRIVATE);
        FrameLayout root=new FrameLayout(this);
        gameSurface=new GameSurfaceView(this);
        hud=new HudOverlay(this,gameSurface,prefs);
        root.addView(gameSurface,new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(hud,new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }
    private void enterImmersiveMode(){
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                |View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                |View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                |View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus){super.onWindowFocusChanged(hasFocus);if(hasFocus)enterImmersiveMode();}
    @Override protected void onPause(){if(gameSurface!=null)gameSurface.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(gameSurface!=null)gameSurface.onResume();enterImmersiveMode();if(hud!=null)hud.invalidate();}
}
