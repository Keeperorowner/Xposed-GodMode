package com.kaisar.xposed.godmode;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.kaisar.xposed.godmode.service.GodModeManagerService;

public class ViewRuleService extends Service {
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new GodModeManagerService(GodModeApplication.getApplication().getBaseContext());
    }
}
