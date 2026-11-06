/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.live2d.demo.full;

import android.util.Log;
import com.live2d.demo.LAppDefine;
import com.live2d.sdk.cubism.core.ICubismLogger;
import com.live2d.sdk.cubism.framework.ICubismLoadFileFunction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class LAppPal {
    /**
     * Logging Function class to be registered in the CubismFramework's logging function.
     */
    public static class PrintLogFunction implements ICubismLogger {
        @Override
        public void print(String message) {
            Log.d(TAG, message);
        }
    }

    /**
     * File loading function class to be registered in the CubismFramework's file loading function.
     */
    public static class LoadFileFunction implements ICubismLoadFileFunction {
        @Override
        public byte[] load(String path) {
            return LAppPal.loadFileAsBytes(path);
        }
    }

    // アプリケーションを中断状態にする。実行されるとonPause()イベントが発生する
    public static void moveTaskToBack() {
        LAppDelegate.getInstance().getActivity().moveTaskToBack(true);
    }

    // デルタタイムの更新
    public static void updateTime() {
        s_currentFrame = getSystemNanoTime();
        _deltaNanoTime = s_currentFrame - _lastNanoTime;
        _lastNanoTime = s_currentFrame;
    }

    // ファイルをバイト列として読み込む
    public static byte[] loadFileAsBytes(final String path) {
        try (InputStream fileData = LAppDelegate.getInstance().getActivity().getAssets().open(path);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int bytesRead;
            while ((bytesRead = fileData.read(chunk)) != -1) {
                output.write(chunk, 0, bytesRead);
            }
            return output.toByteArray();
        } catch (IOException | RuntimeException error) {
            Log.e(TAG, "Unable to load Live2D asset: " + path, error);
            return new byte[0];
        }
    }

    // デルタタイム(前回フレームとの差分)を取得する
    public static float getDeltaTime() {
        // ナノ秒を秒に変換
        return (float) (_deltaNanoTime / 1000000000.0f);
    }

    /**
     * Logging function
     *
     * @param message log message
     */
    public static void printLog(String message) {
        Log.d(TAG, message);
    }

    private static long getSystemNanoTime() {
        return System.nanoTime();
    }

    private static double s_currentFrame;
    private static double _lastNanoTime;
    private static double _deltaNanoTime;

    private static final String TAG = "[APP]";

    private LAppPal() {}
}
