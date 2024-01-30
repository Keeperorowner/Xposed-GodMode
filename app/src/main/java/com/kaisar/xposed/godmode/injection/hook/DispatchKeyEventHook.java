package com.kaisar.xposed.godmode.injection.hook;

import static com.kaisar.xposed.godmode.GodModeApplication.TAG;
import static com.kaisar.xposed.godmode.injection.util.CommonUtils.recycleNullableBitmap;

import android.animation.Animator;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.util.Pair;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import android.widget.SeekBar;
import android.widget.Toast;

import androidx.appcompat.widget.TooltipCompat;

import com.kaisar.xposed.godmode.BuildConfig;
import com.kaisar.xposed.godmode.GodModeApplication;
import com.kaisar.xposed.godmode.R;
import com.kaisar.xposed.godmode.injection.GodModeInjector;
import com.kaisar.xposed.godmode.injection.ViewController;
import com.kaisar.xposed.godmode.injection.ViewHelper;
import com.kaisar.xposed.godmode.injection.bridge.GodModeManager;
import com.kaisar.xposed.godmode.injection.util.GmResources;
import com.kaisar.xposed.godmode.injection.util.Logger;
import com.kaisar.xposed.godmode.injection.util.Property;
import com.kaisar.xposed.godmode.injection.weiget.CancelView;
import com.kaisar.xposed.godmode.injection.weiget.IdView;
import com.kaisar.xposed.godmode.injection.weiget.MaskView;
import com.kaisar.xposed.godmode.injection.weiget.ParticleView;
import com.kaisar.xposed.godmode.rule.RuleCache;
import com.kaisar.xposed.godmode.rule.ViewRule;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public final class DispatchKeyEventHook extends XC_MethodHook implements Property.OnPropertyChangeListener<Boolean>, SeekBar.OnSeekBarChangeListener {

    private static final int OVERLAY_COLOR = Color.argb(150, 255, 0, 0);
    public final List<WeakReference<View>> mViewNodes = new ArrayList<>();
    public int mCurrentViewIndex = 0;

    private MaskView mMaskView;
    private View mNodeSelectorPanel;
    private Activity activity = null;
    private WeakReference<Activity> preAct = new WeakReference<>(null);
    private SeekBar seekbar = null;
    public static volatile boolean mKeySelecting = false;
    private IdView mIdView = null;

    public void setactivity(final Activity a) {
        if (activity != null && a != activity) setdisplay(false); //app内activity自动切换

        activity = a;
        if (activity != null && GodModeInjector.switchProp.get()) {
            if (this.mMaskView != null) setdisplay(false); //先关闭旧的按钮界面
            setdisplay(true);
        }
    }

    public Activity getCurrentAct() {
        return this.activity;
    }

    public void setdisplay(Boolean display) {
        if (activity == null) return;
        if (display) {
            if (preAct.get() != null && preAct.get() != activity) {
                // 切换activity后,清空用于回滚的规则
                GodModeInjector.mRollbackRules.clear();
            }
            showNodeSelectPanel(activity);
        } else {
            dismissNodeSelectPanel();
        }
    }

    @Override
    protected void beforeHookedMethod(MethodHookParam param) {
        if (GodModeInjector.switchProp.get() && !DispatchTouchEventHook.mDragging) {
            Activity activity = (Activity) param.thisObject;
            KeyEvent event = (KeyEvent) param.args[0];
            param.setResult(dispatchKeyEvent(activity, event));
        }
    }

    private boolean dispatchKeyEvent(final Activity activity, KeyEvent keyEvent) {
        Logger.d(TAG, keyEvent.toString());
        int action = keyEvent.getAction();
        int keyCode = keyEvent.getKeyCode();
        if (action == KeyEvent.ACTION_UP &&
                (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (!mKeySelecting) {
                showNodeSelectPanel(activity);
            } else {
                //hide node select panel
                dismissNodeSelectPanel();
            }
        }
        return true;
    }

    private void showNodeSelectPanel(final Activity activity) {
        mViewNodes.clear();
        mCurrentViewIndex = 0;
        //build view hierarchy tree
        mViewNodes.addAll(ViewHelper.buildViewNodes(activity.getWindow().getDecorView()));
        final ViewGroup container = (ViewGroup) activity.getWindow().getDecorView();
        mMaskView = MaskView.makeMaskView(activity);
        mMaskView.setMaskOverlay(OVERLAY_COLOR);
        mMaskView.attachToContainer(container);
        this.mIdView = new IdView(activity);
        this.mIdView.attachToContainer(container);
        try {
            GodModeInjector.injectModuleResources(activity.getResources());
            LayoutInflater layoutInflater = LayoutInflater.from(activity);
            mNodeSelectorPanel = layoutInflater.inflate(GodModeInjector.moduleRes.getLayout(R.layout.layout_node_selector), container, false);
            seekbar = mNodeSelectorPanel.findViewById(R.id.slider);
            seekbar.setMax(mViewNodes.size() - 1);
            seekbar.setOnSeekBarChangeListener(this);
            View btnBlock = mNodeSelectorPanel.findViewById(R.id.block);
            TooltipCompat.setTooltipText(btnBlock, GmResources.getText(R.string.accessibility_block));
            btnBlock.setOnClickListener(v -> {
                try {
                    mNodeSelectorPanel.setAlpha(0f);
                    final View view = mViewNodes.get(mCurrentViewIndex).get();
                    Logger.d(TAG, "removed view = " + view);
                    if (view != null) {
                        //hide overlay
                        mMaskView.updateOverlayBounds(new Rect());
                        final Bitmap snapshot = ViewHelper.snapshotView(ViewHelper.findTopParentViewByChildView(view));
                        final ViewRule viewRule = ViewHelper.makeRule(view);
                        final ParticleView particleView = new ParticleView(activity);
                        particleView.setDuration(1000);
                        particleView.attachToContainer(container);
                        particleView.setOnAnimationListener(new ParticleView.OnAnimationListener() {
                            @Override
                            public void onAnimationStart(View animView, Animator animation) {
                                viewRule.visibility = View.GONE;
                                ViewController.applyRule(view, viewRule);
                            }

                            @Override
                            public void onAnimationEnd(View animView, Animator animation) {
                                GodModeManager.getInstance().writeRule(activity.getPackageName(), viewRule, snapshot);
                                GodModeInjector.addRollbackRule(view, viewRule,mCurrentViewIndex);
                                recycleNullableBitmap(snapshot);
                                particleView.detachFromContainer();
                                mNodeSelectorPanel.animate()
                                        .alpha(1.0f)
                                        .setInterpolator(new DecelerateInterpolator(1.0f))
                                        .setDuration(300)
                                        .start();
                            }
                        });
                        particleView.boom(view);
                    }
                    mViewNodes.remove(mCurrentViewIndex--);
                    seekbar.setMax(mViewNodes.size() - 1);
                    seekbarreduce();
                } catch (Exception e) {
                    Logger.e(TAG, "block fail", e);
                    Toast.makeText(activity, GmResources.getString(R.string.block_fail, e.getMessage()), Toast.LENGTH_SHORT).show();
                }
            });
            View exchange = mNodeSelectorPanel.findViewById(R.id.exchange);
            View topcentent = mNodeSelectorPanel.findViewById(R.id.topcentent);
            exchange.setOnClickListener(v -> {
                Display display = activity.getWindowManager().getDefaultDisplay();
                int width = display.getWidth();
                int Targetwidth = width - (width / 6);
                if (topcentent.getPaddingRight() == Targetwidth) {
                    topcentent.setPadding(4, 4, 12, 4);
                } else {
                    topcentent.setPadding(4, 4, Targetwidth, 4);
                }
            });
            View btnUp = mNodeSelectorPanel.findViewById(R.id.Up);
            btnUp.setOnClickListener(v -> seekbarreduce());
            View btnDown = mNodeSelectorPanel.findViewById(R.id.Down);
            btnDown.setOnClickListener(v -> seekbaradd());
            container.addView(mNodeSelectorPanel);
            mNodeSelectorPanel.setAlpha(0);
            mNodeSelectorPanel.post(() -> {
                mNodeSelectorPanel.setTranslationX(mNodeSelectorPanel.getWidth() / 2.0f);
                mNodeSelectorPanel.animate()
                        .alpha(1)
                        .translationX(0)
                        .setDuration(300)
                        .setInterpolator(new DecelerateInterpolator(1.0f))
                        .start();
            });
            mKeySelecting = true;
            XposedHelpers.findAndHookMethod(Activity.class, "dispatchKeyEvent", KeyEvent.class, new XC_MethodHook() {
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (GodModeInjector.switchProp.get() && !DispatchTouchEventHook.mDragging) {
                        KeyEvent event = (KeyEvent) param.args[0];
                        int action = event.getAction();
                        int keyCode = event.getKeyCode();
                        if (action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                            seekbaradd();
                        } else if (action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                            seekbarreduce();
                        }
                        param.setResult(true);
                    }
                }
            });
            View btnRollback = mNodeSelectorPanel.findViewById(R.id.Rollback);
            btnRollback.setOnClickListener(v -> rollbackRule());
        } catch (Exception e) {
            //god mode package uninstalled?
            Logger.e(TAG, "showNodeSelectPanel fail", e);
            mKeySelecting = false;
        }
    }

    private void rollbackRule() {
        if (GodModeInjector.mRollbackRules.isEmpty()) {
            Toast.makeText(activity, GmResources.getString(R.string.rollback_none), Toast.LENGTH_SHORT).show();
            return;
        }
        RuleCache pop = GodModeInjector.mRollbackRules.pop();

        if (GodModeManager.getInstance().deleteRule(BuildConfig.APPLICATION_ID, pop.rule)) {
            ViewController.revokeRule(pop.view.get(), pop.rule);
            Toast.makeText(activity, "已经还原 !", Toast.LENGTH_SHORT).show();
            mViewNodes.add(pop.pos, pop.view);
            if (pop.pos <= mCurrentViewIndex) seekbaradd();
            seekbar.setMax(mViewNodes.size() - 1);
        } else {
            Toast.makeText(activity, "天地啊 ! 还原失败了", Toast.LENGTH_SHORT).show();
        }
    }

    private void seekbaradd() {
        if (seekbar.getProgress() == seekbar.getMax()) {
            return;
        }
        int Progress = seekbar.getProgress() + 1;
        seekbar.setProgress(Progress);
        onProgressChanged(seekbar, Progress, true);
    }

    public void seekbarreduce() {
        if (seekbar.getProgress() == 0) {
            return;
        }
        int Progress = seekbar.getProgress() - 1;
        seekbar.setProgress(Progress);
        onProgressChanged(seekbar, Progress, true);
    }

    private void dismissNodeSelectPanel() {
        if (mMaskView != null) {
            mMaskView.detachFromContainer();
            mMaskView = null;
        }
        if (mNodeSelectorPanel != null) {
            final View nodeSelectorPanel = mNodeSelectorPanel;
            nodeSelectorPanel.post(() -> nodeSelectorPanel.animate()
                    .alpha(0)
                    .translationX(nodeSelectorPanel.getWidth() / 2.0f)
                    .setDuration(250)
                    .setInterpolator(new AccelerateInterpolator(1.0f))
                    .withEndAction(() -> {
                        ViewGroup parent = (ViewGroup) nodeSelectorPanel.getParent();
                        if (parent != null) parent.removeView(nodeSelectorPanel);
                    })
                    .start());
        }
        mNodeSelectorPanel = null;
        mViewNodes.clear();
        mCurrentViewIndex = 0;
        mKeySelecting = false;
        if (mIdView != null) {
            mIdView.detachFromContainer();
            mIdView = null;
        }
    }

    @Override
    public void onPropertyChange(Boolean enable) {
        if (mMaskView != null) {
            dismissNodeSelectPanel();
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            mCurrentViewIndex = progress;
            View view = mViewNodes.get(mCurrentViewIndex).get();
            Logger.d(TAG, String.format(Locale.getDefault(), "progress=%d selected view=%s", progress, view));
            if (view != null) {
                mMaskView.updateOverlayBounds(ViewHelper.getLocationInWindow(view));
                this.mIdView.setOpViewName(ViewHelper.getResourceName(view));
            }
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        mNodeSelectorPanel.setAlpha(0.2f);
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        mNodeSelectorPanel.setAlpha(1f);
    }
}
