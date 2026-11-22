package com.battleuniverse.game;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.MotionEvent;
import java.util.Arrays;

public final class GameSurfaceView extends GLSurfaceView {
    private static final int JUMP=1, INTERACT=2, BUILD=4, RUN=8, FIRE=16, SWAP=32, AIM=64, CROUCH=128, KIT=256;
    private volatile float moveX,moveY,lookDX,lookDY;
    private volatile int buttons,screenMode,characterId,mapId,matchMode,quality=1;
    private volatile float lookSensitivity=1.0f,aimSensitivity=1.0f;
    private volatile int contentBuildLimit=12,contentResourceCount=5;
    private volatile int shirtStyle,bootStyle,setStyle,pantsStyle;
    private volatile int beardStyle,helmetStyle,backpackStyle,vestStyle;
    private volatile boolean contentPackReady=false;
    private volatile boolean invertCameraY=false;
    private volatile boolean trainingGodMode=false;
    private boolean aimToggled=false;
    private int movePointer=-1,lookPointer=-1;
    private final int[] buttonPointers={-1,-1,-1,-1,-1,-1,-1,-1,-1};
    private final int[] buttonBits={JUMP,INTERACT,BUILD,RUN,FIRE,SWAP,AIM,CROUCH,KIT};
    private float moveOriginX,moveOriginY,lastLookX,lastLookY;

    public GameSurfaceView(Context context) {
        super(context);
        setEGLContextClientVersion(3);
        // The engine uses depth testing but no stencil buffer. A 16-bit depth config
        // is more widely available on mid-range mobile GPUs than 24/8.
        setEGLConfigChooser(8,8,8,8,16,0);
        setPreserveEGLContextOnPause(true);
        setRenderer(new GameRenderer());
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        Arrays.fill(buttonPointers,-1);
    }

