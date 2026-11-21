package com.battleuniverse.admin;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import android.util.Base64;

/** Owner-only game settings client. No account-management or admin username/password UI. */
public final class AdminActivity extends Activity {
    private static final String API = "https://battle-universe-accounts.onrender.com";
    private static final String PREFS = "bu_owner_secure";
    private static final String TOKEN_KEY = "owner_token";
    private static final String ANDROID_KEY_ALIAS = "battle_universe_owner_v1";
    private String token = "";
    private LinearLayout root;
    private TextView status;
    private EditText gameName, characterJson, pairingCode;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        token = readToken();
        if (token.isEmpty()) showPairing(); else showSettings();
    }

    private void base(String title) {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22, 26, 22, 30); root.setBackgroundColor(Color.rgb(10,16,30));
        scroll.addView(root);
        TextView heading = new TextView(this); heading.setText(title);
        heading.setTextColor(Color.WHITE); heading.setTextSize(24); heading.setGravity(Gravity.CENTER);
        heading.setPadding(0, 12, 0, 22); root.addView(heading);
        status = new TextView(this); status.setTextColor(Color.rgb(170,195,220));
        status.setTextSize(14); status.setPadding(4,8,4,14); root.addView(status);
        setContentView(scroll);
    }

    private EditText field(String hint, boolean multiline) {
        EditText e = new EditText(this); e.setSingleLine(!multiline); e.setHint(hint);
        e.setTextColor(Color.WHITE); e.setHintTextColor(Color.GRAY); e.setPadding(14,10,14,10);
        root.addView(e, new LinearLayout.LayoutParams(-1,-2)); return e;
    }

    private Button button(String text, Runnable action) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false);
        root.addView(b, new LinearLayout.LayoutParams(-1,-2));
        b.setOnClickListener(v -> action.run()); return b;
    }

    private void message(String text) { runOnUiThread(() -> status.setText(text)); }

    private void showPairing() {
        base("BATTLE UNIVERSE ADMIN");
        TextView note = new TextView(this);
        note.setText("Central privada de configurações do jogo. Digite o código de ativação fornecido pelo proprietário do servidor. Ele será usado somente nesta ativação e não será salvo.");
        note.setTextColor(Color.LTGRAY); note.setTextSize(15); root.addView(note);
        pairingCode = field("Código privado de ativação", false);
        pairingCode.setInputType(129);
        button("ATIVAR ESTE APARELHO", () -> pair(pairingCode.getText().toString()));
        status.setText("Acesso protegido. Sem código válido, nenhuma configuração pode ser alterada.");
    }

    private void pair(String code) {
        if (code == null || code.trim().length() < 32) {
            status.setText("Código inválido. Confira o código privado.");
            return;
        }
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject(); body.put("pairing_code", code.trim());
                JSONObject result = request("POST", "/v1/owner/pair", body, "");
                String received = result.getString("access_token");
                saveToken(received);
                token = received;
                runOnUiThread(this::showSettings);
            } catch (Exception e) {
                message("Não foi possível ativar: " + safe(e.getMessage()));
            }
        }).start();
    }

    private void showSettings() {
        base("CENTRAL DE CONFIGURAÇÕES");
        TextView info = new TextView(this);
        info.setText("Aparelho autorizado • alterações protegidas pelo servidor");
        info.setTextColor(Color.rgb(80,190,255)); info.setTextSize(13); root.addView(info);
        gameName = field("Nome exibido do jogo", false);
        characterJson = field("Nomes dos personagens por ID — JSON", true);
        characterJson.setMinLines(5);
        button("CARREGAR CONFIGURAÇÕES", this::loadSettings);
        button("SALVAR CONFIGURAÇÕES", this::saveSettings);
        button("BLOQUEAR ESTE APARELHO", this::lockDevice);
        status.setText("Conectando ao servidor...");
        loadSettings();
    }

    private void loadSettings() {
        new Thread(() -> {
            try {
                JSONObject r = request("GET", "/v1/owner/settings", null, token);
                JSONObject names = r.optJSONObject("character_names");
                if (names == null) names = new JSONObject();
                final String game = r.optString("game_name", "BATTLE UNIVERSE");
                final String json = names.toString(2);
                runOnUiThread(() -> {
                    gameName.setText(game); characterJson.setText(json);
                    status.setText("Configurações carregadas do PostgreSQL. 🟢");
                });
            } catch (Exception e) {
                String error = safe(e.getMessage());
                if (error.contains("401") || error.contains("403")) {
                    clearToken(); token = "";
                    runOnUiThread(this::showPairing);
                } else message("Erro ao carregar: " + error);
            }
        }).start();
    }

    private void saveSettings() {
        final String name = gameName.getText().toString().trim();
        final String namesText = characterJson.getText().toString().trim();
        if (name.length() < 2 || name.length() > 40) {
            status.setText("O nome do jogo precisa ter de 2 a 40 caracteres."); return;
        }
        new Thread(() -> {
            try {
                JSONObject names = new JSONObject(namesText.isEmpty() ? "{}" : namesText);
                JSONObject body = new JSONObject(); body.put("game_name", name); body.put("character_names", names);
                JSONObject result = request("PUT", "/v1/owner/settings", body, token);
                if (!result.optBoolean("saved", false)) throw new IOException("O servidor não confirmou o salvamento.");
                message("Configurações salvas no PostgreSQL. 🟢 VERDE");
            } catch (Exception e) { message("Erro ao salvar: " + safe(e.getMessage())); }
        }).start();
    }

    private void lockDevice() {
        clearToken(); token = ""; showPairing();
    }

    private JSONObject request(String method, String path, JSONObject body, String auth) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(API + path).openConnection();
        c.setRequestMethod(method); c.setConnectTimeout(12000); c.setReadTimeout(15000);
        c.setRequestProperty("Accept", "application/json");
        if (auth != null && !auth.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + auth);
        if (body != null) {
            c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream out = c.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        }
        try {
            int code = c.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (in != null) try (InputStream stream = in) {
                byte[] buffer = new byte[4096]; int n;
                while ((n = stream.read(buffer)) != -1) bytes.write(buffer, 0, n);
            }
            String raw = bytes.toString(StandardCharsets.UTF_8.name());
            JSONObject result = raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
            if (code < 200 || code >= 300)
                throw new IOException("HTTP " + code + ": " + result.optString("detail", "falha na API"));
            return result;
        } finally { c.disconnect(); }
    }

    private String safe(String value) { return value == null ? "falha de comunicação" : value; }

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
