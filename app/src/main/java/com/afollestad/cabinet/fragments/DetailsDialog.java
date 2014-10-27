package com.afollestad.cabinet.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.utils.Perm;
import com.afollestad.cabinet.utils.TimeUtils;

import java.util.GregorianCalendar;

public class DetailsDialog extends DialogFragment implements CompoundButton.OnCheckedChangeListener {

    public DetailsDialog() {
    }

    public static DetailsDialog create(File file) {
        DetailsDialog dialog = new DetailsDialog();
        Bundle args = new Bundle();
        args.putSerializable("file", file);
        dialog.setArguments(args);
        return dialog;
    }

    private TextView body;
    private File file;

    private Spanned getBody(boolean loadDirContents) {
        String content;
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTimeInMillis(file.lastModified());
        if (file.isDirectory()) {
            String size = getString(R.string.unavailable);
            if (!file.isRemote()) {
                if (loadDirContents) size = file.getSizeString();
                else {
                    size = getString(R.string.loading);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            final Spanned newBody = getBody(true);
                            if (getActivity() == null) return;
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    body.setText(newBody);
                                }
                            });
                        }
                    }).start();
                }
            }
            if (getActivity() == null) return null;
            content = getString(R.string.details_body_dir,
                    file.getName(), file.getPath(), size, TimeUtils.toStringLong(cal));
        } else {
            if (getActivity() == null) return null;
            content = getString(R.string.details_body_file,
                    file.getName(), file.getPath(), file.getSizeString(), TimeUtils.toStringLong(cal),
                    owner + "" + group + "" + other);
        }
        return Html.fromHtml(content);
    }

    private CheckBox ownerR;
    private CheckBox ownerW;
    private CheckBox ownerX;
    private CheckBox groupR;
    private CheckBox groupW;
    private CheckBox groupX;
    private CheckBox otherR;
    private CheckBox otherW;
    private CheckBox otherX;
    private int owner;
    private int group;
    private int other;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        file = (File) getArguments().getSerializable("file");
        file.setContext(getActivity());
        LayoutInflater layoutInflater = getActivity().getLayoutInflater();
        View rootView = layoutInflater.inflate(R.layout.dialog_custom, null);

        TextView title = (TextView) rootView.findViewById(R.id.title);
        ownerR = (CheckBox) rootView.findViewById(R.id.ownerR);
        ownerW = (CheckBox) rootView.findViewById(R.id.ownerW);
        ownerX = (CheckBox) rootView.findViewById(R.id.ownerX);
        groupR = (CheckBox) rootView.findViewById(R.id.groupR);
        groupW = (CheckBox) rootView.findViewById(R.id.groupW);
        groupX = (CheckBox) rootView.findViewById(R.id.groupX);
        otherR = (CheckBox) rootView.findViewById(R.id.otherR);
        otherW = (CheckBox) rootView.findViewById(R.id.otherW);
        otherX = (CheckBox) rootView.findViewById(R.id.otherX);

        ownerR.setOnCheckedChangeListener(this);
        ownerW.setOnCheckedChangeListener(this);
        ownerX.setOnCheckedChangeListener(this);
        groupR.setOnCheckedChangeListener(this);
        groupW.setOnCheckedChangeListener(this);
        groupX.setOnCheckedChangeListener(this);
        otherR.setOnCheckedChangeListener(this);
        otherW.setOnCheckedChangeListener(this);
        otherX.setOnCheckedChangeListener(this);

        title.setText(R.string.details);
        body = (TextView) rootView.findViewById(R.id.body);
        body.setText(getBody(false));
        return new AlertDialog.Builder(getActivity())
                .setView(rootView)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        dialog.dismiss();
                        applyPermissionsIfNecessary();
                    }
                }).create();
    }

    private void invalidatePermissions() {
        owner = 0;
        if (ownerR.isChecked()) owner += Perm.READ;
        if (ownerW.isChecked()) owner += Perm.WRITE;
        if (ownerX.isChecked()) owner += Perm.EXECUTE;
        group = 0;
        if (groupR.isChecked()) group += Perm.READ;
        if (groupW.isChecked()) group += Perm.WRITE;
        if (groupX.isChecked()) group += Perm.EXECUTE;
        other = 0;
        if (otherR.isChecked()) other += Perm.READ;
        if (otherW.isChecked()) other += Perm.WRITE;
        if (otherX.isChecked()) other += Perm.EXECUTE;
    }

    private void applyPermissionsIfNecessary() {
        Perm.chmod(file, owner, group, other, new Perm.Callback() {
            @Override
            public void onComplete(boolean result, String error) {
                Log.v("DetailsDialog", result + ": " + error);
            }
        });
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        invalidatePermissions();
        body.setText(getBody(false));
    }
}