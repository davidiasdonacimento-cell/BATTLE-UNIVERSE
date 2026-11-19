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
import java.util.Locale;

public final class ContentLoadingView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect=new RectF();
    private float progress=0f,scale=1f,offsetX,offsetY;
    private long downloaded=0,total=0,started=System.currentTimeMillis();
    private String status="PREPARANDO CONEXÃO SEGURA...";
    private String error="";
    private Runnable retryAction,continueAction;

    public ContentLoadingView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        setFocusable(true);
    }

    public void setProgress(long done,long all,String message) {
        downloaded=Math.max(0,done);total=Math.max(0,all);
        progress=all>0?Math.max(0f,Math.min(1f,(float)done/(float)all)):0f;
        status=message==null?"BAIXANDO DADOS DO JOGO...":message;
        error="";
        postInvalidate();
    }
    public void setFailure(String message,Runnable retry,Runnable continueBase) {
        error=message==null?"Não foi possível concluir a instalação.":message;
        status="NÃO FOI POSSÍVEL INSTALAR O PACOTE";
        retryAction=retry;continueAction=continueBase;
        postInvalidate();
    }
    public boolean hasError(){return !error.isEmpty();}

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w=getWidth(),h=getHeight();
        if(w<=0||h<=0)return;
        scale=Math.min(w/1280f,h/720f);
        offsetX=(w-1280f*scale)*.5f;offsetY=(h-720f*scale)*.5f;
        canvas.save();canvas.translate(offsetX,offsetY);canvas.scale(scale,scale);
        paint.setShader(new LinearGradient(0,0,1280,720,
            new int[]{0xFF050D19,0xFF0A1C30,0xFF10142B},null,Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,1280,720,paint);paint.setShader(null);

        float t=(System.currentTimeMillis()-started)/1000f;
        paint.setColor(0x1636C8DA);
        for(int i=0;i<26;i++) {
            float x=(i*173f+t*(7+i%4)*3f)%1280f;
            float y=(i*97f+(float)Math.sin(t*.7f+i)*38f)%720f;
            canvas.drawCircle(x,y,1.3f+(i%4)*.65f,paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        for(int i=0;i<3;i++) {
            paint.setStrokeWidth(i==0?2.2f:1.2f);
            paint.setColor(i==0?0xFF38D5E4:0x4438D5E4);
            float radius=84+i*18+(float)Math.sin(t*1.5f+i)*4f;
            canvas.drawCircle(640,248,radius,paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF66F0F0);canvas.drawCircle(640,248,7,paint);
        text(canvas,"BATTLE",555,105,39,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(canvas,"UNIVERSE",690,105,39,0xFF54DCE9,Typeface.create("sans-serif-black",Typeface.BOLD));
        text(canvas,"EXPANSÃO DE CONTEÚDO  •  TEMPORADA 01",640,390,17,0xFFD4EAF2,Typeface.DEFAULT_BOLD,Paint.Align.CENTER);
        text(canvas,"Modelos 3D  •  Texturas 2K  •  Mapas  •  Áudio ambiente",640,420,12,0xFF88AFC1,Typeface.DEFAULT,Paint.Align.CENTER);

        rect.set(345,460,935,472);
        paint.setColor(0xFF1B3448);canvas.drawRoundRect(rect,8,8,paint);
        rect.set(345,460,345+590*progress,472);
        paint.setShader(new LinearGradient(345,460,935,472,0xFF36B9D0,0xFF69F0D2,Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect,8,8,paint);paint.setShader(null);
        text(canvas,String.format(Locale.US,"%d%%",(int)(progress*100)),640,510,27,Color.WHITE,Typeface.create("sans-serif-black",Typeface.BOLD),Paint.Align.CENTER);
        text(canvas,status,640,548,12,0xFF9FCCD9,Typeface.DEFAULT_BOLD,Paint.Align.CENTER);
        String size=total>0?String.format(Locale.US,"%.1f MB / %.1f MB",downloaded/1048576f,total/1048576f):"AGUARDANDO SERVIDOR";
        text(canvas,size,640,576,10,0xFF7898AA,Typeface.DEFAULT,Paint.Align.CENTER);
        if(!error.isEmpty()) {
            panel(canvas,275,600,350,58,0xFF16475B,0xFF53DDE7);
            panel(canvas,655,600,350,58,0xFF263545,0xFF6F8798);
            text(canvas,"TENTAR NOVAMENTE",450,636,13,Color.WHITE,Typeface.DEFAULT_BOLD,Paint.Align.CENTER);
            text(canvas,"ABRIR JOGO BASE",830,636,13,Color.WHITE,Typeface.DEFAULT_BOLD,Paint.Align.CENTER);
            text(canvas,error,640,688,10,0xFFFFB1A5,Typeface.DEFAULT,Paint.Align.CENTER);
        } else {
            text(canvas,"OS ARQUIVOS SÃO CONFERIDOS ANTES DE SEREM ATIVADOS.",640,640,10,0xFF648DA1,Typeface.DEFAULT_BOLD,Paint.Align.CENTER);
            text(canvas,"NÃO FECHE O JOGO DURANTE A INSTALAÇÃO.",640,660,9,0xFF4B7186,Typeface.DEFAULT,Paint.Align.CENTER);
            postInvalidateDelayed(40);
        }
        canvas.restore();
    }

    private void text(Canvas c,String value,float x,float y,float size,int color,Typeface face) {
        text(c,value,x,y,size,color,face,Paint.Align.LEFT);
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
            if(y>=590&&y<=665) {
                if(x>=275&&x<=625&&retryAction!=null)retryAction.run();
                else if(x>=655&&x<=1005&&continueAction!=null)continueAction.run();
                performClick();
            }
            return true;
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
