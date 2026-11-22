package com.battleuniverse.game;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.provider.Settings;
import android.content.Context;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native Android client for the BATTLE UNIVERSE FastAPI/PostgreSQL account service. */
public final class AccountSyncManager implements SharedPreferences.OnSharedPreferenceChangeListener {
    public interface Listener {
        void onAccountState(boolean serverConfigured,boolean signedIn,String username,String message);
        default void onDeviceIdentity(String deviceCode) {}
        default void onRecoveryCode(String recoveryCode) {}
    }
    public interface WalletListener { void onWallet(long coins,long diamonds,String message); }
    public interface PromoListener { void onResult(boolean success,long balance,String message); }
    public interface RoyaleListener {
        void onResult(boolean success,String prizeId,String prizeName,boolean duplicate,long balance,String message);
    }
    public interface DailyRewardListener {
        void onResult(boolean claimed,long coins,long diamonds,String message);
    }
    private static final Set<String> LONG_PROFILE_KEYS=new HashSet<>(Arrays.asList(
        "xp","missions_day","match_reward_session_id",
        "last_match_reward_xp"
    ));
    private static final String[] PROFILE_KEYS={
        "mode","map","character","quality","music","effects","look_sensitivity","sensitivity_general","sensitivity_aim",
        "invert_camera_y","show_minimap","haptics","selected_mode","selected_map","selected_character",
        "selected_shirt","selected_boots","selected_set","wear_shirt_tactical","wear_shirt_neon","wear_shirt_frost",
        "wear_shirt_desert","wear_boots_combat","wear_boots_neon","wear_boots_frost","wear_boots_gold",
        "wear_set_aurora","wear_set_champion","wear_set_orbit","wear_set_desert",
        "wear_set_volcano","wear_set_nebula","wear_set_cerrado","wear_beard_old","wear_pants_angelical",
        "equipped_beard","equipped_helmet","equipped_backpack","equipped_vest","selected_pants","character_display_name",
        "nickname","xp","profile_schema_version",
        "matches_played","match_wins","match_kills","match_damage","match_resources",
        "last_match_kills","last_match_damage","last_match_resources","last_match_result","match_history",
        "royale_history","royale_spin_count",
        "mission_matches","mission_eliminations","mission_crystals","missions_day",
        "mission_claimed_0","mission_claimed_1","mission_claimed_2","wardrobe_starter_v2_granted",
        "shop_frame_neon","shop_emblem_bloob","shop_title_champion",
        "match_reward_session_id","match_reward_recorded",
        "last_match_reward_diamonds","last_match_reward_xp","last_match_reward_coins"
    };
    private final SharedPreferences prefs;
    private final Context appContext;
    private final Listener listener;
    private final AccountSessionStore session;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private final Runnable saveRunnable=this::saveProfile;
    private final String baseUrl;
    private volatile boolean closed;
    private final AtomicBoolean recoveryBusy=new AtomicBoolean(false);
    private boolean listening,applying;
    private String status="";
    public AccountSyncManager(Activity activity,SharedPreferences prefs,Listener listener){
        this.prefs=prefs;this.listener=listener;this.appContext=activity.getApplicationContext();session=new AccountSessionStore(activity);
        String url=BuildConfig.ACCOUNT_API_BASE_URL==null?"":BuildConfig.ACCOUNT_API_BASE_URL.trim();
        while(url.endsWith("/"))url=url.substring(0,url.length()-1);baseUrl=url;
        publish(isConfigured(),isSignedIn(),getUsername(),isConfigured()?"Servidor de contas configurado.":"Servidor ainda não configurado neste APK.");
    }
    public boolean isConfigured(){return baseUrl.startsWith("https://")||(BuildConfig.DEBUG&&baseUrl.startsWith("http://"));}
    public boolean isSignedIn(){return isConfigured()&&!session.getToken().isEmpty()&&session.getExpiresAt()>System.currentTimeMillis()/1000L;}
    public String getUsername(){return session.getUsername();}
    public void restoreExistingSession(){
        if(!isConfigured()||closed)return;
        if(!isSignedIn()){if(!session.getToken().isEmpty())session.clear();publish(true,false,"","Entre uma vez para manter sua conta conectada neste aparelho.");return;}
        network.execute(()->{try{
            JSONObject r=request("GET","/v1/accounts/me",null,session.getToken()),p=r.optJSONObject("player");
            if(p==null)throw new ApiException(502,"Resposta inválida do servidor.");
            String name=p.optString("username",getUsername());session.save(session.getToken(),name,session.getExpiresAt());
            applyProfile(p.optJSONObject("profile"));syncGameSettings(session.getToken());registerListener();registerDevice(session.getToken());saveProfile();publish(true,true,name,"Login restaurado automaticamente.");
        }catch(ApiException e){if(e.code==401)session.clear();publish(true,false,"",e.code==401?"Sessão expirada. Entre novamente.":e.getMessage());
        }catch(Exception e){publish(true,true,getUsername(),"Servidor indisponível. Sessão mantida neste aparelho; tente novamente com internet.");}});
    }
    public void authenticateUsername(String raw,String password,boolean create){
        if(!isConfigured()){publish(false,false,"","Servidor ainda não configurado neste APK.");return;}
        String name=raw==null?"":raw.trim().toLowerCase(Locale.ROOT);
        if(!name.matches("[a-z0-9_]{3,18}")){publish(true,false,"","Use 3 a 18 letras, números ou _ no nickname.");return;}
        if(password==null||password.length()<8||password.length()>72){publish(true,false,"","A senha precisa ter de 8 a 72 caracteres.");return;}
        publish(true,isSignedIn(),name,create?"Criando conta...":"Entrando na conta...");
        final String submitted=password;
        network.execute(()->{try{
            JSONObject body=new JSONObject();body.put("username",name);body.put("password",submitted);
            JSONObject r=request("POST",create?"/v1/accounts/register":"/v1/accounts/login",body,null);
            String token=r.optString("access_token","");int seconds=r.optInt("expires_in",0);JSONObject player=r.optJSONObject("player");
            if(token.isEmpty()||seconds<=0||player==null)throw new ApiException(502,"Sessão inválida retornada pelo servidor.");
            session.save(token,player.optString("username",name),System.currentTimeMillis()/1000L+seconds);
            applyProfile(player.optJSONObject("profile"));syncGameSettings(token);registerListener();registerDevice(token);saveProfile();
            String firstRecoveryCode=create?r.optString("recovery_code",""):"";
            if(!firstRecoveryCode.isEmpty()&&listener!=null)
                main.post(()->{if(!closed)listener.onRecoveryCode(firstRecoveryCode);});
            publish(true,true,name,create?"Conta criada. Guarde o código de recuperação exibido.":"Login realizado; sessão salva para atualizações.");
            saveProfile();
        }catch(ApiException e){publish(true,isSignedIn(),getUsername(),e.getMessage());}
        catch(Exception e){publish(true,isSignedIn(),getUsername(),"Não foi possível conectar. Verifique a internet.");}});
    }
    public void fetchWallet(WalletListener callback) {
        if(!isSignedIn()){if(callback!=null)main.post(()->callback.onWallet(0,0,"Entre para consultar a carteira online."));return;}
        network.execute(()->{
            try {
                JSONObject result=request("GET","/v1/accounts/me/wallet",null,session.getToken());
                long coins=0,diamonds=result.optLong("diamonds",0);
                if(callback!=null)main.post(()->{if(!closed)callback.onWallet(coins,diamonds,"Carteira online atualizada.");});
            } catch(Exception e) {
                if(callback!=null)main.post(()->{if(!closed)callback.onWallet(0,0,"Não foi possível atualizar a carteira online.");});
            }
        });
    }

