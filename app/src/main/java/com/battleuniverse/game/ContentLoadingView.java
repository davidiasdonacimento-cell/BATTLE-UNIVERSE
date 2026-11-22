package com.battleuniverse.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/** Cinematic THE VISION setup scene. Real progress stays internal; no fake download meter is shown. */
public final class ContentLoadingView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect=new RectF();
    private long started=System.currentTimeMillis();
    private float realProgress;
    private String status="PREPARANDO SUA PRIMEIRA ENTRADA...";
    private String error="";
    private Runnable retryAction,backAction;
    private float scale=1f,offsetX,offsetY;

    public ContentLoadingView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        setFocusable(true);
    }

    public void setProgress(long done,long all,String message) {
        realProgress=all>0?Math.max(0f,Math.min(1f,(float)done/(float)all)):realProgress;
        status=brandStatus(message);
        error="";
        postInvalidate();
    }

    public void setStatus(String message) {
        status=brandStatus(message);
        postInvalidate();
    }

    /** File-level telemetry is intentionally not exposed as a technical list. */
    public void setFileProgress(String name,int index,int count,long done,long size) {
        if(size>0)realProgress=Math.max(realProgress,Math.min(1f,(float)done/(float)size));
        postInvalidate();
    }

    public void setFailure(String message,Runnable retry,Runnable back) {
        error=message==null?"Não foi possível preparar os recursos.":message;
        status="A CONFIGURAÇÃO PRECISA DE ATENÇÃO";
        retryAction=retry;backAction=back;
        postInvalidate();
    }

    private String brandStatus(String raw) {
        if(raw==null||raw.trim().isEmpty())return status;
        String value=raw.toUpperCase();
        if(value.contains("ANALISANDO"))return "CONSULTANDO A TEMPORADA 02...";
        if(value.contains("DOWNLOAD")||value.contains("BAIXANDO"))return "BAIXANDO O PACOTE DA TEMPORADA 02...";
        if(value.contains("VALIDADO")||value.contains("CONFERINDO")||value.contains("SHA-256"))return "VALIDANDO A INTEGRIDADE DO UNIVERSO...";
        if(value.contains("INSTALANDO")||value.contains("EXTRAINDO"))return "MONTANDO OS RECURSOS DO MUNDO...";
        if(value.contains("CONFIGURANDO")||value.contains("PREPARANDO")||value.contains("PACOTE"))return "SINCRONIZANDO A TEMPORADA 02...";
        if(value.contains("UNIVERSO CONFIGURADO")||value.contains("ABRINDO"))return "ABRINDO OS PORTÕES DO UNIVERSO...";
        return "ALINHANDO OS RECURSOS DO UNIVERSO...";
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w=getWidth(),h=getHeight();
        if(w<=0||h<=0)return;
        scale=Math.min(w/1280f,h/720f);
        offsetX=(w-1280f*scale)*.5f;offsetY=(h-720f*scale)*.5f;
        canvas.save();canvas.translate(offsetX,offsetY);canvas.scale(scale,scale);
        float t=(System.currentTimeMillis()-started)/1000f;
        paint.setShader(new LinearGradient(0,0,1280,720,
            new int[]{0xFF030914,0xFF0A1C30,0xFF11152B},null,Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,1280,720,paint);paint.setShader(null);

        // Deep-space field with slow drifting particles and a luminous horizon.
        for(int i=0;i<56;i++) {
            float x=(i*173f+t*(5+i%6)*2.5f)%1280f;
            float y=(i*97f+(float)Math.sin(t*.6f+i)*32f)%720f;
            paint.setColor(i%5==0?0xAA74EAF0:0x447CAFC3);
            canvas.drawCircle(x,y,i%7==0?2.1f:1.1f,paint);
        }
        paint.setColor(0x3323C9DC);
        canvas.drawOval(120,520,1160,900,paint);
        paint.setStyle(Paint.Style.STROKE);
        for(int i=0;i<5;i++) {
            paint.setStrokeWidth(i==0?2.4f:1.2f);
            paint.setColor(i==0?0xFF54E4E8:0x4435C8DA);
            float radius=105+i*24+(float)Math.sin(t*1.25f+i)*5f;
            canvas.drawCircle(640,260,radius,paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF67F0E9);
        float orbit=1f+(float)Math.sin(t*2.4f)*.08f;
        for(int i=0;i<4;i++) {
            double a=t*(.65+i*.12)+i*Math.PI*.5;
            canvas.drawCircle(640+(float)Math.cos(a)*(150+i*19),
                    260+(float)Math.sin(a)*(150+i*19),3.2f*orbit,paint);
        }
        drawVisionMascot(canvas,t);

        text(canvas,"THE VISION",640,91,42,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD),Paint.Align.CENTER);
        text(canvas,"BATTLE UNIVERSE",640,121,13,0xFF5BE5EA,Typeface.create("sans-serif",Typeface.BOLD),Paint.Align.CENTER);
        text(canvas,"TEMPORADA 02  •  UM UNIVERSO ORIGINAL",640,472,15,0xFFD5EAF0,Typeface.DEFAULT_BOLD,Paint.Align.CENTER);
        text(canvas,status,640,516,13,0xFF82E4E5,Typeface.create("sans-serif",Typeface.BOLD),Paint.Align.CENTER);
        text(canvas,"OPERADORES  •  MAPAS  •  EQUIPAMENTOS  •  EXPERIÊNCIA",640,548,9,0xFF789EAF,Typeface.DEFAULT,Paint.Align.CENTER);

        // Animated light sequence, not a percentage or simulated progress indicator.
        for(int i=0;i<7;i++) {
            float pulse=(float)Math.sin(t*3.1f-i*.62f);
            paint.setColor(pulse>0?0xFF67EDE5:0x553C8C9C);
            canvas.drawCircle(610+i*10,594,2.2f+Math.max(0,pulse)*2.2f,paint);
        }
        text(canvas,"A EXPERIÊNCIA SERÁ INICIADA APÓS A VERIFICAÇÃO DOS RECURSOS",640,652,8,0xFF66899A,Typeface.DEFAULT_BOLD,Paint.Align.CENTER);

        if(!error.isEmpty()) {
            panel(canvas,275,565,350,58,0xFF16475B,0xFF53DDE7);
            panel(canvas,655,565,350,58,0xFF263545,0xFF6F8798);
            text(canvas,"TENTAR NOVAMENTE",450,601,12,Color.WHITE,Typeface.DEFAULT_BOLD,Paint.Align.CENTER);
            text(canvas,"VOLTAR AO MENU",830,601,12,Color.WHITE,Typeface.DEFAULT_BOLD,Paint.Align.CENTER);
            text(canvas,shortText(error,108),640,680,9,0xFFFFB1A5,Typeface.DEFAULT,Paint.Align.CENTER);
        } else {
            postInvalidateDelayed(45);
        }
        canvas.restore();
    }

    private void drawVisionMascot(Canvas canvas,float time) {
        float bob=(float)Math.sin(time*2.1f)*5f;
        float pulse=1f+(float)Math.sin(time*3.2f)*0.035f;
        canvas.save();canvas.translate(640,260+bob);canvas.scale(pulse,pulse);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x5535DDEB);
        canvas.drawCircle(0,0,78+(float)Math.sin(time*2.5f)*5f,paint);
        paint.setShader(new LinearGradient(-42,-48,45,58,
            new int[]{0xFFB9F8FF,0xFF36BFD6,0xFF155078},null,Shader.TileMode.CLAMP));
        rect.set(-52,-60,52,64);canvas.drawRoundRect(rect,34,34,paint);paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3f);paint.setColor(0xFFB9F8FF);
        rect.set(-52,-60,52,64);canvas.drawRoundRect(rect,34,34,paint);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xFF071A2B);
        rect.set(-38,-17,38,24);canvas.drawRoundRect(rect,18,18,paint);
        paint.setColor(0xFF71F6F0);canvas.drawOval(-27,-5,-9,7,paint);canvas.drawOval(9,-5,27,7,paint);
        paint.setColor(Color.WHITE);canvas.drawCircle(-18,-1,3,paint);canvas.drawCircle(18,-1,3,paint);
        paint.setColor(0xFF16456A);rect.set(-24,43,24,57);canvas.drawRoundRect(rect,7,7,paint);
        paint.setColor(0xFF8BF7EF);canvas.drawCircle(0,49,4+(float)Math.sin(time*5f)*1.2f,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2f);paint.setColor(0x9976F5F0);
        canvas.drawArc(-66,-70,66,70,205,130,false,paint);canvas.drawArc(-66,-70,66,70,25,130,false,paint);
        paint.setStyle(Paint.Style.FILL);canvas.restore();
    }

    private String shortText(String value,int max) {
        if(value==null)return "";
        if(value.length()<=max)return value;
        return value.substring(0,max-1)+"…";
    }
    private void text(Canvas c,String value,float x,float y,float size,int color,Typeface face,Paint.Align align) {
        paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setTypeface(face);
        paint.setTextSize(size);paint.setTextAlign(align);paint.setColor(color);
        c.drawText(value,x,y,paint);paint.setTextAlign(Paint.Align.LEFT);
    }
    private void panel(Canvas c,float x,float y,float w,float h,int fill,int edge) {
        rect.set(x,y,x+w,y+h);paint.setColor(fill);c.drawRoundRect(rect,12,12,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.4f);paint.setColor(edge);
        c.drawRoundRect(rect,12,12,paint);paint.setStyle(Paint.Style.FILL);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if(event.getActionMasked()==MotionEvent.ACTION_UP&&!error.isEmpty()) {
            float x=(event.getX()-offsetX)/scale,y=(event.getY()-offsetY)/scale;
            if(y>=550&&y<=635) {
                if(x>=275&&x<=625&&retryAction!=null)retryAction.run();
                else if(x>=655&&x<=1005&&backAction!=null)backAction.run();
                performClick();
            }
            return true;
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
