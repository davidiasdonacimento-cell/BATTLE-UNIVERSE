package com.battleuniverse.game;

import android.app.Activity;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import org.json.JSONException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Username/password account client for the official BATTLE UNIVERSE API. */
public final class AccountSyncManager implements SharedPreferences.OnSharedPreferenceChangeListener {
    public interface Listener {
        void onAccountState(boolean configured, boolean signedIn, String username, String message);
    }

    private static final String API="https://battle-universe-accounts.onrender.com";
    private static final String PREFS="battle_universe_secure_session";
    private static final String KEY_ALIAS="battle_universe_api_session_v1";
    private static final String[] PROFILE_KEYS={
        "nickname","mode","map","character","quality","music","effects","look_sensitivity",
        "invert_camera_y","show_minimap","haptics","online_mode","selected_shirt","selected_boots",
        "selected_set","character_display_name"
    };
    private static final String[] SERVER_PROFILE_KEYS={
        "xp","matches_played","match_wins","match_kills","match_damage","match_resources",
        "last_match_kills","last_match_damage","last_match_resources","last_match_result",
        "mission_matches","mission_eliminations","mission_crystals","missions_day",
        "mission_claimed_0","mission_claimed_1","mission_claimed_2",
        "royale_tier_claimed_0","royale_tier_claimed_1","royale_tier_claimed_2",
        "royale_tier_claimed_3","royale_tier_claimed_4","royale_tier_claimed_5",
        "achievement_500","achievement_1500","achievement_3000","event_season_claimed",
        "mail_welcome_claimed","wardrobe_starter_v2_granted",
        "shop_frame_neon","shop_emblem_bloob","shop_title_champion",
        "wear_shirt_tactical","wear_shirt_neon","wear_shirt_frost","wear_shirt_desert",
        "wear_boots_combat","wear_boots_neon","wear_boots_frost","wear_boots_gold",
        "wear_set_aurora","wear_set_champion","wear_set_orbit","wear_set_desert",
        "wear_set_volcano","wear_set_nebula","wear_set_cerrado","wear_beard_old","wear_pants_angelical"
    };
    private final Activity activity;
    private final SharedPreferences profilePrefs;
    private final SharedPreferences sessionPrefs;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean closed=false;
    private volatile boolean busy=false;
    private volatile String username="";
    private volatile boolean applyingProfile=false;
    private final java.util.Set<String> watched=new java.util.HashSet<>();

    public AccountSyncManager(Activity activity,SharedPreferences prefs,Listener listener) {
        this.activity=activity;
        this.profilePrefs=prefs;
        this.sessionPrefs=activity.getSharedPreferences(PREFS,Activity.MODE_PRIVATE);
        this.listener=listener;
        this.username=sessionPrefs.getString("username","");
    }

    public boolean isConfigured(){return true;}
    public boolean isSignedIn(){return !getToken().isEmpty();}
    public String getUsername(){return username;}

    public void restoreExistingSession() {
        String token=getToken();
        if(token.isEmpty()) { publish(false,"","Crie uma conta ou entre com nickname e senha."); return; }
        requestProfile(token,false);
    }

