package com.battleuniverse.game;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

public final class GameSurfaceView extends GLSurfaceView {
    private static final int ACTION_JUMP = 1;
    private static final int ACTION_INTERACT = 2;

    private volatile float moveX;
    private volatile float moveY;
    private volatile float lookDX;
    private volatile float lookDY;
    private volatile int buttons;

    private int movePointer = -1;
    private int lookPointer = -1;
    private int buttonPointer = -1;
    private float moveOriginX;
    private float moveOriginY;
    private float lastLookX;
    private float lastLookY;
    private int activeButton;

    public GameSurfaceView(Context context) {
        super(context);
        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 8, 24, 8);
        setPreserveEGLContextOnPause(true);
        setRenderer(new GameRenderer());
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    public String getStatusText() {
        try {
            return nativeGetStatus();
        } catch (RuntimeException | LinkageError error) {
            return "CARREGANDO CENÁRIO 3D...";
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        final int index = event.getActionIndex();

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            final int id = event.getPointerId(index);
            final float x = event.getX(index);
            final float y = event.getY(index);
            final float w = Math.max(1, getWidth());
            final float h = Math.max(1, getHeight());

            if (buttonPointer == -1 && x > w * 0.75f && y > h * 0.72f) {
                buttonPointer = id;
                activeButton = ACTION_JUMP;
                buttons |= ACTION_JUMP;
            } else if (buttonPointer == -1 && x > w * 0.68f && y > h * 0.52f) {
                buttonPointer = id;
                activeButton = ACTION_INTERACT;
                buttons |= ACTION_INTERACT;
            } else if (movePointer == -1 && x < w * 0.46f && y > h * 0.28f) {
                movePointer = id;
                moveOriginX = x;
                moveOriginY = y;
                moveX = 0;
                moveY = 0;
            } else if (lookPointer == -1 && x >= w * 0.42f) {
                lookPointer = id;
                lastLookX = x;
                lastLookY = y;
            }
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            final float radius = Math.max(60f, getWidth() * 0.14f);
            for (int p = 0; p < event.getPointerCount(); p++) {
                final int id = event.getPointerId(p);
                final float x = event.getX(p);
                final float y = event.getY(p);

                if (id == movePointer) {
                    moveX = clamp((x - moveOriginX) / radius, -1f, 1f);
                    moveY = clamp((moveOriginY - y) / radius, -1f, 1f);
                } else if (id == lookPointer) {
                    lookDX += (x - lastLookX) / Math.max(1f, getWidth());
                    lookDY += (y - lastLookY) / Math.max(1f, getHeight());
                    lastLookX = x;
                    lastLookY = y;
                }
            }
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP
                || action == MotionEvent.ACTION_CANCEL) {
            final int id = action == MotionEvent.ACTION_CANCEL ? -1 : event.getPointerId(index);
            if (action == MotionEvent.ACTION_CANCEL || id == movePointer) {
                movePointer = -1;
                moveX = 0;
                moveY = 0;
            }
            if (action == MotionEvent.ACTION_CANCEL || id == lookPointer) {
                lookPointer = -1;
            }
            if (action == MotionEvent.ACTION_CANCEL || id == buttonPointer) {
                buttons &= ~activeButton;
                buttonPointer = -1;
                activeButton = 0;
            }
            return true;
        }
        return true;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static native void nativeInit();
    private static native void nativeResize(int width, int height);
    private static native void nativeRender(
            float deltaSeconds, float moveX, float moveY,
            float lookDX, float lookDY, int buttons);
    private static native String nativeGetStatus();

    private final class GameRenderer implements GLSurfaceView.Renderer {
        private long previousFrameNanos;

        @Override
        public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl,
                                     javax.microedition.khronos.egl.EGLConfig config) {
            nativeInit();
            previousFrameNanos = System.nanoTime();
        }

        @Override
        public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl,
                                     javax.microedition.khronos.egl.EGLConfig config,
                                     int width, int height) {
            nativeResize(width, height);
        }

        @Override
        public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl) {
            long now = System.nanoTime();
            float dt = Math.min(0.05f, Math.max(0.001f,
                    (now - previousFrameNanos) / 1_000_000_000f));
            previousFrameNanos = now;

            float dx = lookDX;
            float dy = lookDY;
            lookDX = 0;
            lookDY = 0;
            nativeRender(dt, moveX, moveY, dx, dy, buttons);
        }
    }

    static {
        System.loadLibrary("battleuniverse");
    }
}
