package com.kaisar.xposed.godmode.injection;

import static com.kaisar.xposed.godmode.GodModeApplication.TAG;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.kaisar.xposed.godmode.BuildConfig;
import com.kaisar.xposed.godmode.R;
import com.kaisar.xposed.godmode.injection.bridge.GodModeManager;
import com.kaisar.xposed.godmode.injection.bridge.ManagerObserver;
import com.kaisar.xposed.godmode.injection.hook.ActivityLifecycleHook;
import com.kaisar.xposed.godmode.injection.hook.DispatchKeyEventHook;
import com.kaisar.xposed.godmode.injection.hook.DisplayPropertiesHook;
import com.kaisar.xposed.godmode.injection.hook.EventHandlerHook;
import com.kaisar.xposed.godmode.injection.hook.SystemPropertiesHook;
import com.kaisar.xposed.godmode.injection.hook.SystemPropertiesStringHook;
import com.kaisar.xposed.godmode.injection.util.Logger;
import com.kaisar.xposed.godmode.injection.util.PackageManagerUtils;
import com.kaisar.xposed.godmode.injection.util.Property;
import com.kaisar.xposed.godmode.rule.ActRules;
import com.kaisar.xposed.godmode.rule.RuleCache;
import com.kaisar.xposed.godmode.rule.ViewRule;
import com.kaisar.xposed.godmode.service.RemoteGMManager;
import com.kaisar.xposed.godmode.service.RuleUpdateReceiver;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Stack;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by jrsen on 17-10-13.
 */

