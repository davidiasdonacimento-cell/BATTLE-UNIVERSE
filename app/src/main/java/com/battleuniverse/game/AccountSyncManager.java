package com.battleuniverse.game;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native Android client for the BATTLE UNIVERSE FastAPI/PostgreSQL account service. */
public final class AccountSyncManager implements SharedPreferences.OnSharedPreferenceChangeListener {
    public interface Listener{void onAccountState(boolean serverConfigured,boolean signedIn,String username,String message);}
    private static final String[] PROFILE_KEYS={
        "mode","map","character","quality","music","effects","look_sensitivity","sensitivity_general","sensitivity_aim",
        "invert_camera_y","show_minimap","haptics","selected_mode","selected_map","selected_character",
        "selected_shirt","selected_boots","selected_set","wear_shirt_tactical","wear_shirt_neon","wear_shirt_frost",
        "wear_shirt_desert","wear_boots_combat","wear_boots_neon","wear_boots_frost","wear_boots_gold",
        "wear_set_aurora","wear_set_champion","wear_set_orbit","wear_set_desert",
        "wear_set_volcano","wear_set_nebula","wear_set_cerrado",
        "equipped_beard","equipped_helmet","equipped_backpack","equipped_vest","selected_pants","character_display_name"
    };
    private final SharedPreferences prefs;
    private final Listener listener;
    private final AccountSessionStore session;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private final Runnable saveRunnable=this::saveProfile;
    private final String baseUrl;
    private volatile boolean closed;
    private boolean listening,applying;
    private String status="";
    public AccountSyncManager(Activity activity,SharedPreferences prefs,Listener listener){
        this.prefs=prefs;this.listener=listener;session=new AccountSessionStore(activity);
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
            applyProfile(p.optJSONObject("profile"));syncGameSettings(session.getToken());registerListener();publish(true,true,name,"Login restaurado automaticamente.");
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
            applyProfile(player.optJSONObject("profile"));syncGameSettings(token);registerListener();
            publish(true,true,name,create?"Conta criada e sessão protegida neste aparelho.":"Login realizado; sessão salva para atualizações.");
            if(create)saveProfile();
        }catch(ApiException e){publish(true,isSignedIn(),getUsername(),e.getMessage());}
        catch(Exception e){publish(true,isSignedIn(),getUsername(),"Não foi possível conectar. Verifique a internet.");}});
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
            else if(v instanceof Number)ed.putInt(key,((Number)v).intValue());
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
