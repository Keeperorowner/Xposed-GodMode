package com.kaisar.xposed.godmode.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;

import com.kaisar.xposed.godmode.BuildConfig;
import com.kaisar.xposed.godmode.injection.GodModeInjector;

public class RuleUpdateReceiver extends BroadcastReceiver {

    private static Handler mHandler = new Handler(Looper.getMainLooper());
    public static String MSG_EDIT_MODE = BuildConfig.APPLICATION_ID + ".editMode";
    public static String MSG_UPDATE_RULE = BuildConfig.APPLICATION_ID + ".updateRule";

    public static IntentFilter getIntentFilter() {
        IntentFilter tFilter = new IntentFilter();
        tFilter.addAction(MSG_EDIT_MODE);
        tFilter.addAction(MSG_UPDATE_RULE);
        return tFilter;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String tAct = intent.getAction();
        if (tAct.equals(MSG_EDIT_MODE)) {
            GodModeInjector.notifyEditModeChanged(intent.getBooleanExtra("value", false));
        } else if (tAct.equals(MSG_UPDATE_RULE)) {
            mHandler.post(RemoteGMManager::updateRules);
        }
    }
}
