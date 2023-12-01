package com.kaisar.xposed.godmode.util;

public class GodMode {

    public static final String ClassPackName = "com.kaisar.xposed.godmode";
    public static final String AppPackName = "com.viewblocker.jrsen";

    public static boolean isSelfAppPack(String pPack) {
        return AppPackName.equals(pPack);
    }
}
