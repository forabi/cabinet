package com.afollestad.cabinet.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.file.CloudFile;
import com.afollestad.cabinet.file.LocalFile;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.fragments.DirectoryFragment;
import com.afollestad.cabinet.services.NetworkService;
import com.afollestad.cabinet.sftp.SftpClient;
import com.afollestad.cabinet.ui.DrawerActivity;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class Utils {

    public static interface DuplicateCheckResult {
        void onResult(File file);
    }

    public interface InputCallback {
        void onInput(String input);
    }

    public interface InputCancelCallback extends InputCallback {
        void onCancel();
    }

    public interface FileCallback {
        void onFile(File file);
    }

    public static int resolveDrawable(Context context, int drawable) {
        TypedArray a = context.obtainStyledAttributes(new int[]{drawable});
        int resId = a.getResourceId(0, 0);
        a.recycle();
        return resId;
    }

    public static int resolveColor(Context context, int color) {
        TypedArray a = context.obtainStyledAttributes(new int[]{color});
        int resId = a.getColor(0, context.getResources().getColor(R.color.cabinet_color));
        a.recycle();
        return resId;
    }

    public static void lockOrientation(Activity context) {
        int currentOrientation = context.getResources().getConfiguration().orientation;
        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            context.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else {
            context.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        }
    }

    public static void unlockOrientation(Activity context) {
        context.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    public static void checkDuplicates(final Activity context, final File file, final DuplicateCheckResult callback) {
        checkDuplicates(context, file, file.getNameNoExtension(), 0, callback);
    }

    private static void checkDuplicates(final Activity context, final File file, final String originalNameNoExt, final int checks, final DuplicateCheckResult callback) {
        Log.v("checkDuplicates", "Checking: " + file.getPath());
        file.exists(new File.BooleanCallback() {
            @Override
            public void onComplete(final boolean result) {
                context.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (result) {
                            String newName = originalNameNoExt;
                            if (checks > 0) newName += " (" + checks + ")";
                            if (!file.isDirectory()) newName += "." + file.getExtension();
                            File newFile = file.isRemote() ?
                                    new CloudFile(context, (CloudFile) file.getParent(), newName, file.isDirectory()) :
                                    new LocalFile(context, file.getParent(), newName);
                            checkDuplicates(context, newFile, originalNameNoExt, 1 + checks, callback);
                        } else {
                            callback.onResult(file);
                        }
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                if (e != null)
                    showErrorDialog(context, e.getMessage());
            }
        });
    }

    public static File checkDuplicatesSync(final Activity context, final File file) throws Exception {
        return checkDuplicatesSync(context, file, file.getNameNoExtension(), 0);
    }

    private static File checkDuplicatesSync(final Activity context, final File file, final String originalNameNoExt, final int checks) throws Exception {
        Log.v("checkDuplicatesSync", "Checking: " + file.getPath());
        if (file.existsSync()) {
            String newName = originalNameNoExt;
            if (checks > 0) newName += " (" + checks + ")";
            if (!file.isDirectory()) newName += "." + file.getExtension();
            File newFile = file.isRemote() ?
                    new CloudFile(context, (CloudFile) file.getParent(), newName, file.isDirectory()) :
                    new LocalFile(context, file.getParent(), newName);
            return checkDuplicatesSync(context, newFile, originalNameNoExt, 1 + checks);
        } else return file;
    }

    public static void setSorter(DirectoryFragment context, int sorter) {
        PreferenceManager.getDefaultSharedPreferences(context.getActivity()).edit().putInt("sorter", sorter).commit();
        context.mAdapter.showLastModified = (sorter == 5);
        context.sorter = sorter;
        context.resort();
    }

    public static int getSorter(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getInt("sorter", 0);
    }

    public static void setGridColumn(DirectoryFragment context, int gridColumn) {
        PreferenceManager.getDefaultSharedPreferences(context.getActivity()).edit().putInt("gridColumn", gridColumn).commit();
        context.gridColumn = gridColumn;
        context.changeLayout();
    }

    public static int getGridColumn(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getInt("gridColumn", 1);
    }

    public static void setFilter(DirectoryFragment context, String filter) {
        PreferenceManager.getDefaultSharedPreferences(context.getActivity()).edit().putString("filter", filter).commit();
        context.filter = filter;
        context.reload();
    }

    public static String getFilter(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString("filter", null);
    }

    public static boolean getShowHidden(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean("show_hidden", false);
    }

    public interface SimpleClickListener {
        void onPositive(int which, View view);
    }

    public interface ClickListener extends SimpleClickListener {
        void onNeutral();

        void onNegative();
    }

    public static void showConfirmDialog(Activity context, int title, int message, Object replacement, final SimpleClickListener callback) {
//        CustomDialog.create(context, title, context.getString(message, replacement), R.string.yes, 0, R.string.no, callback).show(context.getFragmentManager(), "CONFIRM");
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(context.getString(message, replacement))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        callback.onPositive(which, null);
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (callback instanceof ClickListener) {
                            ((ClickListener) callback).onNegative();
                        }
                    }
                })
                .show();
    }

    public static void showErrorDialog(final Activity context, final int message, final Exception e) {
        context.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(context)
                        .setTitle(R.string.error)
                        .setMessage(context.getString(message, e.getLocalizedMessage()))
                        .create()
                        .show();
            }
        });
    }

    public static void showErrorDialog(final Activity context, final String message) {
        context.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(context)
                        .setTitle(R.string.error)
                        .setMessage(message)
                        .create()
                        .show();
            }
        });
    }

    public static ProgressDialog showProgressDialog(Activity context, int message, ProgressDialog.OnCancelListener cancelListener) {
        ProgressDialog mDialog = new ProgressDialog(context);
        mDialog.setCancelable(cancelListener != null);
        mDialog.setOnCancelListener(cancelListener);
        mDialog.setMessage(context.getString(message));
        mDialog.setIndeterminate(true);
        mDialog.show();
        return mDialog;
    }

    public static ProgressDialog showProgressDialog(Activity context, int message) {
        return showProgressDialog(context, message, null);
    }

    public static void showInputDialog(Activity context, int title, int hint, String prefillInput, final InputCallback callback) {
        final View view = context.getLayoutInflater().inflate(R.layout.dialog_input, null);
        AlertDialog.Builder dialog = new AlertDialog.Builder(context).setTitle(title)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (callback != null) {
                            EditText input = (EditText) view.findViewById(R.id.input);
                            callback.onInput(input.getText().toString().trim());
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setView(view);
        final EditText input = (EditText) view.findViewById(R.id.input);
        if (hint != 0) input.setHint(hint);
        if (prefillInput != null) input.append(prefillInput);
        AlertDialog alert = dialog.create();
        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (callback instanceof InputCancelCallback)
                    ((InputCancelCallback) callback).onCancel();
            }
        });
        alert.show();
    }

    private static boolean cancelledDownload;

    public static void downloadFile(final DrawerActivity context, final File item, final FileCallback callback) {
        final java.io.File downloadDir = new java.io.File(Environment.getExternalStorageDirectory(), "Cabinet");
        if (!downloadDir.exists()) downloadDir.mkdir();
        java.io.File tester = new java.io.File(downloadDir, item.getName());
        if (tester.exists() && tester.length() == item.length()) {
            callback.onFile(new LocalFile(context, tester));
            return;
        }
        final java.io.File dest = new java.io.File(downloadDir, item.getName());
        cancelledDownload = false;
        final ProgressDialog connectDialog = Utils.showProgressDialog(context, R.string.connecting, new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                cancelledDownload = true;
            }
        });
        context.getNetworkService().getSftpClient(new NetworkService.SftpGetCallback() {
            @Override
            public void onSftpClient(final SftpClient client) {
                if (cancelledDownload) return;
                context.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        connectDialog.dismiss();
                        final ProgressDialog downloadDialog = Utils.showProgressDialog(context, R.string.downloading, new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialogInterface) {
                                cancelledDownload = true;
                            }
                        });
                        client.get(item.getPath(), dest.getPath(), new SftpClient.CancelableCompletionCallback() {
                            @Override
                            public void onComplete() {
                                context.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!cancelledDownload) {
                                            downloadDialog.dismiss();
                                            callback.onFile(new LocalFile(context, dest));
                                        } else if (dest.exists()) dest.delete();
                                    }
                                });
                            }

                            @Override
                            public boolean shouldCancel() {
                                return cancelledDownload;
                            }

                            @Override
                            public void onError(final Exception e) {
                                context.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        downloadDialog.dismiss();
                                        Utils.showErrorDialog(context, R.string.failed_download_file, e);
                                    }
                                });
                            }
                        });
                    }
                });
            }

            @Override
            public void onError(final Exception e) {
                context.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        connectDialog.dismiss();
                        Utils.showErrorDialog(context, R.string.failed_connect_server, e);
                    }
                });
            }
        }, (CloudFile) item);
    }

    public static void openFile(final DrawerActivity context, final File item, final boolean openAs) {
        if (item.isRemote()) {
            downloadFile(context, item, new FileCallback() {
                @Override
                public void onFile(File file) {
                    openLocal(context, file, openAs ? null : item.getMimeType(), (CloudFile) item);
                }
            });
            return;
        }
        openLocal(context, item, openAs ? null : item.getMimeType(), null);
    }

    private static void openLocal(final Activity context, final File file, String mime, final CloudFile remoteSource) {
        List<String> textExts = Arrays.asList(context.getResources().getStringArray(R.array.other_text_extensions));
        List<String> codeExts = Arrays.asList(context.getResources().getStringArray(R.array.code_extensions));
        String ext = file.getExtension().toLowerCase(Locale.getDefault());
        if (textExts.contains(ext) || codeExts.contains(ext)) {
            mime = "text/plain";
        }
        if (mime == null) {
            new AlertDialog.Builder(context)
                    .setTitle(R.string.open_as)
                    .setItems(R.array.open_as_array, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String newMime;
                            switch (which) {
                                default:
                                    newMime = "text/*";
                                    break;
                                case 1:
                                    newMime = "image/*";
                                    break;
                                case 2:
                                    newMime = "audio/*";
                                    break;
                                case 3:
                                    newMime = "video/*";
                                    break;
                                case 4:
                                    newMime = "*/*";
                                    break;
                            }
                            openLocal(context, file, newMime, remoteSource);
                        }
                    }).create().show();
            return;
        }
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.fromFile(file.toJavaFile()), mime)
                    .putExtra("remote", remoteSource));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
        }
    }
}