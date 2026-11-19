package com.battleuniverse.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

public final class HudOverlay extends View {
    private final GameSurfaceView game;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public HudOverlay(Context context, GameSurfaceView game) {
        super(context);
        this.game = game;
        setClickable(false);
        setFocusable(false);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float w = getWidth();
        final float h = getHeight();

        paint.setColor(0xB90A1725);
        rect.set(22, 20, Math.min(w * 0.64f, 520), 118);
        canvas.drawRoundRect(rect, 22, 22, paint);

        paint.setColor(0xFF46DDF4);
        paint.setTextSize(25);
        canvas.drawText("BATTLE UNIVERSE", 44, 54, paint);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(15);
        canvas.drawText("ILHA TROPICAL  •  TREINAMENTO", 44, 82, paint);

        paint.setColor(0xFFE8F8FF);
        paint.setTextSize(17);
        String status = game.getStatusText();
        if (status.length() > 66) status = status.substring(0, 63) + "...";
        canvas.drawText(status, 44, 105, paint);

        float pad = Math.min(w, h) * 0.09f;
        float stickX = pad * 1.08f;
        float stickY = h - pad * 0.88f;
        float stickR = pad * 0.52f;
        drawCircle(canvas, stickX, stickY, stickR, 0x6677CDE0, 0xBBD7F7FF, 3);
        drawCircle(canvas, stickX, stickY, stickR * 0.34f, 0x6687DDF0, 0x99FFFFFF, 2);
        paint.setColor(0xFFEAFBFF);
        paint.setTextSize(13);
        canvas.drawText("MOVER", stickX - 24, stickY + stickR + 24, paint);

        float jumpX = w - pad * 0.85f;
        float jumpY = h - pad * 0.92f;
        float buttonR = pad * 0.42f;
        drawCircle(canvas, jumpX, jumpY, buttonR, 0xBB1686A7, 0xFFDDFBFF, 4);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(17);
        canvas.drawText("PULAR", jumpX - 28, jumpY + 6, paint);

        float collectX = w - pad * 2.00f;
        float collectY = h - pad * 1.95f;
        drawCircle(canvas, collectX, collectY, buttonR * 0.88f, 0xBB236D62, 0xFFDDFBFF, 4);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(14);
        canvas.drawText("COLETAR", collectX - 34, collectY + 5, paint);

        paint.setColor(0xCC071522);
        rect.set(w * 0.41f, h - 47, w * 0.63f, h - 18);
        canvas.drawRoundRect(rect, 12, 12, paint);
        paint.setColor(0xFFE7F7FC);
        paint.setTextSize(12);
        canvas.drawText("ARRASTE À DIREITA PARA GIRAR A CÂMERA", w * 0.42f, h - 28, paint);

        postInvalidateDelayed(450);
    }

    private void drawCircle(Canvas canvas, float x, float y, float radius,
                            int fill, int stroke, float strokeWidth) {
        paint.setColor(fill);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(x, y, radius, paint);
        paint.setColor(stroke);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        canvas.drawCircle(x, y, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }
}
