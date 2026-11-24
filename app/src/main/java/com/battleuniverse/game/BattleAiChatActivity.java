package com.battleuniverse.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.UUID;

/** Account-scoped persistent chat UI for BATTLE AI PRO MAXX. */
public final class BattleAiChatActivity extends Activity {
    private AccountSyncManager account;
    private final ArrayList<JSONObject> conversations = new ArrayList<>();
    private LinearLayout messageList;
    private ScrollView messageScroll;
    private Spinner conversationSpinner;
    private EditText composer;
    private EditText memoryEditor;
    private Button sendButton;
    private Button deleteButton;
    private TextView statusView;
    private boolean suppressSelection;
    private boolean busy;
    private String activeConversationId = "";
    private String pendingRequestId = "";
    private String pendingMessageContent = "";

    private int dp(float value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable surface(int color, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(14));
        if (stroke != 0) shape.setStroke(dp(1), stroke);
        return shape;
    }

    private TextView label(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(String value) {
        Button view = new Button(this);
        view.setText(value);
        view.setAllCaps(false);
        return view;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(Color.rgb(7, 15, 27));
        getWindow().setNavigationBarColor(Color.rgb(7, 15, 27));
        account = new AccountSyncManager(this,
            getSharedPreferences("battle_universe_profile", MODE_PRIVATE), null);
        if (!account.isSignedIn()) {
            Toast.makeText(this, "Entre na sua conta BATTLE UNIVERSE primeiro.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        buildInterface();
        refreshConversations();
        loadMemory();
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.setBackgroundColor(Color.rgb(7, 15, 27));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button("‹");
        back.setTextSize(26);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(52), dp(48)));
        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView title = label("BATTLE AI", 19, Color.WHITE, true);
        TextView subtitle = label("PRO MAXX • MEMÓRIA NA NUVEM", 10, Color.rgb(75, 214, 224), true);
        titleBox.addView(title);
        titleBox.addView(subtitle);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1);
        titleParams.leftMargin = dp(8);
        header.addView(titleBox, titleParams);
        Button quickMode = button("RÁPIDO");
        quickMode.setOnClickListener(v -> showQuickModeDialog());
        header.addView(quickMode, new LinearLayout.LayoutParams(-2, dp(46)));
        Button newChat = button("+ NOVO");
        newChat.setOnClickListener(v -> createConversation());
        header.addView(newChat, new LinearLayout.LayoutParams(-2, dp(46)));
        root.addView(header);

        LinearLayout selector = new LinearLayout(this);
        selector.setGravity(Gravity.CENTER_VERTICAL);
        conversationSpinner = new Spinner(this);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        selector.addView(conversationSpinner, spinnerParams);
        deleteButton = button("APAGAR");
        deleteButton.setOnClickListener(v -> confirmDeleteConversation());
        selector.addView(deleteButton);
        root.addView(selector);

        LinearLayout memoryBox = new LinearLayout(this);
        memoryBox.setOrientation(LinearLayout.VERTICAL);
        memoryBox.setPadding(dp(10), dp(8), dp(10), dp(8));
        memoryBox.setBackground(surface(Color.rgb(17, 34, 51), Color.rgb(40, 91, 112)));
        TextView memoryTitle = label("MEMÓRIA COMPARTILHADA DA CONTA", 11, Color.rgb(96, 224, 221), true);
        memoryBox.addView(memoryTitle);
        TextView memoryHint = label("A IA pode consultar esta memória em qualquer conversa desta conta.", 10,
            Color.rgb(181, 199, 211), false);
        memoryBox.addView(memoryHint);
        memoryEditor = new EditText(this);
        memoryEditor.setTextColor(Color.WHITE);
        memoryEditor.setHintTextColor(Color.rgb(135, 155, 170));
        memoryEditor.setHint("Preferências e informações que você quer que a IA lembre...");
        memoryEditor.setTextSize(12);
        memoryEditor.setMinLines(2);
        memoryEditor.setMaxLines(3);
        memoryEditor.setGravity(Gravity.TOP | Gravity.START);
        memoryEditor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        memoryBox.addView(memoryEditor, new LinearLayout.LayoutParams(-1, dp(68)));
        Button saveMemory = button("SALVAR MEMÓRIA");
        saveMemory.setOnClickListener(v -> saveMemory());
        memoryBox.addView(saveMemory, new LinearLayout.LayoutParams(-1, dp(42)));
        LinearLayout.LayoutParams memoryParams = new LinearLayout.LayoutParams(-1, -2);
        memoryParams.topMargin = dp(5);
        memoryParams.bottomMargin = dp(7);
        root.addView(memoryBox, memoryParams);

        statusView = label("Conectando ao histórico da conta...", 11, Color.rgb(170, 191, 205), false);
        root.addView(statusView);

        messageScroll = new ScrollView(this);
        messageScroll.setFillViewport(true);
        messageList = new LinearLayout(this);
        messageList.setOrientation(LinearLayout.VERTICAL);
        messageList.setPadding(dp(2), dp(8), dp(2), dp(8));
        messageScroll.addView(messageList, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1);
        scrollParams.topMargin = dp(4);
        scrollParams.bottomMargin = dp(6);
        root.addView(messageScroll, scrollParams);

        LinearLayout composerRow = new LinearLayout(this);
        composerRow.setGravity(Gravity.CENTER_VERTICAL);
        composer = new EditText(this);
        composer.setTextColor(Color.WHITE);
        composer.setHintTextColor(Color.rgb(135, 155, 170));
        composer.setHint("Escreva sua mensagem...");
        composer.setTextSize(14);
        composer.setMinLines(1);
        composer.setMaxLines(4);
        composer.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        composer.setBackground(surface(Color.rgb(17, 31, 46), Color.rgb(48, 87, 108)));
        LinearLayout.LayoutParams composerParams = new LinearLayout.LayoutParams(0, -2, 1);
        composerParams.rightMargin = dp(6);
        composerRow.addView(composer, composerParams);
        sendButton = button("ENVIAR");
        sendButton.setOnClickListener(v -> sendMessage());
        composerRow.addView(sendButton, new LinearLayout.LayoutParams(-2, dp(54)));
        root.addView(composerRow);
        setContentView(root);
        enterImmersiveMode();

        conversationSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (suppressSelection || position < 0 || position >= conversations.size()) return;
                JSONObject selected = conversations.get(position);
                String nextId = selected.optString("id", "");
                if (!nextId.isEmpty() && !nextId.equals(activeConversationId)) {
                    activeConversationId = nextId;
                    loadMessages();
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void showQuickModeDialog() {
        EditText question = new EditText(this);
        question.setHint("Faça uma pergunta rápida...");
        question.setMinLines(2);
        question.setMaxLines(4);
        question.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(16), dp(8), dp(16), 0);
        box.addView(question, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("BATTLE AI • MODO RÁPIDO")
            .setMessage("Resposta direta, sem criar uma conversa no histórico.")
            .setView(box)
            .setNegativeButton("CANCELAR", null)
            .setPositiveButton("PERGUNTAR", null)
            .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = question.getText().toString().trim();
            if (text.length() < 2) {
                question.setError("Digite uma pergunta.");
                return;
            }
            dialog.dismiss();
            showStatus("Modo rápido: aguardando a IA...");
            account.askCoach(text, (answer, error) -> {
                if (isFinishing()) return;
                if (error != null) {
                    showStatus(error);
                    return;
                }
                showStatus("Resposta rápida recebida.");
                new AlertDialog.Builder(this)
                    .setTitle("BATTLE AI • RESPOSTA RÁPIDA")
                    .setMessage(answer)
                    .setPositiveButton("FECHAR", null)
                    .show();
            });
        }));
        dialog.show();
    }

    private void refreshConversations() {
        account.loadAiConversations((result, error) -> {
            if (isFinishing()) return;
            if (error != null || result == null) {
                showStatus(error == null ? "Não foi possível carregar as conversas." : error);
                return;
            }
            JSONArray rows = result.optJSONArray("conversations");
            conversations.clear();
            if (rows != null) for (int i = 0; i < rows.length(); i++) {
                JSONObject item = rows.optJSONObject(i);
                if (item != null) conversations.add(item);
            }
            if (conversations.isEmpty()) {
                createConversation();
                return;
            }
            ArrayList<String> titles = new ArrayList<>();
            int selected = 0;
            for (int i = 0; i < conversations.size(); i++) {
                JSONObject item = conversations.get(i);
                titles.add(item.optString("title", "Conversa"));
                if (item.optString("id", "").equals(activeConversationId)) selected = i;
            }
            suppressSelection = true;
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, titles);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            conversationSpinner.setAdapter(adapter);
            conversationSpinner.setSelection(selected);
            suppressSelection = false;
            String chosen = conversations.get(selected).optString("id", "");
            if (!chosen.equals(activeConversationId)) {
                activeConversationId = chosen;
                loadMessages();
            }
            deleteButton.setEnabled(true);
        });
    }

    private void createConversation() {
        if (busy) return;
        showStatus("Criando uma conversa segura...");
        account.createAiConversation("Nova conversa", (result, error) -> {
            if (isFinishing()) return;
            if (error != null || result == null) {
                showStatus(error == null ? "Falha ao criar conversa." : error);
                return;
            }
            JSONObject item = result.optJSONObject("conversation");
            if (item == null) {
                showStatus("O servidor não retornou a conversa criada.");
                return;
            }
            activeConversationId = item.optString("id", "");
            refreshConversations();
            renderMessages(new JSONArray());
            showStatus("Conversa salva na nuvem da sua conta.");
        });
    }

    private void loadMessages() {
        if (activeConversationId.isEmpty()) return;
        showStatus("Carregando histórico salvo...");
        account.loadAiMessages(activeConversationId, (result, error) -> {
            if (isFinishing()) return;
            if (error != null || result == null) {
                showStatus(error == null ? "Falha ao carregar mensagens." : error);
                return;
            }
            JSONArray rows = result.optJSONArray("messages");
            renderMessages(rows == null ? new JSONArray() : rows);
            showStatus("Histórico sincronizado • memória compartilhada ativa.");
        });
    }

    private void renderMessages(JSONArray rows) {
        messageList.removeAllViews();
        if (rows.length() == 0) {
            TextView empty = label("Olá! Sou a BATTLE AI PRO MAXX. Suas conversas ficam salvas na sua conta e podem ser retomadas em outro aparelho.", 14,
                Color.rgb(207, 224, 235), false);
            empty.setPadding(dp(14), dp(16), dp(14), dp(16));
            empty.setBackground(surface(Color.rgb(17, 34, 51), Color.rgb(43, 82, 103)));
            messageList.addView(empty);
        }
        for (int i = 0; i < rows.length(); i++) {
            JSONObject message = rows.optJSONObject(i);
            if (message == null) continue;
            boolean user = "user".equals(message.optString("role", ""));
            LinearLayout bubble = new LinearLayout(this);
            bubble.setOrientation(LinearLayout.VERTICAL);
            bubble.setPadding(dp(12), dp(9), dp(12), dp(9));
            bubble.setBackground(surface(user ? Color.rgb(22, 74, 91) : Color.rgb(22, 35, 51),
                user ? Color.rgb(48, 159, 177) : Color.rgb(57, 88, 111)));
            TextView who = label(user ? "VOCÊ" : "BATTLE AI PRO MAXX", 10,
                user ? Color.rgb(125, 231, 238) : Color.rgb(145, 220, 190), true);
            TextView text = label(message.optString("content", ""), 14, Color.WHITE, false);
            text.setTextIsSelectable(true);
            bubble.addView(who);
            bubble.addView(text);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(5);
            params.bottomMargin = dp(5);
            params.leftMargin = user ? dp(28) : 0;
            params.rightMargin = user ? 0 : dp(28);
            messageList.addView(bubble, params);
            if (user && i == rows.length() - 1) {
                String requestId = message.optString("client_message_id", "");
                String pendingText = message.optString("content", "");
                if (!requestId.isEmpty()) {
                    Button retry = button("TENTAR GERAR RESPOSTA");
                    retry.setOnClickListener(v -> retryPendingMessage(requestId, pendingText));
                    LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(-1, dp(42));
                    retryParams.bottomMargin = dp(8);
                    messageList.addView(retry, retryParams);
                }
            }
        }
        messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void sendMessage() {
        if (busy) return;
        String value = composer.getText().toString().trim();
        if (value.isEmpty()) return;
        if (activeConversationId.isEmpty()) {
            showStatus("Crie ou selecione uma conversa primeiro.");
            return;
        }
        if (!pendingRequestId.isEmpty() && !pendingMessageContent.equals(value)) {
            showStatus("Existe uma mensagem pendente. Reenvie o mesmo texto para evitar duplicidade.");
            composer.setText(pendingMessageContent);
            composer.setSelection(composer.length());
            return;
        }
        if (pendingRequestId.isEmpty()) {
            pendingRequestId = UUID.randomUUID().toString();
            pendingMessageContent = value;
        }
        submitAiMessage(pendingRequestId, pendingMessageContent);
    }

    private void retryPendingMessage(String requestId, String content) {
        if (busy || activeConversationId.isEmpty() || requestId.isEmpty()) return;
        pendingRequestId = requestId;
        pendingMessageContent = content;
        submitAiMessage(requestId, content);
    }

    private void submitAiMessage(String requestId, String content) {
        busy = true;
        sendButton.setEnabled(false);
        composer.setText("");
        showStatus("BATTLE AI PRO MAXX está preparando a resposta...");
        account.sendAiMessage(activeConversationId, content, requestId, (result, error) -> {
            busy = false;
            sendButton.setEnabled(true);
            if (isFinishing()) return;
            if (error != null || result == null) {
                showStatus(error == null ? "A mensagem ficou pendente. Use TENTAR GERAR RESPOSTA." : error);
                composer.setText(pendingMessageContent);
                composer.setSelection(composer.length());
                loadMessages();
                return;
            }
            pendingRequestId = "";
            pendingMessageContent = "";
            loadMessages();
            refreshConversations();
        });
    }

    private void confirmDeleteConversation() {
        if (activeConversationId.isEmpty() || busy) return;
        new AlertDialog.Builder(this)
            .setTitle("APAGAR CONVERSA")
            .setMessage("Esta conversa e suas mensagens serão apagadas permanentemente da sua conta. A memória compartilhada não será apagada.")
            .setNegativeButton("CANCELAR", null)
            .setPositiveButton("APAGAR", (dialog, which) -> {
                account.deleteAiConversation(activeConversationId, (result, error) -> {
                    if (isFinishing()) return;
                    if (error != null) {
                        showStatus(error);
                        return;
                    }
                    activeConversationId = "";
                    refreshConversations();
                });
            }).show();
    }

    private void loadMemory() {
        account.loadAiMemory((result, error) -> {
            if (isFinishing()) return;
            if (error != null || result == null) {
                showStatus(error == null ? "Memória indisponível." : error);
                return;
            }
            memoryEditor.setText(result.optString("memory_text", ""));
        });
    }

    private void saveMemory() {
        if (busy) return;
        String value = memoryEditor.getText().toString();
        if (value.length() > 4000) {
            showStatus("A memória aceita até 4.000 caracteres.");
            return;
        }
        busy = true;
        account.saveAiMemory(value, (result, error) -> {
            busy = false;
            if (isFinishing()) return;
            showStatus(error == null ? "Memória da conta salva com segurança." : error);
        });
    }

    private void showStatus(String value) {
        if (statusView != null) statusView.setText(value == null ? "" : value);
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }

    @Override protected void onResume() {
        super.onResume();
        enterImmersiveMode();
    }

    @Override protected void onDestroy() {
        if (account != null) account.close();
        super.onDestroy();
    }
}