    public void authenticateUsername(String raw,String password,boolean create) {
        if(closed||busy)return;
        String name=raw==null?"":raw.trim().toLowerCase(java.util.Locale.ROOT);
        if(!name.matches("[a-z0-9_]{3,18}")) {
            publish(isSignedIn(),username,"Use de 3 a 18 letras, números ou _ no nickname."); return;
        }
        if(password==null||password.length()<8||password.length()>72) {
            publish(isSignedIn(),username,"A senha precisa ter de 8 a 72 caracteres."); return;
        }
        busy=true;
        publish(isSignedIn(),name,create?"Criando conta no servidor...":"Validando login no servidor...");
        worker.execute(()->{
            try {
                JSONObject body=new JSONObject().put("username",name).put("password",password);
                JSONObject response=api(create?"/v1/accounts/register":"/v1/accounts/login","POST",null,body);
                String token=response.optString("access_token","");
                if(token.isEmpty())throw new Exception("O servidor não retornou uma sessão válida.");
                saveToken(token,name);
                username=name;
                if(create) {
                    String recovery=response.optString("recovery_code","");
                    try { syncProfile(token); } catch(Exception ignored) { }
                    refreshWallet(null);
                    registerProfileListener();
                    String note=recovery.isEmpty()?"Conta criada e conectada.":"Conta criada! ANOTE E GUARDE este código de recuperação: "+recovery;
                    publish(true,name,note);
                } else {
                    JSONObject player=response.optJSONObject("player");
                    JSONObject profile=player==null?new JSONObject():player.optJSONObject("profile");
                    if(profile==null||profile.length()==0)syncProfile(token);else applyProfile(profile);
                    refreshWallet(null);
                    registerProfileListener();
                    publish(true,name,"Login concluído. Perfil recuperado do servidor.");
                }
            } catch(Exception e) {
                publish(isSignedIn(),username,cleanError(e));
            } finally { busy=false; }
        });
    }

    public interface CoachListener { void onResult(String answer, String error); }
    public interface ApiActionListener { void onResult(JSONObject result, String error); }

    public void refreshWallet(ApiActionListener callback) {
        String token=getToken();
        if(token.isEmpty()) {
            if(callback!=null)main.post(()->callback.onResult(null,"Entre na conta para carregar a carteira."));
            return;
        }
        worker.execute(()->{
            try {
                JSONObject result=api("/v1/accounts/me/wallet","GET",token,null);
                JSONObject wallet=new JSONObject().put("diamonds",result.optLong("diamonds",0L));
                profilePrefs.edit().putLong("diamonds",wallet.optLong("diamonds",0L)).apply();
                if(callback!=null)main.post(()->callback.onResult(wallet,null));
            } catch(Exception e) {
                if(callback!=null) {
                    String message=cleanError(e);
                    main.post(()->callback.onResult(null,message));
                }
            }
        });
    }

    public void completeMatch(String payload, ApiActionListener callback) {
        String token=getToken();
        if(token.isEmpty()){main.post(()->callback.onResult(null,"Entre na conta para sincronizar as recompensas da partida."));return;}
        worker.execute(()->{
            try {
                JSONObject body=new JSONObject(payload==null?"{}":payload);
                JSONObject result=api("/v1/game/matches/complete","POST",token,body);
                JSONObject profile=result.optJSONObject("profile");
                if(profile!=null)applyProfile(profile);
                JSONObject wallet=result.optJSONObject("wallet");
                if(wallet!=null)profilePrefs.edit().putLong("diamonds",wallet.optLong("diamonds",0L)).apply();
                main.post(()->callback.onResult(result,null));
            } catch(Exception e) { String message=cleanError(e); main.post(()->callback.onResult(null,message)); }
        });
    }

    public void claimConfiguredReward(String rewardType,String rewardKey,ApiActionListener callback) {
        String token=getToken();
        if(token.isEmpty()){main.post(()->callback.onResult(null,"Entre na conta para resgatar recompensas."));return;}
        if(rewardType==null||!rewardType.matches("[a-z_]{3,32}")||rewardKey==null||!rewardKey.matches("[a-zA-Z0-9_]{1,64}")) {
            main.post(()->callback.onResult(null,"Recompensa inválida."));return;
        }
        worker.execute(()->{
            try {
                JSONObject result=api("/v1/game/rewards/"+rewardType+"/"+rewardKey+"/claim","POST",token,new JSONObject());
                JSONObject profile=result.optJSONObject("profile");
                if(profile!=null)applyProfile(profile);
                JSONObject wallet=result.optJSONObject("wallet");
                if(wallet!=null)profilePrefs.edit().putLong("diamonds",wallet.optLong("diamonds",0L)).apply();
                main.post(()->callback.onResult(result,null));
            } catch(Exception e) { String message=cleanError(e); main.post(()->callback.onResult(null,message)); }
        });
    }

