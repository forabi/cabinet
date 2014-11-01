package com.afollestad.cabinet.ui;

import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.cab.PickerCab;
import com.afollestad.cabinet.cab.base.BaseCab;
import com.afollestad.cabinet.cab.base.BaseFileCab;
import com.afollestad.cabinet.file.LocalFile;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.fragments.DirectoryFragment;
import com.afollestad.cabinet.fragments.NavigationDrawerFragment;
import com.afollestad.cabinet.fragments.WelcomeFragment;
import com.afollestad.cabinet.ui.base.NetworkedActivity;
import com.afollestad.cabinet.utils.Pins;
import com.anjlab.android.iab.v3.BillingProcessor;
import com.anjlab.android.iab.v3.TransactionDetails;
import com.melnykov.fab.FloatingActionButton;
import com.readystatesoftware.systembartint.SystemBarTintManager;

public class DrawerActivity extends NetworkedActivity implements BillingProcessor.IBillingHandler {

    public interface FabListener {
        public abstract void onFabPressed(BaseFileCab.PasteMode pasteMode);
    }

    private BillingProcessor mBP; // used for donations
    private BaseCab mCab; // the current contextual action bar, saves state throughout fragments

    public FloatingActionButton fab; // the floating blue add/paste button
    private float fabVisibleY; // saves y position of the top of the visible fab
    private float fabHiddenY; // saves y position of the top of the hidden fab
    private boolean fabShown = true; // flag indicating whether the fab is currently visible
    private FabListener mFabListener; // a callback used to notify DirectoryFragment of fab press
    public BaseFileCab.PasteMode fabPasteMode = BaseFileCab.PasteMode.DISABLED;
    private boolean fabDisabled; // flag indicating whether fab should stay hidden while scrolling
    public boolean shouldAttachFab; // used during config change, tells fragment to reattach to cab
    public boolean pickMode; // flag indicating whether user is picking a file for another app
    public DrawerLayout mDrawerLayout;

    // Both fields used in waitFabInvalidate() so that they can be initialized on UI thread
    private SystemBarTintManager mTintManager;
    SystemBarTintManager.SystemBarConfig mTintConfig;

    public BaseCab getCab() {
        return mCab;
    }

    public void setCab(BaseCab cab) {
        mCab = cab;
    }

    public void invalidateSystemBarTintManager() {
        if (mTintManager == null)
            mTintManager = new SystemBarTintManager(DrawerActivity.this);
        if (mTintConfig == null)
            mTintConfig = mTintManager.getConfig();
    }

