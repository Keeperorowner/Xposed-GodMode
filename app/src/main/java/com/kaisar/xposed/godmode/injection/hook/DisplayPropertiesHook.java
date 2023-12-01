package com.kaisar.xposed.godmode.injection.hook;

import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.kaisar.xposed.godmode.injection.util.Logger;
import com.kaisar.xposed.godmode.injection.util.Property;

import java.util.Optional;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

import static com.kaisar.xposed.godmode.GodModeApplication.TAG;

public final class DisplayPropertiesHook extends ASystemPropertiesHook {

    private boolean mDebugLayout;

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void beforeHookedMethod(MethodHookParam param) {
        if (check(param)) {
            param.setResult(Optional.of(true));
        }
    }
}