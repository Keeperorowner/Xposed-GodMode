package com.kaisar.xposed.godmode;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kaisar.xposed.godmode.injection.bridge.GodModeManager;
import com.kaisar.xposed.godmode.injection.util.FileUtils;
import com.kaisar.xposed.godmode.injection.util.Logger;
import com.kaisar.xposed.godmode.rule.ViewRule;
import com.kaisar.xposed.godmode.util.BitmapHelper;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class RuleProvider extends ContentProvider {
    //public static String PATH_RULE_IMG = "/PATH_RULE_IMG";
    public static String PATH_WRITE_RULE = "/PATH_WRITE_RULE";
    public static String PATH_RULE_IMG = "/PATH_WRITE_IMG";
    public static String PATH_GET_RULES = "/PATH_GET_RULES";
    public static String PATH_EDIT_MODE = "/PATH_EDIT_MODE";
    public static String PATH_DELETE_RULE = "/PATH_DELETE_RILE";
    public static ConcurrentHashMap<String, ViewRule> mImgCaches = new ConcurrentHashMap<>();
    private File BASE_DIR;


    @Nullable
    @Override
    public String getType(Uri uri) {
        if (uri.getPath() == null) return null;
        String tPackName = uri.getQueryParameter("pn");
        String tType = uri.getPath();

        try {
            if (tType.equals(PATH_GET_RULES)) {
                return GodModeManager.getInstance().getRules(tPackName).mJson;
            } else if (tType.equals(PATH_EDIT_MODE)) {
                return Boolean.toString(GodModeManager.getInstance().isInEditMode());
            } else if (tType.equals(PATH_WRITE_RULE)) {
                Logger.i("GodMode", "接收来自远端新增规则: " + uri.getQueryParameter("key"));
                ViewRule tRule = new Gson().fromJson(uri.getQueryParameter("rule"), ViewRule.class);
                mImgCaches.put(uri.getQueryParameter("key"), tRule);

                File tTmpFile = new File(this.BASE_DIR, uri.getQueryParameter("key"));
                try {
                    FileUtils.createNewFile(tTmpFile, true);
                } catch (IOException e) {
                    Logger.i("GodMode", "无法创建规则快照临时文件");
                    throw new RuntimeException(e);
                }
                Uri tUri = FileProvider.getUriForFile(this.getContext(), BuildConfig.APPLICATION_ID + ".fileProvider", tTmpFile);
                this.getContext().grantUriPermission(tPackName, tUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                return tUri.toString();
            } else if (tType.equals(PATH_RULE_IMG)) {
                ViewRule tRule = mImgCaches.get(tType = uri.getQueryParameter("key"));
                File tTmpFile = new File(this.BASE_DIR, tType);
                if (tTmpFile.exists() && tRule != null) {
                    byte[] tData = FileUtils.readData(tTmpFile);
                    if (tData.length != 0) {
                        Bitmap a = BitmapHelper.iconFromBArr(tData);
                        GodModeManager.getInstance().writeRule(tPackName, tRule, a);
                    }
                    if (!tTmpFile.delete()) Logger.e("GodMode", "未能删除临时文件");
                }
                mImgCaches.remove(tType);
            }else if (tType.equals(PATH_DELETE_RULE)) {
                Logger.i("GodMode", "删除规则");
                ViewRule tRule = new Gson().fromJson(uri.getQueryParameter("rule"), ViewRule.class);
                return GodModeManager.getInstance().deleteRule(tPackName, tRule) + "";
            }
        }catch (Throwable e){
            Logger.e("GodMode","报错",e);
        }

        return null;
    }

    @Override
    public boolean onCreate() {
        BASE_DIR = GodModeApplication.getBaseDir(getContext());
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        Logger.i("GodMode", "插入请求");
        if (PATH_WRITE_RULE.equals(uri.getPath()) && values != null) {
            String tPackName = uri.getQueryParameter("pn");
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            GodModeManager.getInstance().writeRule(tPackName
                    , gson.fromJson(values.getAsString("rule"), ViewRule.class)
                    , BitmapHelper.iconFromBArr(values.getAsByteArray("icon")));
        }
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    public static class ImgPart {
        private int mLeftAmount;
        public ViewRule viewRule;
        private StringBuilder mSBuilder = new StringBuilder(500000);

        public ImgPart(ViewRule pRule, int pAmount) {
            this.viewRule = pRule;
            this.mLeftAmount = pAmount;
        }

        public boolean addPart(String pPart) {
            this.mSBuilder.append(pPart);
            return --this.mLeftAmount <= 0;
        }

        public Bitmap getIcon() {
            if (mLeftAmount > 0) throw new IllegalStateException("图片数据未全");
            return BitmapHelper.iconFromStr(this.mSBuilder.toString());
        }
    }
}
