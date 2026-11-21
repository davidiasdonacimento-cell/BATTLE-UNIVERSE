package com.battleuniverse.game;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** AES-GCM encrypted account session backed by Android Keystore. */
final class AccountSessionStore {
    private static final String PREFS="battle_universe_secure_session", BLOB="encrypted_session_v1";
    private static final String ALIAS="battle_universe_account_session_aes";
    private final SharedPreferences prefs;
    AccountSessionStore(Context c){prefs=c.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    synchronized void save(String token,String username,long expiresAt)throws Exception{
        JSONObject j=new JSONObject();j.put("token",token);j.put("username",username);j.put("expires_at",expiresAt);
        byte[] packed=encrypt(j.toString().getBytes(StandardCharsets.UTF_8));
        if(!prefs.edit().putString(BLOB,Base64.encodeToString(packed,Base64.NO_WRAP)).commit())
            throw new IllegalStateException("Could not persist account session");
    }
    synchronized String getToken(){return read().optString("token","");}
    synchronized String getUsername(){return read().optString("username","");}
    synchronized long getExpiresAt(){return read().optLong("expires_at",0L);}
    synchronized void clear(){prefs.edit().remove(BLOB).commit();}
    private JSONObject read(){
        String v=prefs.getString(BLOB,"");if(v==null||v.isEmpty())return new JSONObject();
        try{
            byte[] p=Base64.decode(v,Base64.NO_WRAP);if(p.length<=12)throw new IllegalStateException();
            byte[] iv=new byte[12],enc=new byte[p.length-12];System.arraycopy(p,0,iv,0,12);System.arraycopy(p,12,enc,0,enc.length);
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
            return new JSONObject(new String(c.doFinal(enc),StandardCharsets.UTF_8));
        }catch(Exception e){clear();return new JSONObject();}
    }
    private byte[] encrypt(byte[] plain)throws Exception{
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
        byte[] iv=c.getIV(),enc=c.doFinal(plain),out=new byte[iv.length+enc.length];
        System.arraycopy(iv,0,out,0,iv.length);System.arraycopy(enc,0,out,iv.length,enc.length);return out;
    }
    private SecretKey key()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(ks.containsAlias(ALIAS))return(SecretKey)ks.getKey(ALIAS,null);
        KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setRandomizedEncryptionRequired(true).build());return g.generateKey();
    }
}
