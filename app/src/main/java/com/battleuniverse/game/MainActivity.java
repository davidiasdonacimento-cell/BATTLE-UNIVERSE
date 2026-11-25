package com.battleuniverse.game;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.content.Intent;
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
    private final Handler traceHandler=new Handler(Looper.getMainLooper());
    private final Runnable traceSampler=new Runnable(){@Override public void run(){
        if(gameSurface!=null){int stage=gameSurface.getDiagnosticStage();
            int elimination=gameSurface.getLastEliminationStage();
            android.content.SharedPreferences.Editor trace=getSharedPreferences("bu_crash_trace",MODE_PRIVATE).edit();
            if(stage>=0)trace.putInt("last_stage",stage);
            if(elimination>=0)trace.putInt("last_elimination_stage",elimination);
            trace.apply();}
        if(!isFinishing())traceHandler.postDelayed(this,180);
    }};

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashDiagnostics.install(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersiveMode();
        // O pacote 4K é opcional. A ausência dele nunca pode bloquear a inicialização
        // nem iniciar um download de centenas de MB automaticamente.
        android.content.SharedPreferences tracePrefs=getSharedPreferences("bu_crash_trace",MODE_PRIVATE);
        int previousStage=tracePrefs.getInt("last_stage",-1);
        int previousElimination=tracePrefs.getInt("last_elimination_stage",-1);
        launchGame(ContentPackInstaller.isInstalled(this));
        if(previousStage>0||previousElimination>0) new android.app.AlertDialog.Builder(this)
            .setTitle("DIAGNÓSTICO DE ESTABILIDADE")
            .setMessage("Última etapa registrada: "+diagnosticStageName(previousStage)+"."
                +(previousElimination>0?"\nÚltima etapa da eliminação: "+diagnosticStageName(previousElimination)+".":"")
                +"\n\nRegistro técnico, não confirmação isolada da causa.")
            .setPositiveButton("OK",null).show();
        traceHandler.post(traceSampler);
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
        hud.setAiChatAction(()->{
            if(accountSync==null || !accountSync.isSignedIn()) {
                Toast.makeText(this,"Entre na sua conta para abrir o BATTLE AI PRO MAXX.",Toast.LENGTH_LONG).show();
                return;
            }
            startActivity(new Intent(MainActivity.this,BattleAiChatActivity.class));
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
        hud.setMatchResultAction((payload,callback)->{
            if(accountSync!=null)accountSync.completeMatch(payload,callback);
            else callback.onResult(null,"Sistema de contas ainda não inicializado.");
        });
        hud.setRewardClaimAction((payload,callback)->{
            if(accountSync==null){callback.onResult(null,"Sistema de contas ainda não inicializado.");return;}
            String[] parts=payload==null?new String[0]:payload.split("\\|",2);
            if(parts.length!=2){callback.onResult(null,"Recompensa inválida.");return;}
            accountSync.claimConfiguredReward(parts[0],parts[1],callback);
        });
        hud.setPackAnalyzerAction(this::showContentPackOptions);
        hud.setOwnerControllerAction(()->{if(accountSync!=null)accountSync.openOwnerMailController();});
        accountSync=new AccountSyncManager(this,profilePrefs,(configured,signedIn,username,message)->{
            if(hud!=null)hud.setGoogleAccountStatus(configured,signedIn,username,message);
        });
        hud.setGoogleAccountStatus(true,accountSync.isSignedIn(),accountSync.getUsername(),
            "Crie uma conta ou entre com nickname e senha.");
        // Primeiro anexe a HUD e a superfície OpenGL à Activity. Restaurar a conta
        // antes de setContentView podia aplicar o perfil enquanto a tela ainda estava
        // sendo construída, disparando atualizações de preferências durante a inicialização.
        root.addView(gameSurface,new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(hud,new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));

        setContentView(root);

        // A restauração de rede começa somente depois que a hierarquia da tela foi anexada.
        root.post(() -> {
            if (!isFinishing() && !isDestroyed() && accountSync != null) {
                accountSync.restoreExistingSession();
            }
        });
    }

    private void showContentPackOptions() {
        String[] options={
            "Baixar pacote oficial Season 02 (aprox. 429 MB)",
            "Selecionar ZIP Season 02 já baixado"
        };
        new android.app.AlertDialog.Builder(this)
            .setTitle("CONTEÚDO EXPANDIDO 4K")
            .setItems(options,(dialog,which)->{
                if(which==0) startContentSetup();
                else if(which==1) openLocalPackPicker();
            })
            .setNegativeButton("AGORA NÃO",null)
            .show();
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

    private String diagnosticStageName(int stage) {
        switch(stage){case 1:return "inicialização do renderizador";case 2:return "início do quadro";case 20:return "desenho do mundo";case 30:return "alvos e efeitos do disparo";case 50:return "início do disparo da arma";case 51:return "disparo processado";case 70:return "desenho do personagem";case 80:return "montagem do personagem com Carapina";case 81:return "início do desenho da Carapina";case 82:return "desenho da mira óptica da Carapina";case 83:return "fim do desenho do personagem com Carapina";case 90:return "fim do quadro";case 100:return "início do processamento do alvo atingido";case 101:return "registro do impacto e do efeito visual";case 102:return "início da transição de eliminação";case 103:return "desativação do personagem eliminado";case 104:return "contagem dos participantes restantes";case 105:return "fim do processamento da eliminação";case 110:return "início do desenho do indicador de dano";case 111:return "fim do desenho do indicador de dano";case 112:return "início do desenho final do jogador";case 113:return "fim do desenho final do jogador";case 120:return "fim do quadro com Carapina";default:return "etapa "+stage;}
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
    @Override protected void onPause(){if(gameSurface!=null){gameSurface.releaseAllInput();gameSurface.onPause();}super.onPause();}
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
            }
            // Cancelar o seletor mantém o jogador no lobby. Não iniciar download
            // automaticamente só porque o pacote opcional ainda não existe.
        }
    }
    @Override protected void onDestroy(){
        getSharedPreferences("bu_crash_trace",MODE_PRIVATE).edit().putInt("last_stage",0).apply();
        traceHandler.removeCallbacks(traceSampler);
        if(installer!=null)installer.shutdown();
        if(packAnalyzer!=null)packAnalyzer.close();
        if(accountSync!=null)accountSync.close();
        super.onDestroy();
    }
}
