package com.battleuniverse.game;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Original animated entry gate. Account authentication is required before PLAY. */
public final class EntryGateView extends FrameLayout {
    public interface Actions {
        void authenticate(String username, String password, boolean create);
        void recover(String username, String recoveryCode);
        void rotateRecoveryCode();
        void play();
        void signOut();
    }

    private final LinearLayout panel;
    private final EditText username;
    private final EditText password;
    private final TextView status;
    private final TextView device;
    private final LinearLayout authControls;
    private final LinearLayout signedControls;
    private Actions actions;
    private boolean configured;
    private boolean signedIn;
    private String currentUsername = "";
    private String deviceCode = "";

    public EntryGateView(Context context) {
        super(context);
        setFocusableInTouchMode(true);
        addView(new CosmicBackdrop(context), new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                dp(354), LayoutParams.MATCH_PARENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        scrollParams.rightMargin = dp(18);
        addView(scroll, scrollParams);

        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(20), dp(14), dp(20), dp(14));
        panel.setBackground(roundRect(0xEE0B1B2B, 0xFF2B8193, 22));
        scroll.addView(panel, new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView brand = label(context, "BATTLE UNIVERSE", 23, Color.WHITE, true);
        panel.addView(brand, matchWrap());
        TextView subtitle = label(context, "THE VISION  /  IDENTIDADE DO OPERADOR", 9, 0xFF67E5EB, true);
        subtitle.setPadding(0, dp(3), 0, dp(12));
        panel.addView(subtitle, matchWrap());

        authControls = new LinearLayout(context);
        authControls.setOrientation(LinearLayout.VERTICAL);
        username = new EditText(context);
        styleInput(username, "NICKNAME");
        username.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        password = new EditText(context);
        styleInput(password, "SENHA");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        authControls.addView(username, fieldParams());
        authControls.addView(password, fieldParams());
        Button login = actionButton(context, "ENTRAR", 0xFF1C91A4, 0xFF4FE3E6);
        login.setOnClickListener(v -> submit(false));
        authControls.addView(login, buttonParams());
        Button create = actionButton(context, "CRIAR CONTA", 0xFF263E58, 0xFF4C91A8);
        create.setOnClickListener(v -> submit(true));
        authControls.addView(create, buttonParams());
        Button recovery = textButton(context, "RECUPERAR CONTA COM CÓDIGO");
        recovery.setOnClickListener(v -> showRecoveryDialog());
        authControls.addView(recovery, buttonParams());
        panel.addView(authControls, matchWrap());

        signedControls = new LinearLayout(context);
        signedControls.setOrientation(LinearLayout.VERTICAL);
        TextView welcome = label(context, "IDENTIDADE CONFIRMADA", 14, 0xFF71F0D7, true);
        signedControls.addView(welcome, matchWrap());
        device = label(context, "ID DO DISPOSITIVO: aguardando registro...", 10, 0xFFB6D7E1, true);
        device.setPadding(0, dp(8), 0, dp(8));
        signedControls.addView(device, matchWrap());
        Button play = actionButton(context, "JOGAR  →", 0xFF167E79, 0xFF6BF3D0);
        play.setOnClickListener(v -> { if (actions != null) actions.play(); });
        signedControls.addView(play, buttonParams());
        Button recoveryCode = actionButton(context, "MEU CÓDIGO DE RECUPERAÇÃO", 0xFF263E58, 0xFF4C91A8);
        recoveryCode.setOnClickListener(v -> { if (actions != null) actions.rotateRecoveryCode(); });
        signedControls.addView(recoveryCode, buttonParams());
        Button logout = textButton(context, "SAIR DA CONTA");
        logout.setOnClickListener(v -> { if (actions != null) actions.signOut(); });
        signedControls.addView(logout, buttonParams());
        signedControls.setVisibility(GONE);
        panel.addView(signedControls, matchWrap());

        status = label(context, "Entre ou crie sua conta para continuar.", 10, 0xFFB3C8D3, false);
        status.setPadding(0, dp(10), 0, 0);
        panel.addView(status, matchWrap());

        setAccountState(false, false, "", "Conectando ao serviço de contas...");
        setAlpha(0f);
        setTranslationY(dp(12));
        animate().alpha(1f).translationY(0f).setDuration(650)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    public void setActions(Actions value) { actions = value; }

    public void setAccountState(boolean serverConfigured, boolean authenticated,
                                String name, String message) {
        configured = serverConfigured;
        signedIn = authenticated;
        currentUsername = name == null ? "" : name;
        authControls.setVisibility(authenticated ? GONE : VISIBLE);
        signedControls.setVisibility(authenticated ? VISIBLE : GONE);
        if (authenticated) {
            status.setText("Sessão segura • " + currentUsername);
            status.setTextColor(0xFF71F0D7);
        } else {
            status.setText(message == null ? "" : message);
            String lower = status.getText().toString().toLowerCase();
            status.setTextColor(lower.contains("erro") || lower.contains("inválid")
                    || lower.contains("falha") || lower.contains("indisponível")
                    ? 0xFFFF9A91 : 0xFFB3C8D3);
        }
        if (!configured && !authenticated) {
            username.setEnabled(false);
            password.setEnabled(false);
        } else {
            username.setEnabled(!authenticated);
            password.setEnabled(!authenticated);
        }
    }

    public void setDeviceCode(String code) {
        deviceCode = code == null ? "" : code;
        if (!deviceCode.isEmpty()) device.setText("ID DO DISPOSITIVO  •  " + deviceCode);
    }

    public void showRecoveryCode(String code) {
        if (code == null || code.isEmpty()) return;
        EditText view = new EditText(getContext());
        view.setText(code);
        view.setTextIsSelectable(true);
        view.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(getContext())
                .setTitle("NOVO CÓDIGO DE RECUPERAÇÃO")
                .setMessage("Este código aparece somente agora. Guarde-o fora do aparelho. O código anterior foi invalidado.")
                .setView(view)
                .setPositiveButton("ENTENDI", (dialog, which) -> {})
                .setCancelable(false)
                .show();
    }

    private void submit(boolean create) {
        String name = username.getText().toString().trim().toLowerCase();
        String secret = password.getText().toString();
        if (!configured) { status.setText("Servidor de contas indisponível neste APK."); return; }
        if (!name.matches("[a-z0-9_]{3,18}")) { status.setText("Use nickname de 3 a 18 letras, números ou _."); return; }
        if (secret.length() < 8 || secret.length() > 72) { status.setText("A senha precisa ter de 8 a 72 caracteres."); return; }
        if (actions != null) {
            status.setText(create ? "Criando sua identidade no servidor..." : "Validando sua identidade...");
            actions.authenticate(name, secret, create);
        }
    }

    private void showRecoveryDialog() {
        LinearLayout form = new LinearLayout(getContext());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(8), dp(16), 0);
        EditText name = new EditText(getContext());
        name.setHint("Nickname da conta");
        EditText code = new EditText(getContext());
        code.setHint("Código de recuperação");
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        form.addView(name);
        form.addView(code);
        new AlertDialog.Builder(getContext()).setTitle("RECUPERAR IDENTIDADE")
                .setMessage("Digite o nickname e o código secreto salvo anteriormente.")
                .setView(form)
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("RECUPERAR", (dialog, which) -> {
                    if (actions != null) actions.recover(name.getText().toString(), code.getText().toString());
                }).show();
    }

    private void styleInput(EditText input, String hint) {
        input.setSingleLine(true);
        input.setTextSize(13);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xFF7899AA);
        input.setHint(hint);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackground(roundRect(0xFF102A3B, 0xFF31596A, 10));
    }

