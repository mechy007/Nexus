/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.live2d.demo.full;

import android.opengl.GLSurfaceView;
import android.opengl.GLES20;
import android.util.Log;
import java.util.function.Consumer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "NEXUS-Allium";
    private final Consumer<String> onFailure;
    private volatile boolean rendererFailed;

    public GLRenderer() { this(null); }

    public GLRenderer(Consumer<String> onFailure) {
        this.onFailure = onFailure;
    }

    private void fail(String message, RuntimeException error) {
        rendererFailed = true;
        Log.e(TAG, message, error);
        if (onFailure != null) onFailure.accept(message);
    }
    // Called at initialization (when the drawing context is lost and recreated).
    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        rendererFailed = false;
        try {
            LAppDelegate.getInstance().onSurfaceCreated();
        } catch (RuntimeException error) {
            fail("Live2D surface initialization failed; keeping NEXUS available.", error);
        }
    }

    // Mainly called when switching between landscape and portrait.
    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        if (rendererFailed || width <= 0 || height <= 0) return;
        try {
            LAppDelegate.getInstance().onSurfaceChanged(width, height);
        } catch (RuntimeException error) {
            fail("Live2D surface resize failed; keeping NEXUS available.", error);
        }
    }

    // Called repeatedly for drawing.
    @Override
    public void onDrawFrame(GL10 unused) {
        if (rendererFailed) {
            GLES20.glClearColor(0.035f, 0.043f, 0.075f, 1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            return;
        }
        try {
            LAppDelegate.getInstance().run();
        } catch (RuntimeException error) {
            fail("Live2D frame rendering failed; keeping NEXUS available.", error);
            GLES20.glClearColor(0.035f, 0.043f, 0.075f, 1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        }
    }
}
