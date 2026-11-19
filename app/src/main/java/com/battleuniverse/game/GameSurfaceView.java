package com.battleuniverse.game;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;
import java.util.Arrays;

public final class GameSurfaceView extends GLSurfaceView {
    private static final int JUMP=1, INTERACT=2, BUILD=4, RUN=8, PULSE=16;
    private volatile float moveX, moveY, lookDX, lookDY;
    private volatile int buttons, screenMode, characterId, mapId, matchMode, quality=1;
    private int movePointer=-1, lookPointer=-1;
    private final int[] buttonPointers={-1,-1,-1,-1,-1};
    private final int[] buttonBits={JUMP,INTERACT,BUILD,RUN,PULSE};
    private float moveOriginX, moveOriginY, lastLookX,lastLookY;

    public GameSurfaceView(Context context) {
        super(context);
        setEGLContextClientVersion(3);
        setEGLConfigChooser(8,8,8,8,24,8);
        setPreserveEGLContextOnPause(true);
        setRenderer(new GameRenderer());
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        Arrays.fill(buttonPointers,-1);
    }

    public void setGameScreen(boolean playing) {
        screenMode=playing?1:0;
        queueEvent(()->nativeSetScreen(screenMode));
    }
    public void setCharacter(int id) {
        characterId=Math.max(0,Math.min(3,id));
        queueEvent(()->nativeSetCharacter(characterId));
    }
    public void setMap(int id) {
        mapId=Math.max(0,Math.min(2,id));
        queueEvent(()->nativeSetMap(mapId));
    }
    public void setMatchMode(int id) {
        matchMode=Math.max(0,Math.min(3,id));
        queueEvent(()->nativeSetMode(matchMode));
    }
    public void setQuality(int value) {
        quality=Math.max(0,Math.min(2,value));
        queueEvent(()->nativeSetQuality(quality));
    }
    public String getGameStatus() {
        try { return nativeGetStatus(); }
        catch (UnsatisfiedLinkError error) { return "INICIALIZANDO SISTEMAS..."; }
    }

    private int controlAt(float x,float y,float w,float h) {
        float[][] controls={
            {0.90f,0.78f,JUMP},
            {0.91f,0.56f,BUILD},
            {0.75f,0.84f,INTERACT},
            {0.75f,0.64f,RUN},
            {0.78f,0.43f,PULSE}
        };
        float radius=Math.min(w,h)*0.085f;
        for(float[] control:controls) {
            float dx=x-control[0]*w, dy=y-control[1]*h;
            if(dx*dx+dy*dy<=radius*radius) return (int)control[2];
        }
        return 0;
    }

    private void releasePointer(int id) {
        if(id==movePointer) {
            movePointer=-1; moveX=0; moveY=0;
        }
        if(id==lookPointer) lookPointer=-1;
        for(int i=0;i<buttonPointers.length;i++) {
            if(buttonPointers[i]==id) buttonPointers[i]=-1;
        }
        recalculateButtons();
    }
    private void recalculateButtons() {
        int mask=0;
        for(int i=0;i<buttonPointers.length;i++)
            if(buttonPointers[i]!=-1) mask|=buttonBits[i];
        buttons=mask;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action=event.getActionMasked(), index=event.getActionIndex();
        float w=Math.max(1,getWidth()), h=Math.max(1,getHeight());
        if(action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN) {
            int id=event.getPointerId(index);
            float x=event.getX(index), y=event.getY(index);
            int control=controlAt(x,y,w,h);
            if(control!=0) {
                for(int i=0;i<buttonPointers.length;i++) {
                    if(buttonBits[i]==control && buttonPointers[i]==-1) {
                        buttonPointers[i]=id; recalculateButtons(); return true;
                    }
                }
            }
            if(movePointer==-1 && x<w*0.38f && y>h*0.42f) {
                movePointer=id;
                moveOriginX=w*0.14f;
                moveOriginY=h*0.79f;
                updateMovement(x,y,w,h);
            } else if(lookPointer==-1 && x>=w*0.40f) {
                lookPointer=id; lastLookX=x; lastLookY=y;
            }
            return true;
        }
        if(action==MotionEvent.ACTION_MOVE) {
            float radius=Math.max(55f,Math.min(w,h)*0.16f);
            for(int i=0;i<event.getPointerCount();i++) {
                int id=event.getPointerId(i);
                float x=event.getX(i), y=event.getY(i);
                if(id==movePointer) updateMovement(x,y,w,h);
                else if(id==lookPointer) {
                    lookDX+=(x-lastLookX)/w;
                    lookDY+=(y-lastLookY)/h;
                    lastLookX=x; lastLookY=y;
                }
            }
            return true;
        }
        if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_POINTER_UP
                || action==MotionEvent.ACTION_CANCEL) {
            if(action==MotionEvent.ACTION_CANCEL) {
                movePointer=-1; lookPointer=-1; moveX=moveY=0;
                Arrays.fill(buttonPointers,-1); buttons=0;
            } else releasePointer(event.getPointerId(index));
            return true;
        }
        return true;
    }

    private void updateMovement(float x,float y,float w,float h) {
        float radius=Math.max(55f,Math.min(w,h)*0.16f);
        moveX=clamp((x-moveOriginX)/radius,-1f,1f);
        // Finger moving upward is positive forward; native engine uses this sign directly.
        moveY=clamp((moveOriginY-y)/radius,-1f,1f);
    }
    private static float clamp(float v,float min,float max) {
        return Math.max(min,Math.min(max,v));
    }

    private static native void nativeInit();
    private static native void nativeResize(int width,int height);
    private static native void nativeRender(float dt,float mx,float my,float lx,float ly,int buttons);
    private static native void nativeSetScreen(int mode);
    private static native void nativeSetCharacter(int id);
    private static native void nativeSetMap(int id);
    private static native void nativeSetMode(int id);
    private static native void nativeSetQuality(int value);
    private static native String nativeGetStatus();

    private final class GameRenderer implements GLSurfaceView.Renderer {
        private long previous;
        @Override public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl,
                javax.microedition.khronos.egl.EGLConfig config) {
            nativeInit();
            nativeSetScreen(screenMode);
            nativeSetCharacter(characterId);
            nativeSetMap(mapId);
            nativeSetMode(matchMode);
            nativeSetQuality(quality);
            previous=System.nanoTime();
        }
        @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl,
                int width,int height) {
            nativeResize(width,height);
        }
        @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl) {
            long now=System.nanoTime();
            float dt=Math.min(0.05f,Math.max(0.001f,(now-previous)/1_000_000_000f));
            previous=now;
            float dx=lookDX,dy=lookDY;
            lookDX=lookDY=0;
            nativeRender(dt,moveX,moveY,dx,dy,buttons);
        }
    }
    static { System.loadLibrary("battleuniverse"); }
}
