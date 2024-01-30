package com.kaisar.xposed.godmode.service;

import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kaisar.xposed.godmode.BuildConfig;
import com.kaisar.xposed.godmode.IGodModeManager;
import com.kaisar.xposed.godmode.IObserver;
import com.kaisar.xposed.godmode.RuleProvider;
import com.kaisar.xposed.godmode.injection.GodModeInjector;
import com.kaisar.xposed.godmode.injection.util.Logger;
import com.kaisar.xposed.godmode.rule.ActRules;
import com.kaisar.xposed.godmode.rule.AppRules;
import com.kaisar.xposed.godmode.rule.ViewRule;
import com.kaisar.xposed.godmode.util.BitmapHelper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class RemoteGMManager extends IGodModeManager.Stub {

    private static final ScheduledExecutorService mExec = Executors.newSingleThreadScheduledExecutor();
    public static final RemoteGMManager INSTANCE = new RemoteGMManager();
    private static boolean mInited;
    private static Uri mConfigUri;
    private static WeakReference<Context> mContext = null;
    private static XC_LoadPackage.LoadPackageParam mPParam;

    public static IGodModeManager mGMM = IGodModeManager.Stub.getDefaultImpl();

    public static void init(Context pCon, XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (mInited) return;
        mInited = true;

        if (pCon instanceof Application) {
            mContext = new WeakReference<>(pCon);
        } else {
            mContext = new WeakReference<>(pCon.getApplicationContext());
        }
        mPParam = loadPackageParam;

        mConfigUri = Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".viewRule?pn=" + mContext.get().getPackageName());
        GodModeInjector.notifyEditModeChanged(INSTANCE.isInEditMode());
        updateRules();
    }

    public static void updateRules() {
        GodModeInjector.notifyViewRulesChanged(INSTANCE.getRules(mPParam.packageName));
    }

    @Override
    public boolean hasLight() throws RemoteException {
        return false;
    }

    @Override
    public void setEditMode(boolean enable) throws RemoteException {

    }

    @Override
    public boolean isInEditMode() {
        Uri.Builder tBuilder = mConfigUri.buildUpon().path(RuleProvider.PATH_EDIT_MODE);
        String tStr = mContext.get().getContentResolver().getType(tBuilder.build());
        return !TextUtils.isEmpty(tStr) && Boolean.parseBoolean(tStr);
    }

    @Override
    public void addObserver(String packageName, IObserver observer) throws RemoteException {

    }

    @Override
    public void removeObserver(String packageName, IObserver observer) throws RemoteException {

    }

    @Override
    public AppRules getAllRules() throws RemoteException {
        return null;
    }

    @Override
    public ActRules getRules(String packageName) {
        Uri.Builder tBuilder = mConfigUri.buildUpon().path(RuleProvider.PATH_GET_RULES);
        String tJson = mContext.get().getContentResolver().getType(tBuilder.build());
        if (tJson != null) {
            if (tJson.isEmpty()) return ActRules.EMPTY;
            try {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                ActRules rules = gson.fromJson(tJson, ActRules.class);
                if (rules != null) {
                    Logger.d("GodMode", "已获取到" + rules.size() + "条规则");
                    return rules;
                }
            } catch (Throwable e) {
                Logger.e("GodMode", "获取ViewRule配置时出错!", e);
            }
        }

        Logger.d("GodMode", "未获取到ViewRule配置!");
        return ActRules.EMPTY;
    }

    @Override
    public boolean writeRule(String packageName, ViewRule viewRule, Bitmap pIcon) throws RemoteException {
        Logger.i("GodMode", "新增屏蔽规则: " + viewRule);
        ActRules actRules = GodModeInjector.actRuleProp.get();
        List<ViewRule> viewRules = actRules.get(viewRule.activityClass);
        if (viewRules == null) {
            actRules.put(viewRule.activityClass, viewRules = new ArrayList<>());
        }
        viewRules.add(viewRule);
        //getType测试
        Uri.Builder tBuilder = mConfigUri.buildUpon().path(RuleProvider.PATH_WRITE_RULE);
        //tBuilder.appendQueryParameter("value", i + "");
        String tStr = BitmapHelper.iconToStr(pIcon);
        String tKey = new Random(System.currentTimeMillis()).nextInt(100000000) + "";
        int tAmount = tStr.length() / 100000 + 1;
        tBuilder.appendQueryParameter("key", tKey);
        tBuilder.appendQueryParameter("rule", new Gson().toJson(viewRule));
        tBuilder.appendQueryParameter("amount", tAmount + "");
        if (tAmount == 1) {
            tBuilder.appendQueryParameter("icon", tStr);
        }
        mContext.get().getContentResolver().getType(tBuilder.build());
        if (tAmount > 1) {
            for (int i = 0; i < tAmount; i++) {
                tBuilder = mConfigUri.buildUpon().path(RuleProvider.PATH_RULE_IMG);
                int tEnd = (i + 1) * 100000;
                if (tEnd > tStr.length()) tEnd = tStr.length();
                tBuilder.appendQueryParameter("key", tKey);
                tBuilder.appendQueryParameter("icon", tStr.substring(i * 100000, tEnd));
                mContext.get().getContentResolver().getType(tBuilder.build());
            }
        }


        //aidl测试
        //mGMM.writeRule(packageName, viewRule, pIcon);

        // content provider insert测试
        /*
        Uri.Builder tBuilder = mConfigUri.buildUpon().path(RuleProvider.PATH_WRITE_RULE);
        tBuilder.appendQueryParameter("","");
        ContentValues tValue = new ContentValues();
        tValue.put("rule", new GsonBuilder().create().toJson(viewRule));
        tValue.put("icon", BitmapHelper.iconToBArr(pIcon));
        try {
            ContentResolver tCR = mContext.get().getContentResolver();
            Logger.i("GodMode", tCR.acquireContentProviderClient(tBuilder.build()) + "");
            mContext.get().getContentResolver().insert(tBuilder.build(), tValue);
        } catch (Throwable e) {
            Logger.e("GodMode", "在新增规则时错误", e);
        }
        */

        return true;
    }

    /**
     * 获取指定长度的字符串
     *
     * @param pLen 100的倍数
     * @return
     */
    private static String getStr(int pLen) {
        String tStr = "gxeXLxlY0YwEP61SYcX5Wj28srKBX8UQzCykdlE80hAwsgemXWXtgU2GzmPGckCxv5kSt5wOngOuaXVyVv7Xk0nPacWC6DpuhwdtgxeXLxlY0YwEP61SYcX5Wj28srKBX8UQzCykdlE80hAwsgemXWXtgU2GzmPGckCxv5kSt5wOngOuaXVyVv7Xk0nPacWC6DpuhwdtgxeXLxlY0YwEP61SYcX5Wj28srKBX8UQzCykdlE80hAwsgemXWXtgU2GzmPGckCxv5kSt5wOngOuaXVyVv7Xk0nPacWC6DpuhwdtgxeXLxlY0YwEP61SYcX5Wj28srKBX8UQzCykdlE80hAwsgemXWXtgU2GzmPGckCxv5kSt5wOngOuaXVyVv7Xk0nPacWC6DpuhwdtgxeXLxlY0YwEP61SYcX5Wj28srKBX8UQzCykdlE80hAwsgemXWXtgU2GzmPGckCxv5kSt5wOngOuaXVyVv7Xk0nPacWC6DpuhwdtgxeXLxlY0YwEP61SYcX5Wj28srKBX8UQzCykdlE80hAwsgemXWXtgU2GzmPGckCxv5kSt5wOngOuaXVyVv7Xk0nPacWC6DpuhwdtgxeXLxlY0YwEP61SYcX5Wj28srKBX8UQzCykdlE80hAwsgemXWXtgU2GzmPGckCxv5kSt5wOngOuaXVyVv7Xk0nPacWC6DpuhwdtgxeXLxlY0YwEP61SYcX5Wj28srKBX8UQzCykdlE80hAwsgemXWXtgU2GzmPGckCxv5kSt5wOngOuaXVyVv7Xk0nPacWC6DpuhwdtgxeXLxlY0YwEP61SYcX5Wj28srKBX8UQzCykdlE80hAwsgemXWXtgU2GzmPGckCxv5kSt5wOngOuaXVyVv7Xk0nPacWC6DpuhwdtgxeXLxlY0YwEP61SYcX5Wj28srKBX8UQzCykdlE80hAwsgemXWXtgU2GzmPGckCxv5kSt5wOngOuaXVyVv7Xk0nPacWC6Dpuhwdt";
        StringBuilder tSb = new StringBuilder(tStr);
        while (--pLen > 0) tSb.append(tStr);
        return tSb.toString();
    }

    @Override
    public boolean updateRule(String packageName, ViewRule viewRule) throws RemoteException {
        return false;
    }

    @Override
    public boolean deleteRule(String packageName, ViewRule viewRule) throws RemoteException {
        Uri.Builder tBuilder = mConfigUri.buildUpon().path(RuleProvider.PATH_DELETE_RULE);
        tBuilder.appendQueryParameter("rule", new Gson().toJson(viewRule));
        return Boolean.parseBoolean(mContext.get().getContentResolver().getType(tBuilder.build()));
    }

    @Override
    public boolean deleteRules(String packageName) throws RemoteException {
        return false;
    }

    @Override
    public ParcelFileDescriptor openImageFileDescriptor(String filePath) throws RemoteException {
        return null;
    }
}
