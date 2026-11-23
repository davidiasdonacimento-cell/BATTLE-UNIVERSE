package com.battleuniverse.game;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private GameSurfaceView gameSurface;
    private HudOverlay hud;
    private ContentPackInstaller installer;
    private ContentPackAnalyzer packAnalyzer;
    private ContentLoadingView loadingView;
    private AccountSyncManager accountSync;
    private SharedPreferences profilePrefs;
    private final Handler mainHandler=new Handler(Looper.getMainLooper());
    private static final int REQUEST_PACK_FILE=4201;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersiveMode();
        if(ContentPackInstaller.isInstalled(this)) launchGame(true);
        else startContentSetup();
    }

    private void startContentSetup() {
        loadingView=new ContentLoadingView(this);
        setContentView(loadingView);
        loadingView.setProgress(0,0,"PREPARANDO INSTALAÇÃO...");
        loadingView.setLocalPackAction(this::openLocalPackPicker);
        if(installer!=null)installer.shutdown();
        installer=new ContentPackInstaller(this,new ContentPackInstaller.Listener() {
            @Override public void onProgress(long downloaded,long total,String status) {
                if(loadingView!=null)loadingView.setProgress(downloaded,total,status);
            }
            @Override public void onFileProgress(String fileName,int index,int count,long done,long size) {
                if(loadingView!=null)loadingView.setFileProgress(fileName,index,count,done,size);
            }
            @Override public void onSuccess() {
                if(loadingView!=null)loadingView.setStatus("CONTEÚDO VALIDADO • REINICIANDO O JOGO...");
                mainHandler.postDelayed(()->{
                    if(!isFinishing()&&ContentPackInstaller.isInstalled(MainActivity.this)) recreate();
                    else if(!isFinishing()) startContentSetup();
                },1100);
            }
            @Override public void onFailure(String message) {
                if(loadingView!=null)loadingView.setFailure(message,()->startContentSetup(),()->launchGame(false));
            }
        });
        installer.start();
    }

    private void launchGame(boolean contentPackReady) {
        FrameLayout root=new FrameLayout(this);
        gameSurface=new GameSurfaceView(this);
        if(contentPackReady) {
            int[] settings=ContentPackInstaller.readIslandSettings(this);
            gameSurface.setContentPackData(settings[0],settings[1]);
        }
        profilePrefs=getSharedPreferences("battle_universe_profile",MODE_PRIVATE);
        hud=new HudOverlay(this,gameSurface,profilePrefs);
        hud.setUsernameAccountActions((username,password,createAccount)->{
            if(accountSync!=null)accountSync.authenticateUsername(username,password,createAccount);
        },()->{if(accountSync!=null)accountSync.syncNow();},()->{if(accountSync!=null)accountSync.signOut();});
        hud.setAiCoachAction((question,callback)->{
            if(accountSync!=null)accountSync.askCoach(question,callback);
            else callback.onResult(null,"Sistema de contas ainda não inicializado.");
        });
        hud.setGameApiActions((code,callback)->{
            if(accountSync!=null)accountSync.redeemPromoCode(code,callback);
            else callback.onResult(null,"Sistema de contas ainda não inicializado.");
        },(ignored,callback)->{
            if(accountSync!=null)accountSync.claimDailyReward(callback);
            else callback.onResult(null,"Sistema de contas ainda não inicializado.");
        },(ignored,callback)->{
            if(accountSync!=null)accountSync.fetchGameMail(callback);
            else callback.onResult(null,"Sistema de contas ainda não inicializado.");
        },(messageId,callback)->{
            if(accountSync!=null)accountSync.claimGameMail(messageId,callback);
            else callback.onResult(null,"Sistema de contas ainda não inicializado.");
        });
        hud.setShopPurchaseAction((payload,callback)->{
            if(accountSync==null){callback.onResult(null,"Sistema de contas ainda não inicializado.");return;}
            String[] parts=payload==null?new String[0]:payload.split("\\|",2);
            if(parts.length!=2){callback.onResult(null,"Solicitação de compra inválida.");return;}
            accountSync.purchaseShopItem(parts[0],parts[1],callback);
        });
        hud.setPackAnalyzerAction(()->{
            android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(android.content.Intent.EXTRA_MIME_TYPES,
                new String[]{"application/zip","application/octet-stream","application/x-zip-compressed"});
            startActivityForResult(intent,REQUEST_PACK_FILE);
        });
        accountSync=new AccountSyncManager(this,profilePrefs,(configured,signedIn,username,message)->{
            if(hud!=null)hud.setGoogleAccountStatus(configured,signedIn,username,message);
        });
        hud.setGoogleAccountStatus(true,accountSync.isSignedIn(),accountSync.getUsername(),
            "Crie uma conta ou entre com nickname e senha.");
        accountSync.restoreExistingSession();

        root.addView(gameSurface,new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(hud,new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));

        setContentView(root);
    }

    private void openLocalPackPicker() {
        if(installer!=null){installer.shutdown();installer=null;}
        android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(android.content.Intent.EXTRA_MIME_TYPES,
            new String[]{"application/zip","application/octet-stream","application/x-zip-compressed"});
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            |android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent,REQUEST_PACK_FILE);
    }

    private void analyzeSelectedPack(Uri uri) {
        if(packAnalyzer!=null)packAnalyzer.close();
        Toast.makeText(this,"Analisando nome, manifesto e SHA-256 do pacote...",Toast.LENGTH_LONG).show();
        packAnalyzer=new ContentPackAnalyzer(this,new ContentPackAnalyzer.Listener(){
            @Override public void onStatus(String message){Toast.makeText(MainActivity.this,message,Toast.LENGTH_SHORT).show();}
            @Override public void onConflict(String message,Runnable replaceAction){
                new android.app.AlertDialog.Builder(MainActivity.this)
                    .setTitle("CONFLITO DE PACOTE")
                    .setMessage(message)
                    .setNegativeButton("CANCELAR",null)
                    .setPositiveButton("SUBSTITUIR", (d,w)->replaceAction.run()).show();
            }
            @Override public void onSuccess(String message){
                Toast.makeText(MainActivity.this,message,Toast.LENGTH_LONG).show();
                mainHandler.postDelayed(()->{
                    if(!isFinishing()&&ContentPackInstaller.isInstalled(MainActivity.this))recreate();
                    else if(!isFinishing())startContentSetup();
                },900);
            }
            @Override public void onFailure(String message){new android.app.AlertDialog.Builder(MainActivity.this)
                .setTitle("ANÁLISE RECUSADA").setMessage(message).setPositiveButton("OK",null).show();}
        });
        packAnalyzer.analyze(uri);
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            |View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            |View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            |View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);if(hasFocus)enterImmersiveMode();
    }
    @Override protected void onPause(){if(gameSurface!=null)gameSurface.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(gameSurface!=null)gameSurface.onResume();enterImmersiveMode();if(hud!=null)hud.invalidate();}
    @Override protected void onActivityResult(int requestCode,int resultCode,android.content.Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQUEST_PACK_FILE) {
            if(resultCode==RESULT_OK&&data!=null&&data.getData()!=null) {
                Uri uri=data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(uri,
                        data.getFlags()&android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch(Exception ignored) { }
                analyzeSelectedPack(uri);
            } else if(!ContentPackInstaller.isInstalled(this)) {
                startContentSetup();
            }
        }
    }
    @Override protected void onDestroy(){
        if(installer!=null)installer.shutdown();
        if(packAnalyzer!=null)packAnalyzer.close();
        if(accountSync!=null)accountSync.close();
        super.onDestroy();
    }
}