public final class GodModeInjector implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    public final static Property<Boolean> switchProp = new Property<>();
    public final static Property<ActRules> actRuleProp = new Property<>();
    public static XC_LoadPackage.LoadPackageParam loadPackageParam;
    private static State state = State.UNKNOWN;
    public static final DispatchKeyEventHook dispatchKeyEventHook = new DispatchKeyEventHook();
    public static Stack<RuleCache> mRollbackRules = new Stack<>();

    enum State {
        UNKNOWN,
        ALLOWED,
        BLOCKED
    }

    public static void addRollbackRule(View pV, ViewRule pRule, int pPos) {
        mRollbackRules.push(new RuleCache(pV, pRule, pPos));
    }

    public static void notifyEditModeChanged(boolean enable) {
        if (state == State.UNKNOWN) {
            state = checkBlockList(loadPackageParam.packageName) ? State.BLOCKED : State.ALLOWED;
        }
        if (state == State.ALLOWED) {
            switchProp.set(enable);
        }
        dispatchKeyEventHook.setdisplay(enable);
    }

    public static void notifyViewRulesChanged(ActRules actRules) {
        actRuleProp.set(actRules);
    }

    private static String modulePath;
    public static Resources moduleRes;
    public static boolean mHooked = false;

    // Injector Res
    @Override
    public void initZygote(StartupParam startupParam) {
        modulePath = startupParam.modulePath;
        moduleRes = XModuleResources.createInstance(modulePath, null);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (R.string.res_inject_success >>> 24 == 0x7f) {
            XposedBridge.log("package id must NOT be 0x7f, reject loading...");
            return;
        }
        if (!loadPackageParam.isFirstApplication) {
            return;
        }
        GodModeInjector.loadPackageParam = loadPackageParam;
        final String packageName = loadPackageParam.packageName;
        if ("android".equals(packageName)) {//Run in system process
            /*
            Logger.d(TAG, "inject GodModeManagerService as system service.");
            XServiceManager.initForSystemServer();
            XServiceManager.registerService("godmode", (XServiceManager.ServiceFetcher<Binder>) GodModeManagerService::new);
             */
        } else if (BuildConfig.APPLICATION_ID.equals(packageName)) {
            XposedHelpers.findAndHookMethod(GodModeManager.class.getName(), loadPackageParam.classLoader, "isXpHooked"
                    , XC_MethodReplacement.returnConstant(true));

        } else {//Run in other application processes
            XC_MethodHook tHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Activity activity = (Activity) param.thisObject;
                    dispatchKeyEventHook.setactivity(activity);
                    injectModuleResources(activity.getResources());
                    super.afterHookedMethod(param);
                }
            };
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", tHook);
            registerHook();

            tHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(new GodModeManager(RemoteGMManager.INSTANCE));
                }
            };
            XposedHelpers.findAndHookMethod(GodModeManager.class, "getServerImpl", tHook);

            tHook = new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam pMParam) {
                    if (mHooked) return;
                    Application tApp = (Application) ((pMParam.thisObject instanceof Application) ?
                            pMParam.thisObject : pMParam.args[0]);

                    // 注册规则更新广播
                    try {
                        RemoteGMManager.init(tApp, loadPackageParam);
                        tApp.registerReceiver(new RuleUpdateReceiver(), RuleUpdateReceiver.getIntentFilter());

                        GodModeManager gmManager = GodModeManager.getInstance();
                        gmManager.addObserver(loadPackageParam.packageName, new ManagerObserver());
                        mHooked = true;
                    } catch (Throwable e) {
                        Log.e("GodMode", "Error on hook to " + packageName, e);
                    }

                    /*
                    // 测试使用aidl,由于android11以上的限制,必须在mainfests中注册对应的服务才能使用,此方案作废
                    try {
                        ServiceConnection tConn = new ServiceConnection() {
                            @Override
                            public void onServiceConnected(ComponentName name, IBinder service) {
                                Logger.i("GodMode", "连接到服务器Rule提供器");
                                RemoteGMManager.mGMM = IGodModeManager.Stub.asInterface(service);
                            }

                            @Override
                            public void onServiceDisconnected(ComponentName name) {

                            }
                        };
                        Intent intent = new Intent();
                        intent.setPackage(BuildConfig.APPLICATION_ID);
                        intent.setAction(BuildConfig.APPLICATION_ID + ".aidl.viewRule");
                        if (!tApp.bindService(intent, tConn, Context.BIND_AUTO_CREATE)) {
                            Log.i("GodMode", "Rule服务绑定失败");
                        }
                    } catch (Throwable e) {
                        Log.i("GodMode", "Error on  bind server in " + packageName, e);
                    }

                     */

                }
            };
            XposedHelpers.findAndHookMethod(Application.class, "onCreate", tHook);
            XposedHelpers.findAndHookMethod(Instrumentation.class, "callApplicationOnCreate", Application.class, tHook);

        }
    }

    private void setMetaValue(String newValue, String oldValue, ApplicationInfo applicationInfo) throws Exception {
        Class<?> clazz = Class.forName(applicationInfo.className);
        Field field = clazz.getDeclaredField(oldValue);
        field.setAccessible(true);
        field.set(null, newValue);
    }

    /**
     * Inject resources into hook software - Code from qnotified
     *
     * @param res Inject software resources
     */
    public static void injectModuleResources(Resources res) {
        if (res == null) {
            return;
        }
        try {
            res.getString(R.string.res_inject_success);
            return;
        } catch (Resources.NotFoundException ignored) {
        }
        try {
            String sModulePath = modulePath;
            if (sModulePath == null) {
                throw new RuntimeException(
                        "get module path failed, loader=" + GodModeInjector.class.getClassLoader());
            }
            AssetManager assets = res.getAssets();
            @SuppressLint("DiscouragedPrivateApi")
            Method addAssetPath = AssetManager.class
                    .getDeclaredMethod("addAssetPath", String.class);
            addAssetPath.setAccessible(true);
            int cookie = (int) addAssetPath.invoke(assets, sModulePath);
            try {
                Logger.i(TAG, "injectModuleResources: " + res.getString(R.string.res_inject_success));
            } catch (Resources.NotFoundException e) {
                Logger.e(TAG, "Fatal: injectModuleResources: test injection failure!");
                Logger.e(TAG, "injectModuleResources: cookie=" + cookie + ", path=" + sModulePath
                        + ", loader=" + GodModeInjector.class.getClassLoader());
                long length = -1;
                boolean read = false;
                boolean exist = false;
                boolean isDir = false;
                try {
                    File f = new File(sModulePath);
                    exist = f.exists();
                    isDir = f.isDirectory();
                    length = f.length();
                    read = f.canRead();
                } catch (Throwable e2) {
                    Logger.e(TAG, "Open module error", e2);
                }
                Logger.e(TAG, "sModulePath: exists = " + exist + ", isDirectory = " + isDir + ", canRead = "
                        + read + ", fileLength = " + length);
            }
        } catch (Exception e) {
            Logger.e(TAG, "Inject module resources error", e);
        }
    }

    private static boolean checkBlockList(String packageName) {
        if (TextUtils.equals("com.android.systemui", packageName)) {
            return true;
        }
        try {
            //检查是否为launcher应用
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> resolveInfos;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                resolveInfos = PackageManagerUtils.queryIntentActivities(homeIntent, null, PackageManager.MATCH_ALL, 0);
            } else {
                resolveInfos = PackageManagerUtils.queryIntentActivities(homeIntent, null, 0, 0);
            }
//            Logger.d(TAG, "launcher apps:" + resolveInfos);
            if (resolveInfos != null) {
                for (ResolveInfo resolveInfo : resolveInfos) {
                    if (!TextUtils.equals("com.android.settings", packageName) && TextUtils.equals(resolveInfo.activityInfo.packageName, packageName)) {
                        return true;
                    }
                }
            }

            //检查是否为键盘应用
            Intent keyboardIntent = new Intent("android.view.InputMethod");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                resolveInfos = PackageManagerUtils.queryIntentServices(keyboardIntent, null, PackageManager.MATCH_ALL, 0);
            } else {
                resolveInfos = PackageManagerUtils.queryIntentServices(keyboardIntent, null, 0, 0);
            }