    public void redeemPromoCode(String rawCode,PromoListener callback) {
        if(!isSignedIn()){if(callback!=null)main.post(()->callback.onResult(false,0,"Entre na conta para resgatar um código."));return;}
        String code=rawCode==null?"":rawCode.trim().toUpperCase(Locale.ROOT);
        if(!code.matches("[A-Z0-9_-]{3,32}")){if(callback!=null)main.post(()->callback.onResult(false,0,"Digite um código válido."));return;}
        network.execute(()->{
            try {
                JSONObject body=new JSONObject();body.put("code",code);
                JSONObject result=request("POST","/v1/promo-codes/redeem",body,session.getToken());
                long balance=result.optLong("diamonds_balance",0);
                long awarded=result.optLong("diamonds_awarded",0);
                if(callback!=null)main.post(()->{if(!closed)callback.onResult(true,balance,"Código resgatado: +"+awarded+" diamantes.");});
            } catch(ApiException e) {
                if(callback!=null)main.post(()->{if(!closed)callback.onResult(false,0,e.getMessage());});
            } catch(Exception e) {
                if(callback!=null)main.post(()->{if(!closed)callback.onResult(false,0,"Falha ao comunicar com o servidor.");});
            }
        });
    }

    public void spinRoyale(String requestId,RoyaleListener callback) {
        if(!isSignedIn()){if(callback!=null)main.post(()->callback.onResult(false,"","",false,0,"Entre na conta para usar o Royale online."));return;}
        network.execute(()->{
            try {
                JSONObject body=new JSONObject();body.put("request_id",requestId==null||requestId.isEmpty()?UUID.randomUUID().toString():requestId);
                JSONObject result=request("POST","/v1/royale/spin",body,session.getToken());
                boolean duplicate=result.optBoolean("duplicate",false);
                String prizeId=result.optString("prize_id","");
                String prizeName=result.optString("prize_name","");
                long balance=result.optLong("diamonds_balance",0);
                if(callback!=null)main.post(()->{if(!closed)callback.onResult(true,prizeId,prizeName,duplicate,balance,"Giro confirmado pelo servidor.");});
            } catch(ApiException e) {
                if(callback!=null)main.post(()->{if(!closed)callback.onResult(false,"","",false,0,e.getMessage());});
            } catch(Exception e) {
                if(callback!=null)main.post(()->{if(!closed)callback.onResult(false,"","",false,0,"Falha ao confirmar o giro no servidor.");});
            }
        });
    }