    public void purchaseShopItem(String itemKey, String requestId, ApiActionListener callback) {
        String token=getToken();
        if(token.isEmpty()) {
            main.post(()->callback.onResult(null,"Entre na sua conta para comprar na loja."));
            return;
        }
        if(itemKey==null||!itemKey.matches("[a-z0-9_]{3,64}")
                ||requestId==null||!requestId.matches("[A-Za-z0-9._:-]{16,64}")) {
            main.post(()->callback.onResult(null,"Solicitação de compra inválida."));
            return;
        }
        worker.execute(()->{
            try {
                JSONObject body=new JSONObject().put("item_key",itemKey).put("request_id",requestId);
                JSONObject result=api("/v1/shop/purchase","POST",token,body);
                JSONObject wallet=result.optJSONObject("wallet");
                if(wallet!=null)profilePrefs.edit().putLong("diamonds",
                    wallet.optLong("diamonds",profilePrefs.getLong("diamonds",0L))).apply();
                JSONObject profile=result.optJSONObject("profile");
                if(profile!=null)applyProfile(profile);
                main.post(()->callback.onResult(result,null));
            } catch(Exception e) {
                String message=cleanError(e);
                // HTTP 409 is an expected business response, never a fatal game error.
                main.post(()->callback.onResult(null,message));
            }
        });
    }

    public void redeemPromoCode(String rawCode, ApiActionListener callback) {
        String token=getToken();
        if(token.isEmpty()){main.post(()->callback.onResult(null,"Entre na sua conta para resgatar códigos."));return;}
        String code=rawCode==null?"":rawCode.trim().toUpperCase(java.util.Locale.ROOT);
        if(!code.matches("[A-Z0-9_-]{3,32}")){main.post(()->callback.onResult(null,"Código inválido."));return;}
        worker.execute(()->{
            try {
                JSONObject result=api("/v1/promo-codes/redeem","POST",token,new JSONObject().put("code",code));
                main.post(()->callback.onResult(result,null));
                syncNow();
            } catch(Exception e) {
                String message=cleanError(e);
                main.post(()->callback.onResult(null,message));
            }
        });
    }

    public void claimDailyReward(ApiActionListener callback) {
        String token=getToken();
        if(token.isEmpty()){main.post(()->callback.onResult(null,"Entre na sua conta para coletar a recompensa."));return;}
        worker.execute(()->{
            try {
                JSONObject result=api("/v1/game/rewards/daily","POST",token,new JSONObject());
                main.post(()->callback.onResult(result,null));
                syncNow();
            } catch(Exception e) {
                String message=cleanError(e);
                main.post(()->callback.onResult(null,message));
            }
        });
    }

    public void fetchGameMail(ApiActionListener callback) {
        String token=getToken();
        if(token.isEmpty()){main.post(()->callback.onResult(null,"Entre na sua conta para abrir o correio."));return;}
        worker.execute(()->{
            try {
                JSONObject result=api("/v1/game/mail","GET",token,null);
                main.post(()->callback.onResult(result,null));
            } catch(Exception e) {
                String message=cleanError(e);
                main.post(()->callback.onResult(null,message));
            }
        });
    }

    public void claimGameMail(String messageId, ApiActionListener callback) {
        String token=getToken();
        if(token.isEmpty()){main.post(()->callback.onResult(null,"Entre na sua conta para resgatar o presente."));return;}
        String id=messageId==null?"":messageId.trim();
        if(!id.matches("[A-Za-z0-9._:-]{1,64}")){main.post(()->callback.onResult(null,"Mensagem inválida."));return;}
        worker.execute(()->{
            try {
                JSONObject result=api("/v1/game/mail/"+id+"/claim","POST",token,new JSONObject());
                main.post(()->callback.onResult(result,null));
                syncNow();
            } catch(Exception e) {
                String message=cleanError(e);
                main.post(()->callback.onResult(null,message));
            }
        });
    }

