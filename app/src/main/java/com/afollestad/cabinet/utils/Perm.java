package com.afollestad.cabinet.utils;

import android.os.Handler;
import android.util.Log;

import com.afollestad.cabinet.file.LocalFile;
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

    private static File isSymlink(File file) throws IOException {
        if (file.isRemote() || file.isRoot()) {
            return file;
        }
        java.io.File canon;
        if (file.getParent() == null) {
            canon = file.toJavaFile();
        } else {
            java.io.File canonDir = file.toJavaFile().getParentFile().getCanonicalFile();
            log(file.toJavaFile().getCanonicalPath());
            log(canonDir.getAbsolutePath());
            canon = new java.io.File(canonDir, file.getName());
        }
        if (!canon.getCanonicalFile().equals(canon.getAbsoluteFile())) {
            return new LocalFile(file.getContext(), canon);
        } else {
            return file;
        }
    }

    public static void chmod(final File file, int owner, int group, int other, final Callback callback) {
        String path;
        try {
            path = isSymlink(file).getPath();
        } catch (IOException e) {
            e.printStackTrace();
            callback.onComplete(false, e.getMessage());
            return;
        }
        final String perm = owner + "" + group + "" + other;
        final String cmd = "chmod " + perm + " " + path;
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