    public void claimDailyReward(DailyRewardListener callback) {
        if(!isSignedIn()){if(callback!=null)main.post(()->callback.onResult(false,0,0,"Entre para resgatar a recompensa diária."));return;}
        network.execute(()->{
            try {
                JSONObject result=request("POST","/v1/game/rewards/daily",new JSONObject(),session.getToken());
                JSONObject wallet=result.optJSONObject("wallet");
                long coins=0;
                long diamonds=wallet==null?0:wallet.optLong("diamonds",0);
                boolean claimed=result.optBoolean("claimed",false);
                String message=claimed?"Presente diário recebido: +100 diamantes."
                        :"O presente de hoje já foi resgatado.";
                if(callback!=null)main.post(()->{if(!closed)callback.onResult(claimed,coins,diamonds,message);});
            } catch(Exception e) {
                if(callback!=null)main.post(()->{if(!closed)callback.onResult(false,0,0,"Não foi possível resgatar o presente diário.");});
            }
        });
    }

    public String getDeviceCode() {
        return prefs.getString("server_device_code", "");
    }

    private String stableInstallId() throws Exception {
        String androidId=Settings.Secure.getString(appContext.getContentResolver(),Settings.Secure.ANDROID_ID);
        if(androidId==null||androidId.trim().isEmpty())throw new Exception("Identificador Android indisponível.");
        String material=appContext.getPackageName()+"|"+androidId+"|BATTLE_UNIVERSE_DEVICE_V1";
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder();
        for(byte value:digest)out.append(String.format(Locale.US,"%02x",value&0xff));
        return out.toString();
    }

    private void registerDevice(String token) {
        if(closed||token==null||token.isEmpty())return;
        network.execute(()->{
            try {
                JSONObject body=new JSONObject();
                body.put("install_id",stableInstallId());
                String label=(Build.MANUFACTURER+" "+Build.MODEL).trim();
                if(label.length()>40)label=label.substring(0,40);
                body.put("device_label",label.isEmpty()?"Android":label);
                JSONObject result=request("POST","/v1/accounts/me/devices/register",body,token);
                String code=result.optString("device_id","");
                if(!code.isEmpty()){
                    prefs.edit().putString("server_device_code",code).apply();
                    if(listener!=null)main.post(()->{if(!closed)listener.onDeviceIdentity(code);});
                }
            } catch(Exception ignored) {
                // Device registration is retried on the next authenticated launch.
            }
        });
    }

