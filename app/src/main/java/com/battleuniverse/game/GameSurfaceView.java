package com.battleuniverse.game;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

public final class GameSurfaceView extends GLSurfaceView {
    private volatile float moveX,moveY,lookDX,lookDY;
    private volatile int buttons,screenMode,characterId,mapId,matchMode,quality=1;
    private int movePointer=-1,lookPointer=-1,buttonPointer=-1;
    private float moveOriginX,moveOriginY,lastLookX,lastLookY;

    public GameSurfaceView(Context context){
        super(context);setEGLContextClientVersion(3);setEGLConfigChooser(8,8,8,8,24,8);
        setPreserveEGLContextOnPause(true);setRenderer(new GameRenderer());
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }
    public void setGameScreen(boolean playing){screenMode=playing?1:0;queueEvent(()->nativeSetScreen(screenMode));}
    public void setCharacter(int id){characterId=Math.max(0,Math.min(3,id));queueEvent(()->nativeSetCharacter(characterId));}
    public void setMap(int id){mapId=Math.max(0,Math.min(2,id));queueEvent(()->nativeSetMap(mapId));}
    public void setMatchMode(int id){matchMode=Math.max(0,Math.min(3,id));queueEvent(()->nativeSetMode(matchMode));}
    public void setQuality(int value){quality=Math.max(0,Math.min(2,value));queueEvent(()->nativeSetQuality(quality));}

    @Override public boolean onTouchEvent(MotionEvent e){
        int action=e.getActionMasked(),index=e.getActionIndex();
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
            int id=e.getPointerId(index);float x=e.getX(index),y=e.getY(index);
            float w=Math.max(1,getWidth()),h=Math.max(1,getHeight()),pad=Math.min(w,h)*.10f;
            float jx=w-pad*.85f,jy=h-pad*.92f,cx=w-pad*2f,cy=h-pad*1.95f;
            float jdx=x-jx,jdy=y-jy,cdx=x-cx,cdy=y-cy;
            if(buttonPointer==-1&&jdx*jdx+jdy*jdy<=pad*pad*.3025f){buttonPointer=id;buttons|=1;}
            else if(buttonPointer==-1&&cdx*cdx+cdy*cdy<=pad*pad*.25f){buttonPointer=id;buttons|=2;}
            else if(movePointer==-1&&x<w*.46f&&y>h*.28f){movePointer=id;moveOriginX=x;moveOriginY=y;moveX=moveY=0;}
            else if(lookPointer==-1&&x>=w*.42f){lookPointer=id;lastLookX=x;lastLookY=y;}
            return true;
        }
        if(action==MotionEvent.ACTION_MOVE){
            float radius=Math.max(60f,getWidth()*.14f);
            for(int i=0;i<e.getPointerCount();i++){
                int id=e.getPointerId(i);float x=e.getX(i),y=e.getY(i);
                if(id==movePointer){moveX=clamp((x-moveOriginX)/radius,-1,1);moveY=clamp((moveOriginY-y)/radius,-1,1);}
                else if(id==lookPointer){lookDX+=(x-lastLookX)/Math.max(1,getWidth());lookDY+=(y-lastLookY)/Math.max(1,getHeight());lastLookX=x;lastLookY=y;}
            }return true;
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP||action==MotionEvent.ACTION_CANCEL){
            int id=action==MotionEvent.ACTION_CANCEL?-1:e.getPointerId(index);
            if(action==MotionEvent.ACTION_CANCEL||id==movePointer){movePointer=-1;moveX=moveY=0;}
            if(action==MotionEvent.ACTION_CANCEL||id==lookPointer)lookPointer=-1;
            if(action==MotionEvent.ACTION_CANCEL||id==buttonPointer){buttons=0;buttonPointer=-1;}
            return true;
        }return true;
    }
    private static float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}
    private static native void nativeInit();
    private static native void nativeResize(int width,int height);
    private static native void nativeRender(float dt,float mx,float my,float lx,float ly,int buttons);
    private static native void nativeSetScreen(int mode);
    private static native void nativeSetCharacter(int id);
    private static native void nativeSetMap(int id);
    private static native void nativeSetMode(int id);
    private static native void nativeSetQuality(int value);

    private final class GameRenderer implements GLSurfaceView.Renderer{
        private long previous;
        @Override public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl,javax.microedition.khronos.egl.EGLConfig config){
            nativeInit();nativeSetScreen(screenMode);nativeSetCharacter(characterId);nativeSetMap(mapId);nativeSetMode(matchMode);nativeSetQuality(quality);previous=System.nanoTime();
        }
        @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl,int width,int height){nativeResize(width,height);}
        @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl){
            long now=System.nanoTime();float dt=Math.min(.05f,Math.max(.001f,(now-previous)/1_000_000_000f));previous=now;
            float dx=lookDX,dy=lookDY;lookDX=lookDY=0;nativeRender(dt,moveX,moveY,dx,dy,buttons);
        }
    }
    static{System.loadLibrary("battleuniverse");}
}