    public void setGameScreen(boolean playing) {
        screenMode=playing?1:0;
        if(!playing) {
            aimToggled=false;
            moveX=moveY=lookDX=lookDY=0;
            Arrays.fill(buttonPointers,-1);
            buttons=0;
        }
        queueEvent(()->nativeSetScreen(screenMode));
    }
    public void setCharacter(int id) {
        characterId=Math.max(0,Math.min(3,id));
        queueEvent(()->nativeSetCharacter(characterId));
    }
    public void setMap(int id) {
        mapId=Math.max(0,Math.min(7,id));
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
    public void setLookSensitivity(float general,float aiming) {
        lookSensitivity=Math.max(0.25f,Math.min(2.0f,general));
        aimSensitivity=Math.max(0.25f,Math.min(2.0f,aiming));
        queueEvent(()->nativeSetLookSensitivity(lookSensitivity,aimSensitivity));
    }
    public void setInvertCameraY(boolean enabled) { invertCameraY=enabled; }
    public void setTrainingGodMode(boolean enabled) {
        trainingGodMode=enabled&&matchMode==3;
        queueEvent(()->nativeSetGodMode(trainingGodMode));
    }
    public void setWardrobe(int shirt,int boots,int set) {
        shirtStyle=Math.max(0,Math.min(4,shirt));
        bootStyle=Math.max(0,Math.min(4,boots));
        setStyle=Math.max(0,Math.min(7,set));
        queueEvent(()->nativeSetWardrobe(shirtStyle,bootStyle,setStyle));
    }
    public void setPantsStyle(int value) {
        pantsStyle=Math.max(0,Math.min(1,value));
        queueEvent(()->nativeSetPantsStyle(pantsStyle));
    }
    public void setEquipment(int beard,int helmet,int backpack,int vest) {
        beardStyle=Math.max(0,Math.min(4,beard));
        helmetStyle=Math.max(0,Math.min(3,helmet));
        backpackStyle=Math.max(0,Math.min(3,backpack));
        vestStyle=Math.max(0,Math.min(3,vest));
        queueEvent(()->nativeSetEquipment(beardStyle,helmetStyle,backpackStyle,vestStyle));
    }
    public int getEliminations() {
        try { return nativeGetEliminations(); } catch(UnsatisfiedLinkError error) { return 0; }
    }
    public int getCollectedResources() {
        try { return nativeGetCollected(); } catch(UnsatisfiedLinkError error) { return 0; }
    }
    public int getMedkits() {
        try { return nativeGetMedkits(); } catch(UnsatisfiedLinkError error) { return 0; }
    }
    public int[] getMatchStats() {
        try { int[] stats=nativeGetMatchStats(); return stats==null?new int[]{0,0,0,0,100}:stats; }
        catch(UnsatisfiedLinkError error) { return new int[]{0,0,0,0,100}; }
    }
    public void setSpectatorMode(boolean enabled) {
        queueEvent(()->nativeSetSpectator(enabled));
    }
    public void setContentPackData(int buildLimit,int resourceCount) {
        contentBuildLimit=Math.max(4,Math.min(24,buildLimit));
        contentResourceCount=Math.max(1,Math.min(12,resourceCount));
        contentPackReady=true;
        queueEvent(()->nativeSetContentData(contentBuildLimit,contentResourceCount));
    }
    public float[] getPlayerPosition() {
        try { return nativeGetPlayerPosition(); }
        catch(UnsatisfiedLinkError error) { return new float[]{0f,0f}; }
    }
    public float[] getFlightInfo() {
        try {
            float[] info=nativeGetFlightInfo();
            return info==null||info.length<10?new float[]{0,-110,-32,0,0,0,112,0,0,0}:info;
        } catch(UnsatisfiedLinkError error) {
            return new float[]{0,-110,-32,0,0,0,112,0,0,0};
        }
    }
    public void setWaypoint(float worldX,float worldZ) {
        float x=Math.max(-145f,Math.min(145f,worldX));
        float z=Math.max(-145f,Math.min(145f,worldZ));
        queueEvent(()->nativeSetWaypoint(x,z));
    }
    public void clearWaypoint() { queueEvent(()->nativeSetWaypoint(Float.NaN,Float.NaN)); }
    public String getGameStatus() {
        try { return nativeGetStatus(); }
        catch(UnsatisfiedLinkError error) { return "INICIALIZANDO SISTEMAS..."; }
    }
    public boolean isAimToggled() { return aimToggled; }
    public boolean isFiring() { return (buttons&FIRE)!=0; }
    public boolean isMovingTouchActive() { return movePointer!=-1; }
    public float getMoveTouchX() { return moveX; }
    public float getMoveTouchY() { return moveY; }
    public float getMoveOriginDesignX() { return (moveOriginX-offsetX(getWidth(),scale(getWidth(),getHeight())))/scale(getWidth(),getHeight()); }
    public float getMoveOriginDesignY() { return (moveOriginY-offsetY(getHeight(),scale(getWidth(),getHeight())))/scale(getWidth(),getHeight()); }

    private float scale(float w,float h) { return Math.min(w/1280f,h/720f); }
    private float offsetX(float w,float s) { return (w-1280f*s)*0.5f; }
    private float offsetY(float h,float s) { return (h-720f*s)*0.5f; }
    private float designX(float x,float w,float s) { return (x-offsetX(w,s))/s; }
    private float designY(float y,float h,float s) { return (y-offsetY(h,s))/s; }

    // The HUD is drawn in a 1280x720 design canvas. Always hit-test in that
    // same coordinate space; normalized screen fractions break on ultrawide phones.
    private int controlAt(float px,float py,float w,float h) {
        float s=scale(w,h);
        float x=designX(px,w,s),y=designY(py,h,s);
        float[][] controls={
            {890f,310f,AIM,43f},
            {1002f,310f,FIRE,48f},
            {1165f,403f,BUILD,50f},
            {960f,461f,RUN,47f},
            {1065f,465f,SWAP,42f},
            {1152f,562f,JUMP,52f},
            {960f,605f,INTERACT,47f},
            {850f,562f,CROUCH,43f},
            {1065f,605f,KIT,39f}
        };
        for(float[] c:controls) {
            float dx=x-c[0],dy=y-c[1],r=c[3];
            if(dx*dx+dy*dy<=r*r) return (int)c[2];
        }
        return 0;
    }
    private void releasePointer(int id) {
        if(id==movePointer){movePointer=-1;moveX=moveY=0;}
        if(id==lookPointer)lookPointer=-1;
        for(int i=0;i<buttonPointers.length;i++)
            if(buttonPointers[i]==id)buttonPointers[i]=-1;
        recalculateButtons();
    }
    private void recalculateButtons() {
        int mask=0;
        for(int i=0;i<buttonPointers.length;i++)
            if(buttonPointers[i]!=-1)mask|=buttonBits[i];
        if(aimToggled)mask|=AIM;
        buttons=mask;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action=event.getActionMasked(),index=event.getActionIndex();
        float w=Math.max(1,getWidth()),h=Math.max(1,getHeight()),s=scale(w,h);
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN) {
            int id=event.getPointerId(index);
            float px=event.getX(index),py=event.getY(index);
            int control=controlAt(px,py,w,h);
            if(control!=0) {
                // A mira funciona por toque: liga e permanece ativa até o próximo toque.
                if(control==AIM) {
                    aimToggled=!aimToggled;
                    recalculateButtons();
                    invalidate();
                    return true;
                }
                for(int i=0;i<buttonPointers.length;i++) {
                    if(buttonBits[i]==control&&buttonPointers[i]==-1) {
                        buttonPointers[i]=id;recalculateButtons();return true;
                    }
                }
            }
            float x=designX(px,w,s),y=designY(py,h,s);
            if(movePointer==-1&&x<330f&&y>420f) {
                movePointer=id;
                moveOriginX=px;
                moveOriginY=py;
                updateMovement(px,py,w,h);
            } else if(lookPointer==-1&&x>=390f&&y>145f) {
                lookPointer=id;lastLookX=px;lastLookY=py;
            }
            return true;
        }
        if(action==MotionEvent.ACTION_MOVE) {
            for(int i=0;i<event.getPointerCount();i++) {
                int id=event.getPointerId(i);
                float x=event.getX(i),y=event.getY(i);
                if(id==movePointer)updateMovement(x,y,w,h);
                else if(id==lookPointer) {
                    lookDX-=(x-lastLookX)/w;
                    lookDY+=(y-lastLookY)/h;
                    lastLookX=x;lastLookY=y;
                }
            }
            return true;
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP
                ||action==MotionEvent.ACTION_CANCEL) {
            if(action==MotionEvent.ACTION_CANCEL) {
                movePointer=-1;lookPointer=-1;moveX=moveY=0;
                aimToggled=false;
                Arrays.fill(buttonPointers,-1);buttons=0;
            } else releasePointer(event.getPointerId(index));
            return true;
        }
        return true;
    }