    public void askCoach(String question, CoachListener callback) {
        String token=getToken();
        if(token.isEmpty()) { main.post(()->callback.onResult(null,"Entre na sua conta para usar a IA.")); return; }
        worker.execute(()->{
            try {
                JSONObject response=api("/v1/ai/coach","POST",token,new JSONObject().put("question",question));
                String answer=response.optString("answer","").trim();
                if(answer.isEmpty()) throw new Exception("A IA não retornou uma resposta.");
                main.post(()->callback.onResult(answer,null));
            } catch(Exception e) {
                String message=cleanError(e);
                main.post(()->callback.onResult(null,message));
            }
        });
    }

    public void syncNow() {
        if(closed||busy)return;
        String token=getToken();
        if(token.isEmpty()){publish(false,"","Entre na sua conta antes de sincronizar.");return;}
        busy=true;
        publish(true,username,"Sincronizando perfil com o servidor...");
        worker.execute(()->{
            try {
                syncProfile(token);
                JSONObject account=api("/v1/accounts/me","GET",token,null);
                JSONObject player=account.optJSONObject("player");
                if(player!=null) {
                    JSONObject serverProfile=player.optJSONObject("profile");
                    if(serverProfile!=null)applyProfile(serverProfile);
                }
                JSONObject wallet=api("/v1/accounts/me/wallet","GET",token,null);
                profilePrefs.edit().putLong("diamonds",wallet.optLong("diamonds",0L)).apply();
                publish(true,username,"Perfil, progresso e carteira sincronizados com o servidor.");
            } catch(Exception e) {
                publish(true,username,"Falha ao sincronizar. Seus dados locais foram preservados. "+cleanError(e));
            } finally { busy=false; }
        });
    }

    public void signOut() {
        sessionPrefs.edit().remove("token_cipher").remove("token_iv").remove("username").apply();
        username="";
        publish(false,"","Você saiu da conta. O progresso local continua neste aparelho.");
    }

    private void requestProfile(String token,boolean uploadIfEmpty) {
        if(closed||busy)return;
        busy=true;
        worker.execute(()->{
            try {
                JSONObject response=api("/v1/accounts/me","GET",token,null);
                JSONObject player=response.optJSONObject("player");
                JSONObject profile=player==null?null:player.optJSONObject("profile");
                if(profile==null||profile.length()==0)syncProfile(token);else applyProfile(profile);
                refreshWallet(null);
                registerProfileListener();
                if(player!=null)username=player.optString("username",username);
                sessionPrefs.edit().putString("username",username).apply();
                publish(true,username,"Sessão restaurada. Perfil carregado do servidor.");
            } catch(Exception e) {
                String msg=cleanError(e);
                if(msg.contains("401")||msg.toLowerCase().contains("expirada")) {
                    sessionPrefs.edit().remove("token_cipher").remove("token_iv").remove("username").apply();
                    username="";
                    publish(false,"","Sua sessão expirou. Entre novamente.");
                } else publish(false,"","Não foi possível validar a sessão: "+msg);
            } finally { busy=false; }
        });
    }

    private void syncProfile(String token)throws Exception {
        JSONObject profile=new JSONObject();
        Map<String,?> all=profilePrefs.getAll();
        for(String key:PROFILE_KEYS) {
            Object value=all.get(key);
            if(value instanceof String||value instanceof Boolean||value instanceof Integer||value instanceof Long) {
                profile.put(key,value);
            }
        }
        JSONObject body=new JSONObject().put("profile",profile);
        api("/v1/accounts/me/profile","PATCH",token,body);
    }