//            Logger.d(TAG, "keyboard apps:" + resolveInfos);
            if (resolveInfos != null) {
                for (ResolveInfo resolveInfo : resolveInfos) {
                    if (TextUtils.equals(resolveInfo.serviceInfo.packageName, packageName)) {
                        return true;
                    }
                }
            }

            //检查是否为无界面应用
            PackageInfo packageInfo = PackageManagerUtils.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES, 0);
            if (packageInfo != null && packageInfo.activities != null && packageInfo.activities.length == 0) {
//                Logger.d(TAG, "no user interface app:" + resolveInfos);
                return true;
            }
        } catch (Throwable t) {
            Logger.e(TAG, "checkWhiteListPackage crash", t);
        }
        return false;
    }

    private void registerHook() {
        //hook activity#lifecycle block view
        ActivityLifecycleHook lifecycleHook = new ActivityLifecycleHook();
        actRuleProp.addOnPropertyChangeListener(lifecycleHook);
        XposedHelpers.findAndHookMethod(Activity.class, "onPostResume", lifecycleHook);
        XposedHelpers.findAndHookMethod(Activity.class, "onDestroy", lifecycleHook);

//        DisplayPropertiesHook displayPropertiesHook = new DisplayPropertiesHook();
//        switchProp.addOnPropertyChangeListener(displayPropertiesHook);
//        XposedHelpers.findAndHookConstructor(View.class, Context.class, displayPropertiesHook);

        // Hook debug layout
        try {
            if (Build.VERSION.SDK_INT < 29) {
                SystemPropertiesHook systemPropertiesHook = new SystemPropertiesHook();
                switchProp.addOnPropertyChangeListener(systemPropertiesHook);
                XposedHelpers.findAndHookMethod("android.os.SystemProperties", ClassLoader.getSystemClassLoader(), "native_get_boolean", String.class, boolean.class, systemPropertiesHook);
            } else {
                SystemPropertiesStringHook systemPropertiesStringHook = new SystemPropertiesStringHook();
                switchProp.addOnPropertyChangeListener(systemPropertiesStringHook);
                XposedBridge.hookAllMethods(XposedHelpers.findClass("android.os.SystemProperties", ClassLoader.getSystemClassLoader()), "native_get", systemPropertiesStringHook);

                DisplayPropertiesHook displayPropertiesHook = new DisplayPropertiesHook();
                switchProp.addOnPropertyChangeListener(displayPropertiesHook);
                XposedHelpers.findAndHookMethod("android.sysprop.DisplayProperties", ClassLoader.getSystemClassLoader(), "debug_layout", displayPropertiesHook);
            }

            //Disable show layout margin bound
            XposedHelpers.findAndHookMethod(ViewGroup.class, "onDebugDrawMargins", Canvas.class, Paint.class, XC_MethodReplacement.DO_NOTHING);

            //Disable GM component show layout bounds
            XC_MethodHook disableDebugDraw = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    View view = (View) param.thisObject;
                    if (ViewHelper.TAG_GM_CMP.equals(view.getTag())) {
                        param.setResult(null);
                    }
                }
            };
            XposedHelpers.findAndHookMethod(ViewGroup.class, "onDebugDraw", Canvas.class, disableDebugDraw);
            XposedHelpers.findAndHookMethod(View.class, "debugDrawFocus", Canvas.class, disableDebugDraw);
        } catch (Throwable e) {
            Logger.e(TAG, "Hook debug layout error", e);
        }

        EventHandlerHook eventHandlerHook = new EventHandlerHook();
        switchProp.addOnPropertyChangeListener(eventHandlerHook);
        //Volume key select
        //XposedHelpers.findAndHookMethod(Activity.class, "dispatchKeyEvent", KeyEvent.class, eventHandlerHook);
        //Drag view support
        XposedHelpers.findAndHookMethod(View.class, "dispatchTouchEvent", MotionEvent.class, eventHandlerHook);
    }

}
