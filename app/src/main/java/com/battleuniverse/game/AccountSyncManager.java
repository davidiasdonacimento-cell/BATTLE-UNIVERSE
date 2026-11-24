package com.battleuniverse.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import org.json.JSONArray;
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


    private String getOwnerInstallId() {
        String id=sessionPrefs.getString("owner_install_id","");
        if(id.matches("[A-Za-z0-9._:-]{16,64}"))return id;
        id=java.util.UUID.randomUUID().toString();
        if(!sessionPrefs.edit().putString("owner_install_id",id).commit())
            throw new IllegalStateException("Não foi possível registrar este aparelho controlador.");
        return id;
    }
    private void clearOwnerToken() {
        sessionPrefs.edit().remove("owner_token_iv").remove("owner_token_cipher").commit();
    }
    private String getOwnerToken() {
        try {
            String ivText=sessionPrefs.getString("owner_token_iv","");
            String cipherText=sessionPrefs.getString("owner_token_cipher","");
            if(ivText.isEmpty()||cipherText.isEmpty())return "";
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,getOrCreateKey(),new GCMParameterSpec(128,Base64.decode(ivText,Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(cipherText,Base64.NO_WRAP)),StandardCharsets.UTF_8);
        } catch(Exception e) { return ""; }
    }
    private void saveOwnerToken(String token)throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,getOrCreateKey());
        byte[] encrypted=cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        if(!sessionPrefs.edit().putString("owner_token_iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
            .putString("owner_token_cipher",Base64.encodeToString(encrypted,Base64.NO_WRAP)).commit())
            throw new Exception("Não foi possível salvar a autorização administrativa.");
    }
    public void openOwnerMailController() {
        String token=getOwnerToken();
        if(token.isEmpty()) showOwnerPairDialog(); else showOwnerAccountSearch(token);
    }
    private void showOwnerPairDialog() {
        EditText code=new EditText(activity);
        code.setSingleLine(true);code.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        code.setHint("Código de autorização do proprietário");
        LinearLayout box=new LinearLayout(activity);box.setPadding(36,12,36,0);box.addView(code);
        new AlertDialog.Builder(activity).setTitle("BATTLE UNIVERSE • AUTORIZAR CONTROLADOR")
            .setMessage("Digite aqui o código configurado no Render. Ele será usado apenas para autorizar este aparelho.")
            .setView(box).setNegativeButton("CANCELAR",null).setPositiveButton("AUTORIZAR",(d,w)->{
                String value=code.getText().toString().trim();code.setText("");
                if(value.length()<32){ownerMessage("Código de autorização inválido.");return;}
                worker.execute(()->{
                    try {
                        JSONObject response=api("/v1/owner/pair","POST",null,new JSONObject()
                            .put("pairing_code",value).put("install_id",getOwnerInstallId())
                            .put("device_label","Android • BATTLE UNIVERSE"));
                        String owner=response.optString("access_token","");
                        if(owner.isEmpty())throw new Exception("O servidor não retornou a sessão administrativa.");
                        saveOwnerToken(owner);
                        main.post(()->showOwnerAccountSearch(owner));
                    } catch(Exception e){main.post(()->ownerMessage("Autorização recusada: "+cleanError(e)));}
                });
            }).show();
    }
    private void ownerMessage(String message) {
        if(!activity.isFinishing())new AlertDialog.Builder(activity).setTitle("CONTROLADOR DE CORREIO")
            .setMessage(message).setPositiveButton("OK",null).show();
    }
    private void showOwnerAccountSearch(String token) {
        EditText search=new EditText(activity);search.setSingleLine(true);
        search.setHint("Nickname da conta (vazio = listar recentes)");
        LinearLayout box=new LinearLayout(activity);box.setPadding(36,8,36,0);box.addView(search);
        new AlertDialog.Builder(activity).setTitle("SELECIONAR CONTA")
            .setMessage("Pesquise uma conta ou deixe vazio para carregar as contas recentes.")
            .setView(box).setNegativeButton("CANCELAR",null).setPositiveButton("PESQUISAR",(d,w)->
                loadOwnerAccounts(token,search.getText().toString().trim())).show();
    }
    private void loadOwnerAccounts(String token,String query) {
        worker.execute(()->{
            try {
                String path="/v1/owner/accounts?limit=100";
                if(query!=null&&!query.isEmpty())path+="&search="+java.net.URLEncoder.encode(query,"UTF-8");
                JSONObject response=api(path,"GET",token,null);
                JSONArray accounts=response.optJSONArray("accounts");
                java.util.ArrayList<String> labels=new java.util.ArrayList<>();
                java.util.ArrayList<Integer> ids=new java.util.ArrayList<>();
                labels.add("TODAS AS CONTAS • ENVIO GLOBAL");ids.add(0);
                if(accounts!=null)for(int i=0;i<accounts.length();i++){
                    JSONObject a=accounts.optJSONObject(i);if(a==null)continue;
                    labels.add(a.optString("username","conta")+"  •  ID "+a.optInt("id"));
                    ids.add(a.optInt("id"));
                }
                main.post(()->{
                    if(labels.size()==1){ownerMessage("Nenhuma conta encontrada.");return;}
                    String[] items=labels.toArray(new String[0]);
                    new AlertDialog.Builder(activity).setTitle("DESTINATÁRIO DO CORREIO")
                        .setItems(items,(dialog,which)->showOwnerCompose(token,ids.get(which),labels.get(which)))
                        .setNegativeButton("VOLTAR",(dialog,which)->showOwnerAccountSearch(token))
                        .show();
                });
            }catch(Exception e){
                String message=cleanError(e);
                main.post(()->{
                    if(message.contains("401")||message.contains("403")){
                        clearOwnerToken();
                        ownerMessage("A autorização deste controlador expirou ou foi revogada. Autorize novamente.");
                        showOwnerPairDialog();
                    }else ownerMessage("Falha ao carregar contas: "+message);
                });
            }
        });
    }
    private void showOwnerCompose(String token,int recipientId,String recipientLabel) {
        LinearLayout form=new LinearLayout(activity);form.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(16*activity.getResources().getDisplayMetrics().density);form.setPadding(pad,8,pad,0);
        EditText title=new EditText(activity);title.setSingleLine(true);title.setHint("Título (2 a 80 caracteres)");
        EditText body=new EditText(activity);body.setHint("Mensagem (2 a 1200 caracteres)");body.setMinLines(3);
        EditText diamonds=new EditText(activity);diamonds.setSingleLine(true);diamonds.setInputType(InputType.TYPE_CLASS_NUMBER);diamonds.setHint("Diamantes por conta (0 a 100000)");diamonds.setText("0");
        form.addView(title);form.addView(body);form.addView(diamonds);
        new AlertDialog.Builder(activity).setTitle("✉ NOVO CORREIO")
            .setMessage("Destinatário: "+recipientLabel).setView(form)
            .setNegativeButton("CANCELAR",null).setPositiveButton("ENVIAR",(d,w)->{
                String t=title.getText().toString().trim(),b=body.getText().toString().trim();
                String ds=diamonds.getText().toString().trim();
                int amount;try{amount=Integer.parseInt(ds.isEmpty()?"0":ds);}catch(Exception ex){ownerMessage("Quantidade de diamantes inválida.");return;}
                if(t.length()<2||t.length()>80||b.length()<2||b.length()>1200||amount<0||amount>100000){ownerMessage("Confira título, mensagem e quantidade de diamantes.");return;}
                worker.execute(()->{
                    try {
                        JSONObject payload=new JSONObject().put("title",t).put("body",b).put("diamonds",amount)
                            .put("recipient_player_id",recipientId==0?JSONObject.NULL:recipientId)
                            .put("request_id",java.util.UUID.randomUUID().toString());
                        JSONObject result=api("/v1/owner/mail/send","POST",token,payload);
                        main.post(()->ownerMessage("🟢 VERDE — correio enviado. Contas alcançadas: "+result.optInt("recipients",0)));
                    }catch(Exception ex){main.post(()->ownerMessage("❌ DEU RED! "+cleanError(ex)));}
                });
            }).show();
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
                JSONObject account=api("/v1/accounts/me","GET",token,null);
                JSONObject player=account.optJSONObject("player");
                if(player!=null) {
                    JSONObject serverProfile=player.optJSONObject("profile");
                    if(serverProfile!=null)applyProfile(serverProfile);
                }
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

    private void aiChatRequest(String path,String method,JSONObject body,ApiActionListener callback) {
        String token=getToken();
        if(token.isEmpty()) {
            if(callback!=null)main.post(()->callback.onResult(null,"Entre na sua conta para usar o BATTLE AI PRO MAXX."));
            return;
        }
        worker.execute(()->{
            try {
                JSONObject result=api(path,method,token,body);
                if(callback!=null)main.post(()->callback.onResult(result,null));
            } catch(Exception e) {
                String message=cleanError(e);
                if(callback!=null)main.post(()->callback.onResult(null,message));
            }
        });
    }

    public void loadAiConversations(ApiActionListener callback) {
        aiChatRequest("/v1/ai/conversations?limit=50","GET",null,callback);
    }

    public void createAiConversation(String title,ApiActionListener callback) {
        try {
            aiChatRequest("/v1/ai/conversations","POST",
                new JSONObject().put("title",title==null?"Nova conversa":title),callback);
        } catch(JSONException e) {
            if(callback!=null)main.post(()->callback.onResult(null,"Não foi possível criar a conversa."));
        }
    }

    public void loadAiMessages(String conversationId,ApiActionListener callback) {
        aiChatRequest("/v1/ai/conversations/"+conversationId+"/messages?limit=100","GET",null,callback);
    }

    public void sendAiMessage(String conversationId,String content,String requestId,ApiActionListener callback) {
        try {
            JSONObject body=new JSONObject().put("content",content).put("client_message_id",requestId);
            aiChatRequest("/v1/ai/conversations/"+conversationId+"/messages","POST",body,callback);
        } catch(JSONException e) {
            if(callback!=null)main.post(()->callback.onResult(null,"Não foi possível preparar a mensagem."));
        }
    }

    public void deleteAiConversation(String conversationId,ApiActionListener callback) {
        aiChatRequest("/v1/ai/conversations/"+conversationId,"DELETE",null,callback);
    }

    public void loadAiMemory(ApiActionListener callback) {
        aiChatRequest("/v1/ai/memory","GET",null,callback);
    }

    public void saveAiMemory(String memoryText,ApiActionListener callback) {
        try {
            aiChatRequest("/v1/ai/memory","PUT",
                new JSONObject().put("memory_text",memoryText==null?"":memoryText),callback);
        } catch(JSONException e) {
            if(callback!=null)main.post(()->callback.onResult(null,"Não foi possível preparar a memória."));
        }
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
        sessionPrefs.edit().remove("token_cipher").remove("token_iv").remove("username").commit();
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
                    sessionPrefs.edit().remove("token_cipher").remove("token_iv").remove("username").commit();
                    username="";
                    publish(false,"","Sua sessão foi recusada pelo servidor. Entre novamente.");
                } else if(msg.contains("403")) {
                    // A conta pode estar suspensa ou sem permissão. Preserve o token para
                    // não apagar o login do jogador por uma resposta de autorização.
                    // HTTP 403 não significa que o jogador saiu da conta.
                    // Preserve o estado autenticado e informe a restrição sem expulsá-lo do perfil.
                    publish(true,username,"Conta conectada, mas o servidor bloqueou esta operação: "+msg);
                } else {
                    // Render pode estar iniciando ou a rede pode oscilar. Preserve a sessão.
                    publish(!getToken().isEmpty(),username,
                        "Falha temporária ao validar a sessão. Sua conta continua salva neste aparelho. "+msg);
                }
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
        boolean saved=sessionPrefs.edit()
            .putString("token_iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
            .putString("token_cipher",Base64.encodeToString(encrypted,Base64.NO_WRAP))
            .putString("username",name).commit();
        if(!saved)throw new Exception("Não foi possível gravar a sessão no armazenamento seguro.");
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
            // Não apague a sessão criptografada por uma falha transitória do Android Keystore.
            // Uma falha de leitura não deve transformar automaticamente a conta em logout.
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
        if(m.contains("HTTP 404"))return "O servidor ainda não reconhece esta função do BATTLE AI PRO MAXX (HTTP 404). A atualização da API precisa ser publicada no Render.";
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
