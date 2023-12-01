package com.kaisar.xposed.godmode.injection.hook;

import static com.kaisar.xposed.godmode.GodModeApplication.TAG;

import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.kaisar.xposed.godmode.GodModeApplication;
import com.kaisar.xposed.godmode.injection.util.Logger;
import com.kaisar.xposed.godmode.injection.util.Property;
import com.kaisar.xposed.godmode.util.GodMode;

import java.util.Optional;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public abstract class ASystemPropertiesHook extends XC_MethodHook implements Property.OnPropertyChangeListener<Boolean> {

    protected boolean mDebugLayout;

    public boolean check(MethodHookParam param) {
        return this.mDebugLayout;
    }

    @Override
    abstract protected void beforeHookedMethod(MethodHookParam param);


    @Override
    public void onPropertyChange(Boolean debugLayout) {
        mDebugLayout = debugLayout;
        try {
            @SuppressLint("PrivateApi") Class<?> SystemPropertiesClass = Class.forName("android.os.SystemProperties");
            XposedHelpers.callStaticMethod(SystemPropertiesClass, "callChangeCallbacks");
        } catch (ClassNotFoundException e) {
            Logger.e(TAG, "invoke callChangeCallbacks fail", e);
        }
    }
}
