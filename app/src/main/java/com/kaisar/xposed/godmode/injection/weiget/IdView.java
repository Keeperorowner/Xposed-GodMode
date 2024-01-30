package com.kaisar.xposed.godmode.injection.weiget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.TypedValue;
import android.widget.FrameLayout;

import com.kaisar.xposed.godmode.R;
import com.kaisar.xposed.godmode.injection.util.GmResources;

public class IdView extends CancelView {
    public IdView(Context context) {
        super(context);
    }

    @Override
    public void setOpViewName(String pName) {
        super.setOpViewName(pName);
        this.postInvalidate();
    }

    @Override
    protected void initWidget(Context context) {
        super.initWidget(context);
        text = "当前组件id: ";
        rectPaint.setColor(Color.argb(230, 230, 230, 230));
        textPaint.setColor(Color.argb(255, 20, 20, 20));
        textPaint.getTextBounds(text.toString(), 0, text.length(), textBounds);
        textBounds.offsetTo(0, 0);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        setLayoutParams(lp);
    }
}
