package com.battleuniverse.game;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

public final class MainActivity extends Activity {
    private GameSurfaceView gameSurface;
    private HudOverlay hud;
    private ContentPackInstaller installer;
    private ContentLoadingView loadingView;
    private AccountSyncManager accountSync;
    private SharedPreferences profilePrefs;
    private EntryGateView entryGate;
    private final Handler mainHandler=new Handler(Looper.getMainLooper());
    private boolean gameLaunched;
    private boolean contentInstalling;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersiveMode();
        profilePrefs=getSharedPreferences("battle_universe_profile",MODE_PRIVATE);
        entryGate=new EntryGateView(this);
        accountSync=new AccountSyncManager(this,profilePrefs,new AccountSyncManager.Listener() {
            @Override public void onAccountState(boolean configured,boolean signedIn,String username,String message) {
                if(entryGate!=null)entryGate.setAccountState(configured,signedIn,username,message);
                if(hud!=null)hud.setGoogleAccountStatus(configured,signedIn,username,message);
                if(!signedIn&&gameLaunched)showEntryGate();
            }
            @Override public void onDeviceIdentity(String code) {
                if(entryGate!=null)entryGate.setDeviceCode(code);
                if(hud!=null)hud.setDeviceIdentity(code);
            }
            @Override public void onRecoveryCode(String code) {
                if(hud!=null)hud.showRecoveryCode(code); else if(entryGate!=null)entryGate.showRecoveryCode(code);
            }
        });
        entryGate.setActions(new EntryGateView.Actions() {
            @Override public void authenticate(String username,String password,boolean create) {
                accountSync.authenticateUsername(username,password,create);
            }
            @Override public void recover(String username,String code) {
                accountSync.recoverAccount(username,code);
            }
            @Override public void rotateRecoveryCode() {
                accountSync.rotateRecoveryCode();
            }
            @Override public void play() {
                if(accountSync.isSignedIn()) {
                    if(ContentPackInstaller.isInstalled(MainActivity.this))launchGame(true);
                    else startContentSetup();
                } else if(entryGate!=null) {
                    entryGate.setAccountState(accountSync.isConfigured(),false,"","Entre na sua conta antes de jogar.");
                }
            }
            @Override public void signOut() {
                accountSync.signOut();
            }
        });
        setContentView(entryGate);
        entryGate.setAccountState(accountSync.isConfigured(),accountSync.isSignedIn(),
                accountSync.getUsername(),accountSync.isConfigured()
                        ?"Entre ou crie sua conta para continuar."
                        :"Servidor de contas indisponível neste APK.");
        entryGate.setDeviceCode(accountSync.getDeviceCode());
        accountSync.restoreExistingSession();
    }

    private void showEntryGate() {
        if(isFinishing()||entryGate==null)return;
        if(gameSurface!=null)gameSurface.onPause();
        gameLaunched=false;
        setContentView(entryGate);
        entryGate.setAccountState(accountSync!=null&&accountSync.isConfigured(),false,"",
                "Entre na sua conta para continuar.");
    }

    private void startContentSetup() {
        if(contentInstalling||isFinishing())return;
        if(accountSync==null||!accountSync.isSignedIn()) {
            showEntryGate();
            return;
        }
        contentInstalling=true;
        loadingView=new ContentLoadingView(this);
        setContentView(loadingView);
        loadingView.setProgress(0,0,"PREPARANDO SUA PRIMEIRA ENTRADA...");
        if(installer!=null)installer.shutdown();
        installer=new ContentPackInstaller(this,new ContentPackInstaller.Listener() {
            @Override public void onProgress(long downloaded,long total,String status) {
                if(loadingView!=null)loadingView.setProgress(downloaded,total,status);
            }
            @Override public void onFileProgress(String fileName,int index,int count,long done,long size) {
                if(loadingView!=null)loadingView.setFileProgress(fileName,index,count,done,size);
            }
            @Override public void onSuccess() {
                contentInstalling=false;
                if(loadingView!=null)loadingView.setStatus("UNIVERSO PRONTO • INICIANDO...");
                mainHandler.postDelayed(()->{
                    if(!isFinishing()&&accountSync!=null&&accountSync.isSignedIn())launchGame(true);
                    else if(!isFinishing())showEntryGate();
                },900);
            }
            @Override public void onFailure(String message) {
                contentInstalling=false;
                if(loadingView!=null)loadingView.setFailure(message,
                    ()->startContentSetup(),()->showEntryGate());
            }
        });
        installer.start();
    }

    private void launchGame(boolean contentPackReady) {
        if(isFinishing())return;
        if(accountSync==null||!accountSync.isSignedIn()) {
            showEntryGate();
            return;
        }
        if(!contentPackReady||!ContentPackInstaller.isInstalled(this)) {
            startContentSetup();
            return;
        }
        if(gameSurface!=null)gameSurface.onPause();
        FrameLayout root=new FrameLayout(this);
        gameSurface=new GameSurfaceView(this);
        int[] settings=ContentPackInstaller.readIslandSettings(this);
        gameSurface.setContentPackData(settings[0],settings[1]);
        hud=new HudOverlay(this,gameSurface,profilePrefs);
        hud.setUsernameAccountActions((username,password,createAccount)->{
            if(accountSync!=null)accountSync.authenticateUsername(username,password,createAccount);
        },()->{
            if(accountSync!=null)accountSync.syncNow();
        },()->{
            if(accountSync!=null)accountSync.signOut();
        });
        hud.setGoogleAccountStatus(accountSync.isConfigured(),accountSync.isSignedIn(),
                accountSync.getUsername(),"Conta BATTLE UNIVERSE autenticada.");
        hud.setDeviceIdentity(accountSync.getDeviceCode());
        hud.setRecoveryCodeAction(()->accountSync.rotateRecoveryCode());
        hud.setRoyaleSpinAction((requestId,callback)->accountSync.spinRoyale(requestId,callback::onResult));
        hud.setDailyRewardAction(callback->accountSync.claimDailyReward(callback::onResult));
        hud.setPromoCodeAction((code,callback)->accountSync.redeemPromoCode(code,callback::onResult));
        accountSync.fetchWallet((coins,diamonds,message)->{
            if(hud!=null)hud.setOnlineWallet(diamonds);
        });
        root.addView(gameSurface,new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(hud,new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        gameLaunched=true;
        gameSurface.onResume();
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
        if(gameSurface!=null&&gameLaunched)gameSurface.onResume();
        enterImmersiveMode();
        if(hud!=null)hud.invalidate();
    }
    @Override protected void onDestroy() {
        if(installer!=null)installer.shutdown();
        if(accountSync!=null)accountSync.close();
        super.onDestroy();
    }
}