    private void updateMovement(float x,float y,float w,float h) {
        float s=scale(w,h),radius=94f*s;
        moveX=clamp((x-moveOriginX)/radius,-1f,1f);
        moveY=clamp((moveOriginY-y)/radius,-1f,1f);
    }
    private static float clamp(float value,float min,float max) {
        return Math.max(min,Math.min(max,value));
    }

    private static native void nativeInit();
    private static native void nativeResize(int width,int height);
    private static native void nativeRender(float dt,float mx,float my,float lx,float ly,int buttons);
    private static native void nativeSetScreen(int mode);
    private static native void nativeSetCharacter(int id);
    private static native void nativeSetMap(int id);
    private static native void nativeSetMode(int id);
    private static native void nativeSetGodMode(boolean enabled);
    private static native void nativeSetQuality(int value);
    private static native void nativeSetLookSensitivity(float general,float aiming);
    private static native void nativeSetWardrobe(int shirt,int boots,int set);
    private static native void nativeSetPantsStyle(int pants);
    private static native void nativeSetEquipment(int beard,int helmet,int backpack,int vest);
    private static native void nativeSetContentData(int buildLimit,int resourceCount);
    private static native String nativeGetStatus();
    private static native float[] nativeGetPlayerPosition();
    private static native float[] nativeGetFlightInfo();
    private static native void nativeSetWaypoint(float worldX,float worldZ);
    private static native int nativeGetEliminations();
    private static native int nativeGetCollected();
    private static native int nativeGetMedkits();
    private static native int[] nativeGetMatchStats();
    private static native void nativeSetSpectator(boolean enabled);

    private final class GameRenderer implements GLSurfaceView.Renderer {
        private long previous;
        @Override public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl,
                javax.microedition.khronos.egl.EGLConfig config) {
            Log.i("BattleUniverse","Startup stage: GLSurfaceView surface created; initializing native engine");
            try {
                nativeInit();
            } catch(Throwable error) {
                Log.e("BattleUniverse","Java/JNI startup failure in nativeInit",error);
                return;
            }
            nativeSetCharacter(characterId);nativeSetMap(mapId);nativeSetMode(matchMode);
            nativeSetGodMode(trainingGodMode);
            nativeSetQuality(quality);nativeSetLookSensitivity(lookSensitivity,aimSensitivity);
            nativeSetWardrobe(shirtStyle,bootStyle,setStyle);
            nativeSetPantsStyle(pantsStyle);
            nativeSetEquipment(beardStyle,helmetStyle,backpackStyle,vestStyle);
            if(contentPackReady)nativeSetContentData(contentBuildLimit,contentResourceCount);
            nativeSetScreen(screenMode);
            previous=System.nanoTime();
            Log.i("BattleUniverse","Startup stage: renderer initialization sequence completed");
        }
        @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl,
                int width,int height) {
            nativeResize(width,height);
        }
        @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl) {
            long now=System.nanoTime();
            float dt=Math.min(0.05f,Math.max(0.001f,(now-previous)/1_000_000_000f));
            previous=now;
            float dx=lookDX,dy=lookDY;lookDX=lookDY=0;
            nativeRender(dt,moveX,moveY,dx,invertCameraY?-dy:dy,buttons);
        }
    }
    static { System.loadLibrary("battleuniverse"); }
}
