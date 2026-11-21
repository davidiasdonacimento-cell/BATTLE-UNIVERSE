package com.battleuniverse.game;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Authenticated matchmaking client. Uses the same encrypted Android Keystore
 * session as AccountSyncManager; access tokens are never copied to preferences.
 */
public final class MultiplayerApiClient {
    public interface Listener {
        void onQueueUpdate(JSONObject state, String message);
    }

    private final AccountSessionStore session;
    private final String baseUrl;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile Listener listener;
    private volatile boolean closed;
    private volatile boolean polling;
    private volatile String ticketId = "";
    private volatile String matchId = "";
    private long pollGeneration;

    public MultiplayerApiClient(Context context, Listener listener) {
        session = new AccountSessionStore(context);
        this.listener = listener;
        String url = BuildConfig.ACCOUNT_API_BASE_URL == null ? "" : BuildConfig.ACCOUNT_API_BASE_URL.trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        baseUrl = url;
    }

    public boolean hasValidSession() {
        return !closed && baseUrl.startsWith("https://")
                && !session.getToken().isEmpty()
                && session.getExpiresAt() > System.currentTimeMillis() / 1000L;
    }

    public void joinQueue(int mode, int mapId) {
        if (!hasValidSession()) {
            publish(new JSONObject(), "Entre na conta para procurar uma partida online.");
            return;
        }
        if (mode < 0 || mode > 2 || mapId < 0 || mapId > 7) {
            publish(new JSONObject(), "Modo ou mapa online inválido.");
            return;
        }
        final long generation = ++pollGeneration;
        polling = true;
        ticketId = "";
        matchId = "";
        publish(new JSONObject(), "Conectando à fila de partidas...");
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("mode", mode == 0 ? "battle_royale" : mode == 1 ? "duo" : "squad");
                body.put("map_id", mapId);
                body.put("region", "sa-east");
                JSONObject result = request("POST", "/v1/matches/queue", body);
                ticketId = result.optString("ticket_id", "");
                if ("matched".equals(result.optString("state"))) {
                    matchId = result.optString("match_id", "");
                    publish(result, "Partida encontrada. Carregando lobby...");
                    fetchMatch(generation);
                } else if (!ticketId.isEmpty()) {
                    JSONObject queued = new JSONObject();
                    queued.put("state", "queued");
                    queued.put("ticket_id", ticketId);
                    queued.put("remaining_seconds", 30);
                    publish(queued, "Procurando jogadores humanos. A busca dura até 30 segundos.");
                    poll(generation);
                } else {
                    throw new IllegalStateException("O servidor não retornou o ticket da fila.");
                }
            } catch (Exception error) {
                polling = false;
                publish(new JSONObject(), safeMessage(error));
            }
        });
    }

    private void poll(long generation) {
        if (closed || !polling || generation != pollGeneration || ticketId.isEmpty()) return;
        io.execute(() -> {
            try {
                JSONObject result = request("GET", "/v1/matches/queue/" + ticketId, null);
                if ("matched".equals(result.optString("state"))) {
                    matchId = result.optString("match_id", "");
                    polling = false;
                    publish(result, "Sala encontrada. Preparando participantes...");
                    fetchMatch(generation);
                    return;
                }
                publish(result, "Buscando humanos • " + result.optInt("remaining_seconds", 0) + "s restantes.");
                main.postDelayed(() -> poll(generation), 1800L);
            } catch (Exception error) {
                if (generation == pollGeneration) {
                    polling = false;
                    publish(new JSONObject(), "Falha ao consultar a fila: " + safeMessage(error));
                }
            }
        });
    }

    private void fetchMatch(long generation) {
        if (matchId.isEmpty() || closed || generation != pollGeneration) return;
        io.execute(() -> {
            try {
                JSONObject result = request("GET", "/v1/matches/" + matchId, null);
                result.put("state", "matched");
                publish(result, "Lobby online conectado ao servidor.");
            } catch (Exception error) {
                publish(new JSONObject(), "Sala encontrada, mas não foi possível carregar o lobby: " + safeMessage(error));
            }
        });
    }

    public void cancelQueue() {
        final String ticket = ticketId;
        ++pollGeneration;
        polling = false;
        io.execute(() -> {
            try {
                if (!ticket.isEmpty()) request("DELETE", "/v1/matches/queue/" + ticket, null);
                ticketId = "";
                matchId = "";
                publish(new JSONObject(), "Você saiu da fila.");
            } catch (Exception error) {
                publish(new JSONObject(), "Não foi possível cancelar a fila: " + safeMessage(error));
            }
        });
    }

    private JSONObject request(String method, String path, JSONObject body) throws Exception {
        String token = session.getToken();
        if (token.isEmpty()) throw new IllegalStateException("Sessão da conta não encontrada.");
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        }
        try {
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            if (stream != null) try (InputStream input = stream) {
                byte[] chunk = new byte[4096];
                int count;
                while ((count = input.read(chunk)) != -1) buffer.write(chunk, 0, count);
            }
            String response = buffer.toString(StandardCharsets.UTF_8.name());
            JSONObject json = response.isEmpty() ? new JSONObject() : new JSONObject(response);
            if (code < 200 || code >= 300) {
                if (code == 401) throw new IllegalStateException("Sessão expirada. Entre novamente na conta.");
                throw new IllegalStateException(json.optString("detail", "Erro HTTP " + code + " na API de partidas."));
            }
            return json;
        } finally {
            connection.disconnect();
        }
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? "Servidor de partidas indisponível." : message;
    }

    private void publish(JSONObject state, String message) {
        Listener target = listener;
        if (target == null || closed) return;
        main.post(() -> {
            Listener current = listener;
            if (current != null && !closed) current.onQueueUpdate(state, message);
        });
    }

    public void close() {
        closed = true;
        polling = false;
        ++pollGeneration;
        listener = null;
        io.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }
}