    public void rotateRecoveryCode() {
        if(!isSignedIn()){publish(isConfigured(),false,"","Entre na conta para gerar um código de recuperação.");return;}
        if(!recoveryBusy.compareAndSet(false,true))return;
        network.execute(()->{
            try {
                JSONObject body=new JSONObject();body.put("confirm",true);
                JSONObject result=request("POST","/v1/accounts/me/recovery-code/rotate",body,session.getToken());
                String code=result.optString("recovery_code","");
                if(code.isEmpty())throw new ApiException(502,"O servidor não retornou o código de recuperação.");
                if(listener!=null)main.post(()->{if(!closed)listener.onRecoveryCode(code);});
                publish(true,true,getUsername(),"Código novo gerado. Guarde-o agora; o anterior foi invalidado.");
            } catch(Exception e) {
                publish(true,isSignedIn(),getUsername(),"Não foi possível gerar o código de recuperação. Tente novamente.");
            } finally {
                recoveryBusy.set(false);
            }
        });
    }

    public void recoverAccount(String rawUsername,String recoveryCode) {
        String name=rawUsername==null?"":rawUsername.trim().toLowerCase(Locale.ROOT);
        String code=recoveryCode==null?"":recoveryCode.trim();
        if(!isConfigured()){publish(false,false,"","Servidor de contas não configurado.");return;}
        if(!name.matches("[a-z0-9_]{3,18}")||code.length()<20||code.length()>80){
            publish(true,false,"","Nickname ou código de recuperação inválido.");return;
        }
        if(!recoveryBusy.compareAndSet(false,true))return;
        publish(true,false,name,"Validando código de recuperação...");
        network.execute(()->{
            try {
                JSONObject body=new JSONObject();body.put("username",name);body.put("recovery_code",code);
                JSONObject result=request("POST","/v1/accounts/recover",body,null);
                String token=result.optString("access_token","");int seconds=result.optInt("expires_in",0);
                JSONObject player=result.optJSONObject("player");
                String replacement=result.optString("new_recovery_code","");
                if(token.isEmpty()||seconds<=0||player==null||replacement.isEmpty())
                    throw new ApiException(502,"Resposta de recuperação incompleta.");
                session.save(token,player.optString("username",name),System.currentTimeMillis()/1000L+seconds);
                applyProfile(player.optJSONObject("profile"));syncGameSettings(token);registerListener();registerDevice(token);
                if(listener!=null)main.post(()->{if(!closed)listener.onRecoveryCode(replacement);});
                publish(true,true,name,"Conta recuperada. Salve o novo código de recuperação.");
            } catch(ApiException e) {
                publish(true,false,"",e.getMessage());
            } catch(Exception e) {
                publish(true,false,"","Falha ao recuperar a conta. Verifique a conexão e tente novamente.");
            } finally {
                recoveryBusy.set(false);
            }
        });
    }

    private void syncGameSettings(String token) {
        try {
            JSONObject data=request("GET","/v1/game/settings",null,token);
            String gameName=data.optString("game_name","BATTLE UNIVERSE");
            JSONObject names=data.optJSONObject("character_names");
            SharedPreferences.Editor edit=prefs.edit().putString("server_game_name",gameName);
            if(names!=null)edit.putString("server_character_names",names.toString());
            String accountName=data.optString("account_character_name","");
            if(!accountName.isEmpty())edit.putString("character_display_name",accountName);
            edit.apply();
        } catch(Exception ignored) {
            // Keep last known settings; account and offline profile remain usable.
        }
    }

