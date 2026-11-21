package com.battleuniverse.game;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;

import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Google login + cloud save.
 *
 * The local SharedPreferences remain the offline cache. A Google account
 * identifies the player's cloud document, so the same progress can be restored
 * after installing another build of the same game.
 */
public final class AccountSyncManager implements SharedPreferences.OnSharedPreferenceChangeListener {
    public interface Listener {
        void onAccountState(boolean firebaseConfigured, boolean signedIn,
                            String email, String message);
    }

    private static final String COLLECTION = "players";
    private static final String USERNAME_DOMAIN = "@accounts.battleuniverse.app";
    private static final String[] PROFILE_KEYS = {
        "nickname", "xp", "diamonds", "coins", "coins_wallet_initialized",
        "profile_schema_version", "economy_ledger", "daily_reward_day", "code_100k_redeemed",
        "mode", "map", "character", "quality", "music", "effects",
        "look_sensitivity", "sensitivity_general", "sensitivity_aim",
        "shop_frame_neon", "shop_emblem_bloob",
        "shop_title_champion", "selected_shirt", "selected_boots", "selected_set",
        "invert_camera_y", "show_minimap", "haptics", "online_mode",
        "local_friend_1", "event_season_claimed", "mail_welcome_claimed",
        "achievement_500", "achievement_1500", "achievement_3000",
        "missions_day", "mission_matches", "mission_eliminations", "mission_crystals",
        "mission_claimed_0", "mission_claimed_1", "mission_claimed_2",
        "royale_tier_claimed_0", "royale_tier_claimed_1", "royale_tier_claimed_2",
        "royale_tier_claimed_3", "royale_tier_claimed_4", "royale_tier_claimed_5",
        "royale_spin_count", "royale_last_reward",
        "last_match_reward_coins", "last_match_reward_diamonds", "last_match_reward_xp",
        "wear_shirt_tactical", "wear_shirt_neon", "wear_shirt_frost", "wear_shirt_desert",
        "wear_boots_combat", "wear_boots_neon", "wear_boots_frost", "wear_boots_gold",
        "wear_set_aurora", "wear_set_champion", "wear_set_orbit", "wear_set_desert"
    };

    private final Activity activity;
    private final SharedPreferences prefs;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Executor mainExecutor = command -> mainHandler.post(command);
    private final Runnable saveRunnable = this::saveProfile;

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private CredentialManager credentialManager;
    private boolean configured;
    private boolean applyingCloud;
    private boolean prefsListenerRegistered;
    private boolean closed;
    private String statusMessage = "Crie uma conta BATTLE UNIVERSE para guardar seu progresso.";

    public AccountSyncManager(Activity activity, SharedPreferences prefs, Listener listener) {
        this.activity = activity;
        this.prefs = prefs;
        this.listener = listener;
        initializeFirebase();
    }

    private void initializeFirebase() {
        try {
            // Firebase is the account/cloud backend; Google sign-in is not required.
            if (FirebaseApp.getApps(activity).isEmpty()) {
                configured = false;
                publish(false, "", "Servidor de contas não configurado neste APK.");
                return;
            }
            FirebaseApp app = FirebaseApp.getInstance();
            auth = FirebaseAuth.getInstance(app);
            firestore = FirebaseFirestore.getInstance(app);
            configured = true;
            publish(false, "", "Crie uma conta ou entre com nome e senha.");
        } catch (Exception e) {
            configured = false;
            publish(false, "", "Não foi possível iniciar o serviço de conta.");
        }
    }

    public boolean isSignedIn() {
        return configured && auth != null && auth.getCurrentUser() != null;
    }

    public boolean isConfigured() {
        return configured;
    }

