package com.kaisar.xposed.godmode.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.io.ByteArrayOutputStream;

public class BitmapHelper {

    public static byte[] iconToBArr(Bitmap pBitMap) {
        ByteArrayOutputStream tBAOStream = new ByteArrayOutputStream();
        pBitMap.compress(Bitmap.CompressFormat.PNG, 100, tBAOStream);

        return tBAOStream.toByteArray();
    }

    public static Bitmap iconFromBArr(byte[] pImgBArr) {
        try {
            return BitmapFactory.decodeByteArray(pImgBArr, 0, pImgBArr.length);
        } catch (Exception e) {
            return Bitmap.createBitmap(16, 16, Bitmap.Config.ALPHA_8);
        }
    }

    public static String iconToStr(Bitmap pBitMap) {
        return Base64.encodeToString(iconToBArr(pBitMap), Base64.NO_WRAP);
    }

    public static Bitmap iconFromStr(String pImgStr) {
        return iconFromBArr(Base64.decode(pImgStr, Base64.NO_WRAP));
    }
}
