package com.battleuniverse.game;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public final class MainActivity extends Activity {
    private GameSurfaceView gameSurface;
    private HudOverlay hud;
    private ContentPackInstaller installer;
    private ContentLoadingView loadingView;
    private AccountSyncManager accountSync;
    private SharedPreferences profilePrefs;
    private SharedPreferences diagnosticPrefs;
    private EntryGateView entryGate;
    private final Handler mainHandler=new Handler(Looper.getMainLooper());
    private boolean gameLaunched;
    private boolean contentInstalling;
    private String previousExitNotice="";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersiveMode();
        diagnosticPrefs=getSharedPreferences("battle_universe_diagnostics",MODE_PRIVATE);
        previousExitNotice=readPreviousExitNotice();
        recordStartupStage("TELA_INICIAL","");
        profilePrefs=getSharedPreferences("battle_universe_profile",MODE_PRIVATE);
        entryGate=new EntryGateView(this);
        entryGate.setDiagnosticsNotice(previousExitNotice);
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
                    if(ContentPackInstaller.isInstalled(MainActivity.this))startContentVerification();
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

    private String readPreviousExitNotice() {
        if(Build.VERSION.SDK_INT<30)return "";
        try {
            ActivityManager manager=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
            if(manager==null)return "";
            java.util.List<ApplicationExitInfo> exits=manager.getHistoricalProcessExitReasons(getPackageName(),0,1);
            if(exits==null||exits.isEmpty())return "";
            ApplicationExitInfo exit=exits.get(0);
            String reason;
            switch(exit.getReason()) {
                case ApplicationExitInfo.REASON_CRASH_NATIVE: reason="FALHA NA ENGINE NATIVA (C++/OPENGL)"; break;
                case ApplicationExitInfo.REASON_CRASH: reason="FALHA DO PROCESSO ANDROID"; break;
                case ApplicationExitInfo.REASON_ANR: reason="TRAVAMENTO/ANR DO ANDROID"; break;
                case ApplicationExitInfo.REASON_LOW_MEMORY: reason="ENCERRADO POR PRESSÃO DE MEMÓRIA"; break;
                default: return "";
            }
            String description=exit.getDescription();
            if(description==null)description="";
            if(description.length()>120)description=description.substring(0,120);
            String stage=diagnosticPrefs==null?"":diagnosticPrefs.getString("last_stage","");
            String file=diagnosticPrefs==null?"":diagnosticPrefs.getString("last_file","");
            String traceHint=readCrashHint(exit);
            StringBuilder report=new StringBuilder("DIAGNÓSTICO DA ÚLTIMA ABERTURA: ").append(reason);
            if(!stage.isEmpty())report.append(" • ETAPA: ").append(stage);
            if(!file.isEmpty())report.append(" • ARQUIVO: ").append(file);
            if(!description.isEmpty())report.append(" • ").append(description);
            if(!traceHint.isEmpty())report.append(" • ERRO: ").append(traceHint);
            return report.toString();
        } catch(Exception ignored) { return ""; }
    }

    private String readCrashHint(ApplicationExitInfo exit) {
        if(Build.VERSION.SDK_INT<31)return "";
        try(InputStream input=exit.getTraceInputStream()) {
            if(input==null)return "";
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            byte[] buffer=new byte[2048];
            int count,total=0;
            while((count=input.read(buffer))!=-1&&total<32768) {
                int allowed=Math.min(count,32768-total);
                output.write(buffer,0,allowed);
                total+=allowed;
            }
            String trace=output.toString("UTF-8");
            String[] markers={"Caused by:","FATAL EXCEPTION","OutOfMemoryError","SIGSEGV","SIGABRT"};
            for(String marker:markers) {
                int at=trace.lastIndexOf(marker);
                if(at>=0) {
                    int end=trace.indexOf('\n',at);
                    String line=end>at?trace.substring(at,end):trace.substring(at);
                    line=line.replace('\r',' ').trim();
                    return line.length()>150?line.substring(0,150):line;
                }
            }
        } catch(Exception ignored) { }
        return "";
    }

    private void recordStartupStage(String stage,String file) {
        if(diagnosticPrefs==null)return;
        SharedPreferences.Editor editor=diagnosticPrefs.edit().putString("last_stage",stage);
        if(file!=null&&!file.isEmpty())editor.putString("last_file",file);
        editor.apply();
    }
    /** Synchronous checkpoint immediately before a crash-sensitive startup boundary. */
    private void recordStartupCheckpoint(String stage) {
        if(diagnosticPrefs==null)return;
        diagnosticPrefs.edit().putString("last_stage",stage).commit();
    }
    private void showEntryGate() {
        if(isFinishing()||entryGate==null)return;
        if(gameSurface!=null)gameSurface.onPause();
        gameLaunched=false;
        setContentView(entryGate);
        entryGate.setAccountState(accountSync!=null&&accountSync.isConfigured(),false,"",
                "Entre na sua conta para continuar.");
    }

    private void startContentVerification() {
        if(contentInstalling||isFinishing())return;
        if(accountSync==null||!accountSync.isSignedIn()) {
            showEntryGate();
            return;
        }
        contentInstalling=true;
        recordStartupStage("INICIANDO_VERIFICACAO_DA_PASTA","");
        loadingView=new ContentLoadingView(this);
        setContentView(loadingView);
        loadingView.setProgress(0,1,"VERIFICANDO OS ARQUIVOS DA PASTA DO JOGO...");
        if(installer!=null)installer.shutdown();
        installer=new ContentPackInstaller(this,new ContentPackInstaller.Listener() {
            @Override public void onProgress(long done,long total,String status) {
                recordStartupStage(status,"");
                if(loadingView!=null)loadingView.setProgress(done,total,status);
            }
            @Override public void onFileProgress(String name,int index,int count,long done,long size) {
                recordStartupStage("VERIFICANDO_ARQUIVO",name);
                if(loadingView!=null)loadingView.setFileProgress(name,index,count,done,size);
            }
            @Override public void onSuccess() {
                recordStartupStage("VERIFICACAO_CONCLUIDA","");
                contentInstalling=false;
                if(!isFinishing()&&accountSync!=null&&accountSync.isSignedIn())launchGame(true);
                else if(!isFinishing())showEntryGate();
            }
            @Override public void onFailure(String message) {
                recordStartupStage("FALHA_NA_VERIFICACAO",message);
                contentInstalling=false;
                if(loadingView!=null)loadingView.setFailure(
                    "A verificação encontrou um problema: "+message,
                    ()->startContentSetup(),()->showEntryGate());
            }
        });
        installer.verifyInstalled();
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
        recordStartupCheckpoint("ANTES_DE_CRIAR_GAME_SURFACE");
        if(gameSurface!=null)gameSurface.onPause();
        FrameLayout root=new FrameLayout(this);
        int[] settings=ContentPackInstaller.readIslandSettings(this);
        recordStartupCheckpoint("CONFIGURACAO_SEASON02_CARREGADA");
        gameSurface=new GameSurfaceView(this,settings[0],settings[1]);
        recordStartupCheckpoint("GAME_SURFACE_CRIADA");
        recordStartupCheckpoint("ANTES_DE_CRIAR_HUD");
        hud=new HudOverlay(this,gameSurface,profilePrefs);
        recordStartupCheckpoint("HUD_CRIADA");
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
            if(hud!=null)hud.setOnlineWallet(coins,diamonds);
        });
        root.addView(gameSurface,new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(hud,new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
        recordStartupCheckpoint("ANTES_DE_SET_CONTENT_VIEW");
        setContentView(root);
        recordStartupCheckpoint("LAYOUT_DO_LOBBY_INSTALADO");
        gameLaunched=true;
        recordStartupCheckpoint("ANTES_DE_INICIAR_RENDERER");
        gameSurface.onResume();
        recordStartupCheckpoint("RENDERER_RETOMADO");
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