    public void signOut(){session.clear();if(listening){prefs.unregisterOnSharedPreferenceChangeListener(this);listening=false;}
        publish(isConfigured(),false,"","Você saiu. A conta continua salva no servidor.");}
    private void registerListener(){if(!listening&&!closed){prefs.registerOnSharedPreferenceChangeListener(this);listening=true;}}
    @Override public void onSharedPreferenceChanged(SharedPreferences p,String key){
        if(closed||applying||!isSignedIn())return;for(String allowed:PROFILE_KEYS)if(allowed.equals(key)){
            main.removeCallbacks(saveRunnable);main.postDelayed(saveRunnable,1000L);return;}
    }
    public void syncNow(){if(!isConfigured())publish(false,false,"","Servidor ainda não configurado.");
        else if(!isSignedIn())publish(true,false,"","Entre na conta para sincronizar.");
        else saveProfile();}
    private JSONObject collectProfile()throws Exception{
        JSONObject out=new JSONObject();Map<String,?> all=prefs.getAll();
        for(String key:PROFILE_KEYS){Object v=all.get(key);if(v instanceof String||v instanceof Number||v instanceof Boolean)out.put(key,v);}
        return out;
    }
    private void applyProfile(JSONObject profile){
        if(profile==null||closed)return;applying=true;SharedPreferences.Editor ed=prefs.edit();Map<String,?> old=prefs.getAll();
        for(String key:PROFILE_KEYS){if(!profile.has(key)||profile.isNull(key))continue;Object v=profile.opt(key),prior=old.get(key);
            if(v instanceof Boolean)ed.putBoolean(key,(Boolean)v);
            else if(v instanceof Number) {
                if(LONG_PROFILE_KEYS.contains(key)||prior instanceof Long)ed.putLong(key,((Number)v).longValue());
                else ed.putInt(key,((Number)v).intValue());
            }
            else if(v instanceof String&&(prior==null||prior instanceof String))ed.putString(key,(String)v);}
        ed.commit();applying=false;
    }
    private void saveProfile(){
        if(closed||!isSignedIn())return;try{JSONObject body=new JSONObject();body.put("profile",collectProfile());
            request("PATCH","/v1/accounts/me/profile",body,session.getToken());
            publish(true,true,getUsername(),"Configurações da conta sincronizadas.");
        }catch(ApiException e){if(e.code==401)session.clear();publish(true,isSignedIn(),getUsername(),e.getMessage());}
        catch(Exception e){publish(true,isSignedIn(),getUsername(),"Falha ao sincronizar. Dados locais preservados.");}
    }
    private JSONObject request(String method,String path,JSONObject body,String token)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(baseUrl+path).openConnection();
        c.setRequestMethod(method);c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setRequestProperty("Accept","application/json");
        if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");}
        if(token!=null&&!token.isEmpty())c.setRequestProperty("Authorization","Bearer "+token);
        try{
            if(body!=null){byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream o=c.getOutputStream()){o.write(bytes);}}
            int code=c.getResponseCode();InputStream stream=code>=200&&code<300?c.getInputStream():c.getErrorStream();
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();if(stream!=null)try(InputStream in=stream){
                byte[] chunk=new byte[4096];int n;while((n=in.read(chunk))!=-1)buffer.write(chunk,0,n);}
            String text=buffer.toString(StandardCharsets.UTF_8.name());
            JSONObject json;
            try { json=text.isEmpty()?new JSONObject():new JSONObject(text); }
            catch(org.json.JSONException malformed) {
                if(code<200||code>=300) {
                    String detail=text.trim();if(detail.length()>180)detail=detail.substring(0,180);
                    throw new ApiException(code,detail.isEmpty()?"Servidor retornou erro HTTP "+code+".":detail);
                }
                throw new ApiException(502,"O servidor respondeu em formato inválido. Tente novamente.");
            }
            if(code<200||code>=300)throw new ApiException(code,json.optString("detail","Erro HTTP "+code+" ao comunicar com o servidor."));
            return json;
        }finally{c.disconnect();}
    }
    private void publish(boolean configured,boolean signed,String username,String message){
        final String safeMessage=message==null?"":message;
        status=safeMessage;if(listener==null||closed)return;
        final String safeUsername=username==null?"":username;
        main.post(()->{if(!closed)listener.onAccountState(configured,signed,safeUsername,safeMessage);});
    }
    public void close(){closed=true;main.removeCallbacks(saveRunnable);if(listening){prefs.unregisterOnSharedPreferenceChangeListener(this);listening=false;}network.shutdownNow();}
    private static final class ApiException extends Exception{final int code;ApiException(int c,String m){super(m);code=c;}}
}
