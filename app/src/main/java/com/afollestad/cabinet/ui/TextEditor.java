package com.afollestad.cabinet.ui;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.file.LocalFile;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.file.root.RootFile;
import com.afollestad.cabinet.fragments.DetailsDialog;
import com.afollestad.cabinet.sftp.SftpClient;
import com.afollestad.cabinet.ui.base.NetworkedActivity;
import com.afollestad.cabinet.utils.Utils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class TextEditor extends NetworkedActivity implements TextWatcher {

    private EditText mInput;
    private java.io.File mFile;
    private String mOriginal;
    private Timer mTimer;
    private boolean mModified;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_texteditor);

        mInput = (EditText) findViewById(R.id.input);
        mInput.addTextChangedListener(this);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (getIntent().getData() != null) load(getIntent().getData());
        else mInput.setVisibility(View.VISIBLE);
    }

    @Override
    protected boolean disconnectOnNotify() {
        return false;
    }

    private void setProgress(boolean show) {
        mInput.setVisibility(show ? View.GONE : View.VISIBLE);
        findViewById(R.id.progress).setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void load(final Uri uri) {
        setProgress(true);
        Log.v("TextEditor", "Loading...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                mFile = new java.io.File(uri.getPath());
                if (!mFile.exists()) {
                    Log.v("TextEditor", "File doesn't exist...");
                    finish();
                    return;
                }

                if (new LocalFile(TextEditor.this, mFile).requiresRoot()) {
                    new RootFile(TextEditor.this, mFile).mountParent(true);
                }

                String ext = File.getExtension(TextEditor.this, mFile.getName()).toLowerCase(Locale.getDefault());
                String mime = File.getMimeType(TextEditor.this, ext);
                Log.v("TextEditor", "Mime: " + mime);
                List<String> textExts = Arrays.asList(getResources().getStringArray(R.array.other_text_extensions));
                List<String> codeExts = Arrays.asList(getResources().getStringArray(R.array.code_extensions));
                if (!mime.startsWith("text/") && !textExts.contains(ext) && !codeExts.contains(ext)) {
                    Log.v("TextEditor", "Unsupported extension");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            new AlertDialog.Builder(TextEditor.this)
                                    .setTitle(R.string.unsupported_extension)
                                    .setMessage(R.string.unsupported_extension_desc)
                                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            finish();
                                        }
                                    }).create().show();
                        }
                    });
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setTitle(mFile.getName());
                    }
                });
                Log.v("TextEditor", "Reading file...");
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(mFile), "UTF-8"));
                    String line;
                    final StringBuilder text = new StringBuilder();
                    try {
                        while ((line = br.readLine()) != null) {
                            text.append(line);
                            text.append('\n');
                        }
                    } catch (final OutOfMemoryError e) {
                        e.printStackTrace();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Utils.showErrorDialog(TextEditor.this, e.getLocalizedMessage());
                            }
                        });
                    }
                    br.close();
                    Log.v("TextEditor", "Setting contents to input area...");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                mOriginal = text.toString();
                                text.setLength(0); // clear string builder to reduce memory usage
                                mInput.setText(mOriginal);
                            } catch (OutOfMemoryError e) {
                                Utils.showErrorDialog(TextEditor.this, e.getLocalizedMessage());
                            }
                            setProgress(false);
                        }
                    });
                } catch (final IOException e) {
                    Log.v("TextEditor", "Error: " + e.getMessage());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Utils.showErrorDialog(TextEditor.this, e.getLocalizedMessage());
                            setProgress(false);
                        }
                    });
                }
            }
        }).start();
    }

    private void upload(final boolean exitAfter) {
        if (getRemoteSwitch() == null) {
            if (exitAfter) finish();
            return;
        }
        new LocalFile(this, mFile).copy(getRemoteSwitch(), new SftpClient.FileCallback() {
            @Override
            public void onComplete(File file) {
                if (exitAfter) finish();
            }

            @Override
            public void onError(Exception e) {
                // Dialog is already shown, can ignore this
            }
        }, false);
    }

    private void save(final boolean exitAfter) {
        final ProgressDialog mDialog = new ProgressDialog(this);
        mDialog.setIndeterminate(true);
        mDialog.setMessage(getString(R.string.saving));
        mDialog.setCancelable(false);
        mDialog.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (new LocalFile(TextEditor.this, mFile).requiresRoot()) {
                        new RootFile(TextEditor.this, mFile).mountParent(true);
                    }

                    FileOutputStream os = new FileOutputStream(mFile);
                    os.write(mInput.getText().toString().getBytes("UTF-8"));
                    os.close();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mDialog.dismiss();
                            if (exitAfter) {
                                mOriginal = null;
                                upload(exitAfter);
                            } else {
                                mOriginal = mInput.getText().toString();
                                mModified = false;
                                invalidateOptionsMenu();
                                upload(exitAfter);
                            }
                        }
                    });
                } catch (final IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Utils.showErrorDialog(TextEditor.this, e.getLocalizedMessage());
                            mDialog.dismiss();
                        }
                    });
                } finally {
                    if (new LocalFile(TextEditor.this, mFile).requiresRoot()) {
                        new RootFile(TextEditor.this, mFile).unmountParent(true);
                    }
                }
            }
        }).start();
    }

    @Override
    public void onBackPressed() {
        checkUnsavedChanges();
    }

    private void checkUnsavedChanges() {
        if (mOriginal != null && !mOriginal.equals(mInput.getText().toString())) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.unsaved_changes)
                    .setMessage(R.string.unsaved_changes_desc)
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            save(true);
                        }
                    })
                    .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    }).create().show();
        } else finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.text_editor, menu);
        menu.findItem(R.id.save).setVisible(mModified);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            checkUnsavedChanges();
            return true;
        } else if (item.getItemId() == R.id.save) {
            save(false);
            return true;
        } else if (item.getItemId() == R.id.details) {
            DetailsDialog.create(new LocalFile(this, mFile)).show(getFragmentManager(), "DETAILS_DIALOG");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer.purge();
            mTimer = null;
        }
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mModified = !mInput.getText().toString().equals(mOriginal);
                invalidateOptionsMenu();
            }
        }, 250);
    }

    @Override
    public void afterTextChanged(Editable editable) {
    }
}
