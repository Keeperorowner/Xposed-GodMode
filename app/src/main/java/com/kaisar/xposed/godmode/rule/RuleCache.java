package com.kaisar.xposed.godmode.rule;

import android.view.View;

import java.lang.ref.WeakReference;

public class RuleCache {
    public final WeakReference<View> view;
    public final ViewRule rule;
    public final int pos;

    public RuleCache(View pView, ViewRule pRule, int pPos) {
        this.view = new WeakReference<>(pView);
        this.rule = pRule;
        this.pos = pPos;
    }
}