    public void waitFabInvalidate() {
        if (mTintManager == null || mTintConfig == null)
            throw new RuntimeException("Tint manager and config have not be initialized yet.");
        final float translation = getResources().getDimension(R.dimen.fab_translation) + mTintConfig.getPixelInsetBottom();
        while (fabVisibleY == 0) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    fabVisibleY = fab.getY();
                    fabHiddenY = fab.getY() + translation;
                }
            });
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // Make sure memory is released
        mTintManager = null;
        mTintConfig = null;
    }

    public void toggleFab(boolean hide) {
        if (fabVisibleY == 0) {
            SystemBarTintManager tintManager = new SystemBarTintManager(this);
            SystemBarTintManager.SystemBarConfig config = tintManager.getConfig();
            float translation = getResources().getDimension(R.dimen.fab_translation) + config.getPixelInsetBottom();
            fabVisibleY = fab.getY();
            fabHiddenY = fab.getY() + translation;
            Log.v("Fab", "Invalidate position– top: " + fabVisibleY + ", bottom: " + fabHiddenY);
        }
        if (hide) {
            if (fabShown) {
                ObjectAnimator outAnim = ObjectAnimator.ofFloat(fab, "y", fabVisibleY, fabHiddenY);
                outAnim.setDuration(250);
                outAnim.setInterpolator(new AccelerateInterpolator());
                outAnim.start();
                fabShown = false;
            }
        } else {
            if (!fabShown && !fabDisabled) {
                ObjectAnimator inAnim = ObjectAnimator.ofFloat(fab, "y", fabHiddenY, fabVisibleY);
                inAnim.setDuration(250);
                inAnim.setInterpolator(new DecelerateInterpolator());
                inAnim.start();
                fabShown = true;
            }
        }
    }

    public void disableFab(boolean disable) {
        fabDisabled = disable;
        toggleFab(disable);
    }

    public void setFabListener(FabListener mFabListener) {
        this.mFabListener = mFabListener;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        if (mCab != null && mCab.isActive())
            outState.putSerializable("cab", mCab);
        outState.putSerializable("fab_pastemode", fabPasteMode);
        outState.putBoolean("fab_disabled", fabDisabled);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (getFragmentManager().getBackStackEntryCount() == 0) {
            super.onBackPressed();
        } else getFragmentManager().popBackStack();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drawer);

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey("cab")) {
                mCab = (BaseCab) savedInstanceState.getSerializable("cab");
                if (mCab instanceof BaseFileCab) {
                    shouldAttachFab = true;
                } else {
                    if (mCab instanceof PickerCab) pickMode = true;
                    mCab.setContext(this).start();
                }
            }
            fabPasteMode = (BaseFileCab.PasteMode) savedInstanceState.getSerializable("fab_pastemode");
            fabDisabled = savedInstanceState.getBoolean("fab_disabled");
        }

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        NavigationDrawerFragment mNavigationDrawerFragment = (NavigationDrawerFragment) getFragmentManager().findFragmentById(R.id.navigation_drawer);
        mNavigationDrawerFragment.setUp(R.id.navigation_drawer, mDrawerLayout, savedInstanceState == null);

        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mFabListener != null) mFabListener.onFabPressed(fabPasteMode);
            }
        });
        fab.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                Toast.makeText(DrawerActivity.this, fabPasteMode == BaseFileCab.PasteMode.ENABLED ? R.string.paste : R.string.newStr, Toast.LENGTH_SHORT).show();
                return false;
            }
        });

        mBP = new BillingProcessor(this, "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAlPBB2hP/R0PrXtK8NPeDX7QV1fvk1hDxPVbIwRZLIgO5l/ZnAOAf8y9Bq57+eO5CD+ZVTgWcAVrS/QsiqDI/MwbfXcDydSkZLJoFofOFXRuSL7mX/jNwZBNtH0UrmcyFx1RqaHIe9KZFONBWLeLBmr47Hvs7dKshAto2Iy0v18kN48NqKxlWtj/PHwk8uIQ4YQeLYiXDCGhfBXYS861guEr3FFUnSLYtIpQ8CiGjwfU60+kjRMmXEGnmhle5lqzj6QeL6m2PNrkbJ0T9w2HM+bR7buHcD8e6tHl2Be6s/j7zn1Ypco/NCbqhtPgCnmLpeYm8EwwTnH4Yei7ACR7mXQIDAQAB", this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        processIntent(intent, null);
    }

    private void checkRating() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.getBoolean("shown_rating_dialog", false)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.rate)
                    .setMessage(R.string.rate_desc)
                    .setPositiveButton(R.string.sure, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            PreferenceManager.getDefaultSharedPreferences(DrawerActivity.this)
                                    .edit().putBoolean("shown_rating_dialog", true).commit();
                            startActivity(new Intent(Intent.ACTION_VIEW)
                                    .setData(Uri.parse("market://details?id=com.afollestad.cabinet")));
                        }
                    })
                    .setNeutralButton(R.string.later, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .setNegativeButton(R.string.no_thanks, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            PreferenceManager.getDefaultSharedPreferences(DrawerActivity.this)
                                    .edit().putBoolean("shown_rating_dialog", true).commit();
                        }
                    })
                    .create()
                    .show();
        }
    }

    @Override
    protected void processIntent(Intent intent, Bundle savedInstanceState) {
        super.processIntent(intent, savedInstanceState);
        pickMode = intent.getAction() != null && intent.getAction().equals(Intent.ACTION_GET_CONTENT);
        if (pickMode) {
            setCab(new PickerCab().setContext(this).start());
        } else if (getRemoteSwitch() != null && savedInstanceState == null) {
            if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean("shown_welcome", false)) {
                getFragmentManager().beginTransaction().replace(R.id.container, new WelcomeFragment()).commit();
            } else {
                checkRating();
                switchDirectory(null, true);
            }
        }
    }

    public void reloadNavDrawer(boolean open) {
        ((NavigationDrawerFragment) getFragmentManager().findFragmentByTag("NAV_DRAWER")).reload(open);
    }

    public void reloadNavDrawer() {
        reloadNavDrawer(false);
    }

    public void switchDirectory(Pins.Item to) {
        File file = to.toFile(this);
        switchDirectory(file, file.isStorageDirectory(), false);
    }

    @Override
    public void switchDirectory(File to, boolean clearBackStack) {
        switchDirectory(to, clearBackStack, true);
    }

    public void switchDirectory(File to, boolean clearBackStack, boolean animate) {
        if (to == null) to = new LocalFile(this, Environment.getExternalStorageDirectory());
        if (clearBackStack)
            getFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        FragmentTransaction trans = getFragmentManager().beginTransaction();
        if (animate && !clearBackStack)
            trans.setCustomAnimations(R.anim.frag_enter, R.anim.frag_exit);
        trans.replace(R.id.container, DirectoryFragment.create(to));
        if (!clearBackStack) trans.addToBackStack(null);
        try {
            trans.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void search(File currentDir, String query) {
        getFragmentManager().beginTransaction().replace(R.id.container,
                DirectoryFragment.create(currentDir, query)).addToBackStack(null).commit();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            if (mCab != null && mCab.isActive()) {
                onBackPressed();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    /* Donation stuff via in app billing */

    @Override
    public void onBillingInitialized() {
    }

    @Override
    public void onProductPurchased(String productId, TransactionDetails transactionDetails) {
        mBP.consumePurchase(productId);
        Toast.makeText(this, R.string.thank_you, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBillingError(int errorCode, Throwable error) {
        if (errorCode != 110) {
            Toast.makeText(this, "Billing error: code = " + errorCode + ", error: " +
                    (error != null ? error.getMessage() : "?"), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onPurchaseHistoryRestored() {
        /*
         * Called then purchase history was restored and the list of all owned PRODUCT ID's
         * was loaded from Google Play
         */
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!mBP.handleActivityResult(requestCode, resultCode, data))
            super.onActivityResult(requestCode, resultCode, data);
    }

    public void donate(int index) {
        mBP.purchase("donation" + index);
    }

    @Override
    public void onDestroy() {
        if (mBP != null) mBP.release();
        super.onDestroy();
    }
}
