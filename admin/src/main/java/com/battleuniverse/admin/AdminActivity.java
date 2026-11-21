package com.battleuniverse.admin;

import android.app.Activity;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native Android administration client. All privileged decisions are enforced by the API. */
public final class AdminActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private static final String PREFS = "bu_owner_secure";
    private static final String TOKEN_KEY = "owner_token";
    private static final String ANDROID_KEY_ALIAS = "battle_universe_owner_v1";
    private String token = "";
    private LinearLayout root, content;
    private TextView status;
    private EditText search, gameName, characterJson;
    private String api;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        api = "https://battle-universe-accounts.onrender.com";
        token = readToken();
        if (token.isEmpty()) showSecurityGate(); else showDashboard();
    }

    private void base(String title) {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 24, 20, 28); root.setBackgroundColor(Color.rgb(10,16,30));
        scroll.addView(root);
        TextView heading = new TextView(this); heading.setText(title);
        heading.setTextColor(Color.WHITE); heading.setTextSize(25); heading.setGravity(Gravity.CENTER);
        heading.setPadding(0, 12, 0, 22); root.addView(heading);
        status = new TextView(this); status.setTextColor(Color.rgb(170,195,220));
        status.setTextSize(13); status.setPadding(4,8,4,12); root.addView(status);
        setContentView(scroll);
    }

    private EditText field(String hint, boolean secret) {
        EditText e = new EditText(this); e.setSingleLine(!hint.toLowerCase().contains("json"));
        e.setHint(hint); e.setTextColor(Color.WHITE); e.setHintTextColor(Color.GRAY);
        e.setPadding(14,10,14,10);
        if (secret) e.setInputType(129);
        root.addView(e, new LinearLayout.LayoutParams(-1,-2));
        return e;
    }

    private Button button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false);
        root.addView(b, new LinearLayout.LayoutParams(-1,-2));
        b.setOnClickListener(v -> action.run()); return b;
    }

    private void message(String text) { runOnUiThread(() -> status.setText(text)); }
    @FunctionalInterface
    private interface Work { void run() throws Exception; }

    private void work(String label, Work job) {
        message(label);
        io.execute(() -> { try { job.run(); } catch (Exception e) { String error = safe(e.getMessage()); if (error.contains("HTTP 401") || error.contains("HTTP 403")) { token = ""; clearToken(); runOnUiThread(this::showSecurityGate); } else message("Erro: " + error); } });
    }
    private String safe(String s) { return s == null ? "falha de comunicação" : s; }

    private void showSecurityGate() {
        base("BATTLE UNIVERSE ADMIN");
        TextView note = new TextView(this);
        note.setText("Acesso privado do proprietário. Responda à pergunta de segurança para continuar.");
        note.setTextColor(Color.LTGRAY); note.setTextSize(15); root.addView(note);
        EditText answer = field("Quantos anos tem?", false);
        answer.setInputType(2);
        button("CONTINUAR", () -> {
            if ("1000".equals(answer.getText().toString().trim())) showPairing();
            else status.setText("Resposta incorreta. Acesso não liberado.");
        });
        status.setText("Proteção local ativa. O servidor também exige autorização privada.");
    }

    private void showPairing() {
        base("ATIVAÇÃO PRIVADA");
        TextView note = new TextView(this);
        note.setText("Digite o código privado do proprietário. O código não será salvo no aparelho.");
        note.setTextColor(Color.LTGRAY); note.setTextSize(15); root.addView(note);
        EditText code = field("Código privado de ativação", true);
        button("ATIVAR ESTE APARELHO", () -> work("Validando código privado...", () -> {
            JSONObject body = new JSONObject(); body.put("pairing_code", code.getText().toString().trim());
            JSONObject result = request("POST", "/v1/owner/pair", body);
            token = result.getString("access_token");
            saveToken(token);
            runOnUiThread(this::showDashboard);
        }));
        status.setText("Sem um código válido, nenhuma operação administrativa será autorizada.");
    }

    private void showDashboard() {
        base("CENTRAL ADMINISTRATIVA");
        button("ATUALIZAR RESUMO", this::loadOverview);
        search = field("Pesquisar nickname", false);
        button("PESQUISAR CONTAS", () -> loadAccounts(search.getText().toString()));
        button("LISTAR CONTAS", () -> loadAccounts(""));
        TextView section = label("CONFIGURAÇÕES GLOBAIS");
        gameName = field("Nome exibido do jogo", false);
        characterJson = field("Nomes por ID em JSON (ex.: {\"caio\":\"Caio\"})", false);
        button("CARREGAR CONFIGURAÇÕES", this::loadSettings);
        button("SALVAR NOME DO JOGO E PERSONAGENS", this::saveSettings);
        button("VER AUDITORIA", this::loadAudit);
        button("BLOQUEAR ESTE APARELHO", () -> { clearToken(); token = ""; showSecurityGate(); });
        loadOverview(); loadSettings();
    }

    private TextView label(String text) {
        TextView t = new TextView(this); t.setText(text); t.setTextColor(Color.rgb(80,190,255));
        t.setTextSize(17); t.setPadding(0,18,0,8); root.addView(t); return t;
    }

    private void loadOverview() {
        work("Consultando servidor...", () -> {
            JSONObject r=request("GET","/v1/owner/overview",null);
            message("Contas: "+r.optInt("accounts_total")+" | Suspensas: "+r.optInt("accounts_suspended")+
                    " | Encerradas: "+r.optInt("accounts_deleted")+" | Admin: "+r.optString("admin"));
        });
    }

    private void loadSettings() {
        work("Carregando configurações...", () -> {
            JSONObject r=request("GET","/v1/owner/settings",null);
            JSONObject names=r.optJSONObject("character_names"); if(names==null) names=new JSONObject();
            JSONObject finalNames=names;
            runOnUiThread(() -> { gameName.setText(r.optString("game_name","BATTLE UNIVERSE"));
                characterJson.setText(finalNames.toString()); });
            message("Configurações carregadas.");
        });
    }

    private void saveSettings() {
        work("Salvando configurações...", () -> {
            JSONObject names=new JSONObject(characterJson.getText().toString());
            JSONObject body=new JSONObject(); body.put("game_name",gameName.getText().toString().trim());
            body.put("character_names",names);
            request("PUT","/v1/owner/settings",body); message("Configurações salvas no PostgreSQL.");
        });
    }

    private void loadAccounts(String term) {
        work("Buscando contas...", () -> {
            String path="/v1/owner/accounts?limit=60&offset=0";
            if(term!=null&&!term.trim().isEmpty()) path+="&search="+URLEncoder.encode(term.trim(),"UTF-8");
            JSONObject r=request("GET",path,null); JSONArray rows=r.optJSONArray("accounts");
            runOnUiThread(() -> renderAccounts(rows==null?new JSONArray():rows));
            message("Contas carregadas: "+(rows==null?0:rows.length()));
        });
    }

    private void renderAccounts(JSONArray rows) {
        if(content!=null) root.removeView(content);
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(-1,-2));
        for(int i=0;i<rows.length();i++) {
            JSONObject a=rows.optJSONObject(i); if(a==null) continue;
            int id=a.optInt("id"); String username=a.optString("username");
            String state=a.optString("state"); String character=a.optString("character_name");
            long diamonds=a.optLong("diamonds",0);\n            TextView item=new TextView(this); item.setText("#"+id+"  "+username+"\nPersonagem: "+character+"\nDiamantes: "+String.format(java.util.Locale.US,"%,d",diamonds)+"\nEstado: "+state);
            item.setTextColor(Color.WHITE); item.setTextSize(15); item.setPadding(12,14,12,8);
            content.addView(item);
            Button wallet=new Button(this); wallet.setText("GERENCIAR DIAMANTES / HISTÓRICO"); wallet.setAllCaps(false); content.addView(wallet);\n            wallet.setOnClickListener(v->showWalletDialog(id,username));\n            Button edit=new Button(this); edit.setText("EDITAR NICKNAME / PERSONAGEM"); edit.setAllCaps(false);
            content.addView(edit); edit.setOnClickListener(v->editAccount(id,username,character));
            Button stateButton=new Button(this); stateButton.setText(state.equals("suspended")?"REATIVAR CONTA":"SUSPENDER CONTA");
            stateButton.setAllCaps(false); content.addView(stateButton);
            stateButton.setOnClickListener(v->setState(id,state.equals("suspended")?"active":"suspended"));
            Button delete=new Button(this); delete.setText("EXCLUIR CONTA"); delete.setAllCaps(false); content.addView(delete);
            delete.setOnClickListener(v->confirmDelete(id,username));
            View divider=new View(this); divider.setBackgroundColor(Color.DKGRAY);
            content.addView(divider,new LinearLayout.LayoutParams(-1,2));
        }
    }

    private void showWalletDialog(int id,String username) {
        work("Consultando carteira de "+username+"...",()->{
            JSONObject wallet=request("GET","/v1/owner/accounts/"+id+"/wallet",null);
            JSONArray tx=wallet.optJSONArray("transactions");
            StringBuilder history=new StringBuilder("Saldo atual: "+wallet.optLong("diamonds",0)+" diamantes\\n\\nÚLTIMAS OPERAÇÕES\\n");
            if(tx!=null)for(int i=0;i<tx.length();i++){
                JSONObject row=tx.optJSONObject(i); if(row==null)continue;
                long delta=row.optLong("delta");
                history.append(delta>=0?"+":"").append(delta).append(" ◆  ")
                    .append(row.optString("reason")).append("  → ")
                    .append(row.optLong("balance_after")).append("\\n");
            }
            String text=history.toString();
            runOnUiThread(()->new AlertDialog.Builder(this).setTitle("CARTEIRA • "+username)
                .setMessage(text).setPositiveButton("ADICIONAR", (d,w)->adjustWallet(id,username,"add"))
                .setNeutralButton("RETIRAR", (d,w)->adjustWallet(id,username,"remove"))
                .setNegativeButton("FECHAR",null).show());
        });
    }

    private void adjustWallet(int id,String username,String action) {
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(18,4,18,4);
        EditText amount=new EditText(this); amount.setHint("Quantidade de diamantes"); amount.setInputType(2); box.addView(amount);
        EditText reason=new EditText(this); reason.setHint("Motivo obrigatório"); box.addView(reason);
        String title=action.equals("add")?"Adicionar diamantes":"Retirar diamantes";
        new AlertDialog.Builder(this).setTitle(title+" • "+username).setView(box)
            .setNegativeButton("Cancelar",null).setPositiveButton("CONFIRMAR",(d,w)->work("Atualizando carteira...",()->{
                long value;
                try{value=Long.parseLong(amount.getText().toString().trim());}
                catch(Exception e){throw new IOException("Quantidade inválida.");}
                if(value<=0)throw new IOException("Informe uma quantidade maior que zero.");
                String why=reason.getText().toString().trim();
                if(why.length()<3)throw new IOException("Informe o motivo da operação.");
                JSONObject body=new JSONObject();
                body.put("operation_id",java.util.UUID.randomUUID().toString());
                body.put("action",action); body.put("diamonds",value); body.put("reason",why);
                JSONObject result=request("POST","/v1/owner/accounts/"+id+"/wallet",body);
                message("Carteira atualizada: "+result.optLong("diamonds")+" diamantes. Operação auditada.");
                loadAccounts(search==null?"":search.getText().toString());
            })).show();
    }

    private void editAccount(int id,String oldUser,String oldCharacter) {
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(18,4,18,4);
        EditText nick=new EditText(this); nick.setHint("Novo nickname"); nick.setText(oldUser); box.addView(nick);
        EditText character=new EditText(this); character.setHint("Nome de exibição do personagem"); character.setText(oldCharacter); box.addView(character);
        new AlertDialog.Builder(this).setTitle("Editar conta #"+id).setView(box)
            .setNegativeButton("Cancelar",null).setPositiveButton("Salvar",(d,w)->work("Atualizando conta...",()->{
                JSONObject body=new JSONObject(); body.put("username",nick.getText().toString().trim());
                body.put("character_name",character.getText().toString().trim());
                request("PATCH","/v1/owner/accounts/"+id,body); message("Conta atualizada."); loadAccounts(search.getText().toString());
            })).show();
    }

    private void setState(int id,String state) {
        work("Atualizando estado...",()->{
            JSONObject body=new JSONObject(); body.put("state",state);
            body.put("reason",state.equals("suspended")?"Suspensão administrativa":"Reativada pelo administrador");
            request("PATCH","/v1/owner/accounts/"+id,body); message("Estado atualizado."); loadAccounts(search.getText().toString());
        });
    }

    private void confirmDelete(int id,String username) {
        new AlertDialog.Builder(this).setTitle("Excluir "+username+"?")
            .setMessage("A conta será encerrada, o acesso bloqueado e o perfil limpo. O registro mínimo fica preservado na auditoria.")
            .setNegativeButton("Cancelar",null).setPositiveButton("EXCLUIR",(d,w)->work("Encerrando conta...",()->{
                request("DELETE","/v1/owner/accounts/"+id,null); message("Conta encerrada."); loadAccounts(search.getText().toString());
            })).show();
    }

    private void loadAudit() {
        work("Consultando auditoria...",()->{
            JSONObject r=request("GET","/v1/owner/audit?limit=50",null);
            JSONArray events=r.optJSONArray("events"); StringBuilder out=new StringBuilder("ÚLTIMAS OPERAÇÕES\n");
            if(events!=null) for(int i=0;i<events.length();i++) { JSONObject e=events.optJSONObject(i);
                out.append("#").append(e.optInt("id")).append("  ").append(e.optString("action"))
                   .append("  alvo=").append(e.optString("target")).append("\n"); }
            String text=out.toString(); runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Auditoria").setMessage(text).setPositiveButton("Fechar",null).show());
            message("Auditoria carregada.");
        });
    }

    private JSONObject request(String method,String path,JSONObject body)throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(api+path).openConnection();
        c.setRequestMethod(method); c.setConnectTimeout(12000); c.setReadTimeout(15000);
        c.setRequestProperty("Accept","application/json");
        if(!token.isEmpty()) c.setRequestProperty("Authorization","Bearer "+token);
        if(body!=null) { c.setDoOutput(true); c.setRequestProperty("Content-Type","application/json; charset=utf-8");
            try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));} }
        try {
            int code=c.getResponseCode(); InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            if(in!=null) try(InputStream stream=in){byte[] b=new byte[4096];int n;while((n=stream.read(b))!=-1)bytes.write(b,0,n);}
            String text=bytes.toString(StandardCharsets.UTF_8.name()); JSONObject result=text.isEmpty()?new JSONObject():new JSONObject(text);
            if(code<200||code>=300) throw new IOException(result.optString("detail","HTTP "+code));
            return result;
        } finally { c.disconnect(); }
    }

    @Override protected void onDestroy() { super.onDestroy(); io.shutdownNow(); }

    private SecretKey getKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (ks.containsAlias(ANDROID_KEY_ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(ANDROID_KEY_ALIAS, null)).getSecretKey();
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ANDROID_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }

    private void saveToken(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getKey());
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        String packed = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" +
                Base64.encodeToString(encrypted, Base64.NO_WRAP);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(TOKEN_KEY, packed).apply();
    }

    private String readToken() {
        try {
            String packed = getSharedPreferences(PREFS, MODE_PRIVATE).getString(TOKEN_KEY, "");
            if (packed.isEmpty()) return "";
            String[] parts = packed.split(":", 2);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception e) { clearToken(); return ""; }
    }

    private void clearToken() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(TOKEN_KEY).apply();
    }

}