    private void applyProfile(JSONObject profile) {
        if(profile==null)return;
        SharedPreferences.Editor edit=profilePrefs.edit();
        applyingProfile=true;
        Iterator<String> keys=profile.keys();
        while(keys.hasNext()) {
            String key=keys.next();
            if(!allowed(key))continue;
            Object value=profile.opt(key);
            if(value instanceof String)edit.putString(key,(String)value);
            else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);
            else if(value instanceof Number) {
                if(isIntegerPreference(key))edit.putInt(key,((Number)value).intValue());
                else edit.putLong(key,((Number)value).longValue());
            }
        }
        edit.commit();
        applyingProfile=false;
    }

    private boolean isIntegerPreference(String key) {
        return "mode".equals(key)||"map".equals(key)||"character".equals(key)||"quality".equals(key)
            ||"look_sensitivity".equals(key)||"selected_shirt".equals(key)||"selected_boots".equals(key)
            ||"selected_set".equals(key);
    }

    private void registerProfileListener() {
        if(!watched.isEmpty())return;
        for(String key:PROFILE_KEYS)watched.add(key);
        profilePrefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences prefs,String key) {
        if(closed||applyingProfile||!watched.contains(key)||!isSignedIn())return;
        main.removeCallbacks(autoSync);
        main.postDelayed(autoSync,1800L);
    }

    private final Runnable autoSync=()->syncNow();

    private boolean allowed(String key) {
        for(String item:PROFILE_KEYS)if(item.equals(key))return true;
        for(String item:SERVER_PROFILE_KEYS)if(item.equals(key))return true;
        return false;
    }

    private JSONObject api(String path,String method,String token,JSONObject body)throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept","application/json");
        if(token!=null&&!token.isEmpty())c.setRequestProperty("Authorization","Bearer "+token);
        if(body!=null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/json; charset=utf-8");
            byte[] data=body.toString().getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(data.length);
            try(OutputStream out=c.getOutputStream()){out.write(data);}
        }
        try {
            int code=c.getResponseCode();
            InputStream stream=code>=200&&code<300?c.getInputStream():c.getErrorStream();
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            if(stream!=null)try(InputStream in=stream){byte[] b=new byte[4096];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>1024*1024)throw new Exception("Resposta do servidor excedeu o limite.");out.write(b,0,n);}}
            String text=new String(out.toByteArray(),StandardCharsets.UTF_8);
            JSONObject json=text.isEmpty()?new JSONObject():new JSONObject(text);
            if(code<200||code>=300) {
                String detail=json.optString("detail","HTTP "+code);
                throw new Exception(detail+" (HTTP "+code+")");
            }
            return json;
        } finally {c.disconnect();}
    }

    private void saveToken(String token,String name)throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,getOrCreateKey());
        byte[] encrypted=cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        sessionPrefs.edit()
            .putString("token_iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
            .putString("token_cipher",Base64.encodeToString(encrypted,Base64.NO_WRAP))
            .putString("username",name).apply();
    }

    private String getToken() {
        try {
            String ivText=sessionPrefs.getString("token_iv","");
            String cipherText=sessionPrefs.getString("token_cipher","");
            if(ivText.isEmpty()||cipherText.isEmpty())return "";
            byte[] iv=Base64.decode(ivText,Base64.NO_WRAP);
            byte[] encrypted=Base64.decode(cipherText,Base64.NO_WRAP);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,getOrCreateKey(),new GCMParameterSpec(128,iv));
            return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);
        } catch(Exception e) {
            sessionPrefs.edit().remove("token_iv").remove("token_cipher").remove("username").apply();
            return "";
        }
    }

    private SecretKey getOrCreateKey()throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(KEY_ALIAS))return (SecretKey)store.getKey(KEY_ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }

    private String cleanError(Exception e) {
        String m=e.getMessage()==null?"Erro de comunicação.":e.getMessage();
        if(m.contains("Unable to resolve host")||m.contains("Failed to connect"))return "Servidor indisponível ou sem internet.";
        return m.length()>150?m.substring(0,150):m;
    }

    private void publish(boolean signed,String name,String message) {
        if(closed||listener==null)return;
        main.post(()->{if(!closed)listener.onAccountState(true,signed,name==null?"":name,message==null?"":message);});
    }

    public void close() {
        closed=true;
        main.removeCallbacks(autoSync);
        if(!watched.isEmpty())profilePrefs.unregisterOnSharedPreferenceChangeListener(this);
        worker.shutdownNow();
    }
}
