package com.kaisar.xposed.godmode.injection.hook;


import android.annotation.SuppressLint;

import com.kaisar.xposed.godmode.injection.util.Logger;
import com.kaisar.xposed.godmode.injection.util.Property;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

import static com.kaisar.xposed.godmode.GodModeApplication.TAG;

public final class SystemPropertiesHook extends ASystemPropertiesHook {

    private boolean mDebugLayout;

    @Override
    protected void beforeHookedMethod(MethodHookParam param) {
        if (check(param)&&"debug.layout".equals(param.args[0])) {
            param.setResult(true);
        }
    }
}