    private Button actionButton(Context context, String text, int fill, int edge) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextSize(12);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        button.setAllCaps(false);
        button.setBackground(roundRect(fill, edge, 12));
        return button;
    }

    private Button textButton(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextSize(10);
        button.setTextColor(0xFF9CC7D4);
        button.setAllCaps(false);
        button.setBackgroundColor(Color.TRANSPARENT);
        return button;
    }

    private TextView label(Context context, String text, int size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        return view;
    }

    private GradientDrawable roundRect(int color, int stroke, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        shape.setStroke(dp(1), stroke);
        return shape;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }
    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams p = matchWrap(); p.height = dp(43); p.bottomMargin = dp(7); return p;
    }
    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams p = matchWrap(); p.height = dp(42); p.topMargin = dp(5); return p;
    }
    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class CosmicBackdrop extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final long started = System.currentTimeMillis();
        CosmicBackdrop(Context context) { super(context); setLayerType(View.LAYER_TYPE_HARDWARE, null); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float t = (System.currentTimeMillis() - started) / 1000f;
            paint.setShader(new LinearGradient(0, 0, w, h,
                    new int[]{0xFF030A14, 0xFF0A2031, 0xFF11172C}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint); paint.setShader(null);
            float cx = w * .34f, cy = h * .53f;
            for (int i = 0; i < 5; i++) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(i == 0 ? 2.4f : 1.1f);
                paint.setColor(i == 0 ? 0xAA42E2E8 : 0x332BC8DD);
                float radius = Math.min(w, h) * (.19f + i * .045f)
                        + (float)Math.sin(t * 1.2f + i) * 5f;
                canvas.drawCircle(cx, cy, radius, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x222BC8DD);
            canvas.drawCircle(cx, cy, Math.min(w, h) * .22f, paint);
            // Original operator silhouette: helmet, visor, armor plates and energy core.
            float bob = (float)Math.sin(t * 1.6f) * 4f;
            canvas.save();
            canvas.translate(cx, cy + bob);
            paint.setColor(0xFF10283A);
            canvas.drawRoundRect(-w*.105f, -h*.01f, w*.105f, h*.39f, 28, 28, paint);
            paint.setColor(0xFF183F54);
            canvas.drawRoundRect(-w*.145f, h*.12f, -w*.075f, h*.42f, 18, 18, paint);
            canvas.drawRoundRect(w*.075f, h*.12f, w*.145f, h*.42f, 18, 18, paint);
            paint.setShader(new LinearGradient(-w*.07f, -h*.24f, w*.07f, h*.03f,
                    new int[]{0xFFB7E7E8, 0xFF287E98, 0xFF102C43}, null, Shader.TileMode.CLAMP));
            canvas.drawOval(-w*.075f, -h*.27f, w*.075f, h*.06f, paint);
            paint.setShader(null);
            paint.setColor(0xFF061827);
            canvas.drawRoundRect(-w*.065f, -h*.14f, w*.065f, -h*.035f, 15, 15, paint);
            paint.setColor(0xFF70F5EE);
            canvas.drawRoundRect(-w*.048f, -h*.105f, w*.048f, -h*.075f, 8, 8, paint);
            paint.setColor(0xFF65EAD9);
            canvas.drawCircle(0, h*.055f, 6 + (float)Math.sin(t*4f)*1.5f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setColor(0x995CE3E9);
            canvas.drawArc(-w*.16f, -h*.3f, w*.16f, h*.48f, 210, 120, false, paint);
            canvas.drawArc(-w*.16f, -h*.3f, w*.16f, h*.48f, 30, 120, false, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.restore();
            for (int i = 0; i < 42; i++) {
                float x = (i * 173f + t * (9 + i % 5) * 2f) % w;
                float y = (i * 97f + (float)Math.sin(t + i) * 18f) % h;
                paint.setColor(i % 4 == 0 ? 0xAA66EAF0 : 0x4474AFC2);
                canvas.drawCircle(x, y, i % 5 == 0 ? 2f : 1f, paint);
            }
            paint.setColor(0x6647DCE8);
            canvas.drawRect(0, h*.84f, w, h*.843f, paint);
            postInvalidateDelayed(40);
        }
    }
}
