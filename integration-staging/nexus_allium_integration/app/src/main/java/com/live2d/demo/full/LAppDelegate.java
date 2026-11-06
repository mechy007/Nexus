/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.live2d.demo.full;

import android.app.Activity;
import android.opengl.GLES20;
import android.os.Build;
import com.live2d.demo.LAppDefine;
import com.live2d.sdk.cubism.framework.CubismFramework;
import com.live2d.sdk.cubism.framework.rendering.android.CubismShaderAndroid;

import static android.opengl.GLES20.*;

public class LAppDelegate {
    public static LAppDelegate getInstance() {
        if (s_instance == null) {
            s_instance = new LAppDelegate();
        }
        return s_instance;
    }

    /**
     * クラスのインスタンス（シングルトン）を解放する。
     */
    public static void releaseInstance() {
        if (s_instance != null) {
            s_instance = null;
        }
    }

    /**
     * アプリケーションを非アクティブにする
     */
    public void deactivateApp() {
        isActive = false;
    }

    public void onStart(Activity activity) {
        this.activity = activity;
        isActive = true;
    }

    public void onPause() {}

    public void onStop() {}

    public void onDestroy() {
        if (view != null) {
            view.close();
        }
        textureManager = null;

        LAppLive2DManager.releaseInstance();
        CubismFramework.dispose();

        releaseInstance();
    }

    public void onSurfaceCreated() {
        // テクスチャサンプリング設定
        GLES20.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GLES20.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

        // 透過設定
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

        if (textureManager == null) {
            textureManager = new LAppTextureManager();
        } else {
            // 無効になっているテクスチャ情報を破棄
            textureManager.releaseInvalidTextures();
        }

        if (view != null) {
            view.close();
        }
        view = new LAppView();

        LAppPal.updateTime();

        if (!CubismFramework.isInitialized()) {
            CubismFramework.initialize();
        }

        // 無効になっているOpenGLリソースを破棄
        CubismShaderAndroid.getInstance().releaseInvalidShaderProgram();
        // シェーダコードを再読み込みするためにインスタンスを破棄しておく
        CubismShaderAndroid.deleteInstance();

        // Do not create the model here: GLSurfaceView can call onSurfaceCreated
        // before it reports a valid surface size. Model renderer creation is deferred
        // until onSurfaceChanged supplies the real dimensions.
        rendererNeedsReload = LAppLive2DManager.hasInstance();
    }

    public void onSurfaceChanged(int width, int height) {
        if (width <= 0 || height <= 0 || view == null) {
            return;
        }
        // 描画範囲指定
        GLES20.glViewport(0, 0, width, height);
        windowWidth = width;
        windowHeight = height;

        // AppViewの初期化
        view.initialize();

        // Create the model only after the GL surface has a valid size. If Android
        // recreated the EGL context, rebuild model renderers in this new context.
        LAppLive2DManager live2DManager = LAppLive2DManager.getInstance();
        if (rendererNeedsReload) {
            live2DManager.reloadRenderers();
            rendererNeedsReload = false;
        }
        live2DManager.setRenderTargetSize(width, height);
    }

    public void run() {
        // 時間更新
        LAppPal.updateTime();

        // 画面初期化
        glClearColor(0.035f, 0.043f, 0.075f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glClearDepthf(1.0f);

        if (view != null) {
            view.render();
        }

        // アプリケーションを非アクティブにする
        if (!isActive && activity != null) {
            activity.finishAndRemoveTask();
        }
    }


    public void onTouchBegan(float x, float y) {
        mouseX = x;
        mouseY = y;

        if (view != null) {
            isCaptured = true;
            view.onTouchesBegan(mouseX, mouseY);
        }
    }

    public void onTouchEnd(float x, float y) {
        mouseX = x;
        mouseY = y;

        if (view != null) {
            isCaptured = false;
            view.onTouchesEnded(mouseX, mouseY);
        }
    }

    public void onTouchMoved(float x, float y) {
        mouseX = x;
        mouseY = y;

        if (isCaptured && view != null) {
            view.onTouchesMoved(mouseX, mouseY);
        }
    }

    // getter, setter群
    public Activity getActivity() {
        return activity;
    }

    public LAppTextureManager getTextureManager() {
        return textureManager;
    }

    public LAppView getView() {
        return view;
    }

    /**
     * シーンインデックスを設定する。
     *
     * @param index シーンインデックス
     */
    public void setSceneIndex(int index) {
        sceneIndex = index;
    }

    /**
     * シーンインデックスを取得する。
     *
     * @return シーンインデックス
     */
    public int getSceneIndex() {
        return sceneIndex;
    }

    public int getWindowWidth() {
        return windowWidth;
    }

    public int getWindowHeight() {
        return windowHeight;
    }

    private static LAppDelegate s_instance;

    private LAppDelegate() {
        sceneIndex = 0;

        // Set up Cubism SDK framework.
        cubismOption.logFunction = new LAppPal.PrintLogFunction();
        cubismOption.loggingLevel = LAppDefine.cubismLoggingLevel;
        cubismOption.loadFileFunction = new LAppPal.LoadFileFunction();

        CubismFramework.cleanUp();
        CubismFramework.startUp(cubismOption);
    }

    private Activity activity;

    private final CubismFramework.Option cubismOption = new CubismFramework.Option();

    private LAppTextureManager textureManager;
    private LAppView view;
    private int windowWidth;
    private int windowHeight;
    private boolean isActive = true;
    private boolean rendererNeedsReload;

    /**
     * モデルシーンインデックス
     */
    private int sceneIndex;

    /**
     * クリックしているか
     */
    private boolean isCaptured;
    /**
     * マウスのX座標
     */
    private float mouseX;
    /**
     * マウスのY座標
     */
    private float mouseY;
}
