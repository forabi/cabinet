package com.afollestad.cabinet.utils;

import android.os.Handler;
import android.util.Log;

import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.file.root.RootFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

/**
 * @author Aidan Follestad (afollestad)
 */
public class Perm {

    public static final int READ = 4;
    public static final int WRITE = 2;
    public static final int EXECUTE = 1;

    public static interface Callback {
        public void onComplete(boolean result, String error);
    }

    private static void log(String message) {
        Log.d("Perm", message);
    }

    public static void chmod(final File file, String permissionsString, final Callback callback) {
        final String cmd = "-c chmod " + permissionsString + " \"" + file.getPath() + "\"";
        final Handler mHandler = new Handler();
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (file.isRoot()) {
                    try {
                        List<String> results = ((RootFile) file).runAsRoot(cmd);
                        for (String str : results) {
                            log(str);
                        }
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onComplete(true, null);
                            }
                        });
                    } catch (final Exception e) {
                        e.printStackTrace();
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onComplete(false, e.getMessage());
                            }
                        });
                    }
                } else {
                    final boolean result = exec(cmd);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onComplete(result, null);
                        }
                    });
                }
            }
        }).start();
    }

    public static String parse(String permLine) {
        int owner = 0;
        if (permLine.charAt(1) == 'r')
            owner += READ;
        if (permLine.charAt(2) == 'w')
            owner += WRITE;
        if (permLine.charAt(3) == 'x')
            owner += EXECUTE;
        int group = 0;
        if (permLine.charAt(4) == 'r')
            group += READ;
        if (permLine.charAt(5) == 'w')
            group += WRITE;
        if (permLine.charAt(6) == 'x')
            group += EXECUTE;
        int world = 0;
        if (permLine.charAt(7) == 'r')
            world += READ;
        if (permLine.charAt(8) == 'w')
            world += WRITE;
        if (permLine.charAt(9) == 'x')
            world += EXECUTE;
        return owner + "" + group + "" + world;
    }

    private static boolean exec(String command) {
        log(command);
        Runtime runtime = Runtime.getRuntime();
        Process process;
        boolean mErrOcc = false;
        try {
            process = runtime.exec(command);
            try {
                String str;
                process.waitFor();
                BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                while ((str = stdError.readLine()) != null) {
                    log(str);
                    mErrOcc = true;
                }
                process.getInputStream().close();
                process.getOutputStream().close();
                process.getErrorStream().close();
            } catch (InterruptedException e) {
                mErrOcc = true;
            }
        } catch (IOException e1) {
            mErrOcc = true;
        }
        return !mErrOcc;
    }
}