    /** Restores the Firebase session automatically after an app update/restart. */
    public void restoreExistingSession() {
        if (!configured || closed) return;
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            publish(true, "", "Crie uma conta ou entre com nome e senha para sincronizar.");
            return;
        }
        loadCloudProfile(user);
    }

    public void authenticateUsername(String rawUsername, String password, boolean createAccount) {
        if (!configured || auth == null || firestore == null) {
            publish(false, "", "Servidor de contas não configurado neste APK.");
            return;
        }
        String username = rawUsername == null ? "" : rawUsername.trim().toLowerCase(java.util.Locale.ROOT);
        if (!username.matches("[a-z0-9_]{3,18}")) {
            publish(true, "", "Use 3 a 18 letras, números ou _.");
            return;
        }
        if (password == null || password.length() < 8 || password.length() > 72) {
            publish(true, "", "A senha precisa ter de 8 a 72 caracteres.");
            return;
        }
        String internalEmail = username + USERNAME_DOMAIN;
        publish(true, username, createAccount ? "Criando sua conta..." : "Entrando na conta...");
        if (createAccount) {
            auth.createUserWithEmailAndPassword(internalEmail, password)
                .addOnCompleteListener(activity, task -> finishUsernameAuth(task, username));
        } else {
            auth.signInWithEmailAndPassword(internalEmail, password)
                .addOnCompleteListener(activity, task -> finishUsernameAuth(task, username));
        }
    }

    private void finishUsernameAuth(com.google.android.gms.tasks.Task<AuthResult> task, String username) {
        if (closed) return;
        if (!task.isSuccessful()) {
            publish(true, "", "Não foi possível entrar/criar. Confira o nome, a senha e a conexão.");
            return;
        }
        prefs.edit().putString("account_username", username).apply();
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) loadCloudProfile(user);
    }

    public void signOut() {
        if (auth != null) auth.signOut();
        if (prefsListenerRegistered) {
            prefs.unregisterOnSharedPreferenceChangeListener(this);
            prefsListenerRegistered = false;
        }
        publish(configured, "", "Você saiu da conta. O progresso local continua neste aparelho.");
    }

    public String getUsername() {
        return prefs.getString("account_username", "");
    }

    public void signInWithGoogle() {
        if (!configured || credentialManager == null) {
            publish(false, "", "Falta configurar o Firebase e o cliente OAuth do jogo.");
            return;
        }

        int resourceId = activity.getResources().getIdentifier(
            "default_web_client_id", "string", activity.getPackageName());
        if (resourceId == 0) {
            publish(false, "", "O cliente OAuth Web não está no google-services.json.");
            return;
        }

        try {
            GetGoogleIdOption googleOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(activity.getString(resourceId))
                .build();

            GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build();

            publish(true, "", "Abrindo a seleção da conta Google...");
            credentialManager.getCredentialAsync(
                activity,
                request,
                new CancellationSignal(),
                mainExecutor,
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override public void onResult(GetCredentialResponse response) {
                        handleCredential(response.getCredential());
                    }

                    @Override public void onError(GetCredentialException error) {
                        publish(true, "", "Login cancelado ou indisponível. Tente novamente.");
                    }
                }
            );
        } catch (Exception e) {
            publish(true, "", "Não foi possível abrir o login Google.");
        }
    }

    private void handleCredential(Credential credential) {
        if (!(credential instanceof CustomCredential)
                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                    .equals(credential.getType())) {
            publish(true, "", "O Google não retornou uma credencial compatível.");
            return;
        }

        try {
            GoogleIdTokenCredential googleCredential =
                GoogleIdTokenCredential.createFrom(((CustomCredential) credential).getData());
            AuthCredential firebaseCredential =
                GoogleAuthProvider.getCredential(googleCredential.getIdToken(), null);

            auth.signInWithCredential(firebaseCredential)
                .addOnCompleteListener(activity, task -> {
                    if (closed) return;
                    if (!task.isSuccessful()) {
                        publish(true, "", "Falha ao validar a conta Google. Confira a configuração OAuth.");
                        return;
                    }
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null) loadCloudProfile(user);
                });
        } catch (Exception e) {
            publish(true, "", "Não foi possível validar a conta Google.");
        }
    }

    private DocumentReference profileDocument(FirebaseUser user) {
        return firestore.collection(COLLECTION).document(user.getUid());
    }

    private void loadCloudProfile(FirebaseUser user) {
        if (closed || user == null) return;
        publish(true, getUsername(), "Buscando seu progresso salvo na nuvem...");

        profileDocument(user).get()
            .addOnSuccessListener(activity, snapshot -> {
                if (closed) return;

                if (snapshot.exists()) {
                    applyCloudProfile(snapshot);
                    registerPrefsListener();
                    publish(true, getUsername(), "Progresso recuperado da nuvem.");
                } else {
                    // First Google login: preserve and upload the current local profile.
                    registerPrefsListener();
                    publish(true, getUsername(), "Criando seu primeiro salvamento na nuvem...");
                    saveProfile();
                }
            })
            .addOnFailureListener(activity, error -> {
                if (!closed) {
                    publish(true, user.getEmail(),
                        "Conta conectada, mas não consegui baixar o progresso. Verifique a internet e as regras Firestore.");
                }
            });
    }

    private void applyCloudProfile(DocumentSnapshot snapshot) {
        Map<String, Object> data = snapshot.getData();
        if (data == null) return;

        applyingCloud = true;
        SharedPreferences.Editor editor = prefs.edit();

        for (String key : PROFILE_KEYS) {
            Object value = data.get(key);
            if (value == null) continue;

            if (value instanceof String) {
                editor.putString(key, (String) value);
            } else if ("xp".equals(key) || "diamonds".equals(key) || "coins".equals(key)
                    || "daily_reward_day".equals(key) || "missions_day".equals(key)
                    || "match_wins".equals(key) || "match_kills".equals(key)
                    || "matches_played".equals(key) || "last_match_reward_coins".equals(key)
                    || "last_match_reward_diamonds".equals(key) || "last_match_reward_xp".equals(key)) {
                if (value instanceof Number) editor.putLong(key, ((Number) value).longValue());
            } else if ("mode".equals(key) || "map".equals(key) || "character".equals(key)
                    || "quality".equals(key) || "look_sensitivity".equals(key)
                    || "selected_shirt".equals(key) || "selected_boots".equals(key)
                    || "selected_set".equals(key) || "profile_schema_version".equals(key)
                    || "mission_matches".equals(key) || "mission_eliminations".equals(key)
                    || "mission_crystals".equals(key) || "royale_spin_count".equals(key)) {
                if (value instanceof Number) editor.putInt(key, ((Number) value).intValue());
            } else if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            }
        }

        editor.commit();
        applyingCloud = false;
    }

    private void registerPrefsListener() {
        if (prefsListenerRegistered || closed) return;
        prefs.registerOnSharedPreferenceChangeListener(this);
        prefsListenerRegistered = true;
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (closed || applyingCloud || !isSignedIn()) return;
        for (String allowed : PROFILE_KEYS) {
            if (allowed.equals(key)) {
                mainHandler.removeCallbacks(saveRunnable);
                mainHandler.postDelayed(saveRunnable, 900L);
                return;
            }
        }
    }

    /** Manually retries a cloud save from the profile page. */
    public void syncNow() {
        if (!configured) {
            publish(false, "", "Falta configurar o Firebase para salvar na nuvem.");
            return;
        }
        if (!isSignedIn()) {
            publish(configured, "", "Entre com nome e senha para sincronizar seu progresso.");
            return;
        }
        saveProfile();
    }

    private Map<String, Object> collectProfile() {
        Map<String, ?> all = prefs.getAll();
        Map<String, Object> profile = new HashMap<>();
        profile.put("schemaVersion", 2);
        for (String key : PROFILE_KEYS) {
            Object value = all.get(key);
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                profile.put(key, value);
            }
        }
        return profile;
    }

    private void saveProfile() {
        if (closed || !isSignedIn()) return;
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        Map<String, Object> profile = collectProfile();
        profile.put("username", getUsername());
        profile.put("displayName", user.getDisplayName() == null ? "" : user.getDisplayName());
        profile.put("updatedAt", FieldValue.serverTimestamp());

        profileDocument(user).set(profile, SetOptions.merge())
            .addOnSuccessListener(activity, unused -> {
                if (!closed) publish(true, getUsername(), "Conta BATTLE UNIVERSE sincronizada.");
            })
            .addOnFailureListener(activity, error -> {
                if (!closed) publish(true, getUsername(),
                    "Não foi possível enviar o progresso. Ele continua salvo localmente.");
            });
    }

    private void publish(boolean ready, String email, String message) {
        statusMessage = message == null ? "" : message;
        if (listener == null || closed) return;
        boolean signed = isSignedIn();
        String visibleEmail = email == null ? "" : email;
        mainHandler.post(() -> {
            if (!closed) listener.onAccountState(ready, signed, visibleEmail, statusMessage);
        });
    }

    public void close() {
        closed = true;
        mainHandler.removeCallbacks(saveRunnable);
        if (prefsListenerRegistered) {
            prefs.unregisterOnSharedPreferenceChangeListener(this);
            prefsListenerRegistered = false;
        }
    }
}
