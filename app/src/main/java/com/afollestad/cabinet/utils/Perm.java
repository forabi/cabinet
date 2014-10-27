package com.afollestad.cabinet.utils;

import android.os.Handler;
import android.util.Log;

import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.file.root.RootFile;
import com.afollestad.cabinet.fragments.DetailsDialog;

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

    public static String parse(String permLine, DetailsDialog dialog) {
        int owner = 0;
        if (permLine.charAt(1) == 'r') {
            owner += READ;
            dialog.ownerR.setChecked(true);
        }
        if (permLine.charAt(2) == 'w') {
            owner += WRITE;
            dialog.ownerW.setChecked(true);
        }
        if (permLine.charAt(3) == 'x') {
            owner += EXECUTE;
            dialog.ownerX.setChecked(true);
        }
        int group = 0;
        if (permLine.charAt(4) == 'r') {
            group += READ;
            dialog.groupR.setChecked(true);
        }
        if (permLine.charAt(5) == 'w') {
            group += WRITE;
            dialog.groupW.setChecked(true);
        }
        if (permLine.charAt(6) == 'x') {
            group += EXECUTE;
            dialog.groupX.setChecked(true);
        }
        int world = 0;
        if (permLine.charAt(7) == 'r') {
            world += READ;
            dialog.otherR.setChecked(true);
        }
        if (permLine.charAt(8) == 'w') {
            world += WRITE;
            dialog.otherW.setChecked(true);
        }
        if (permLine.charAt(9) == 'x') {
            world += EXECUTE;
            dialog.otherX.setChecked(true);
        }
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
