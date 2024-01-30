package com.kaisar.xposed.godmode.injection.weiget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.kaisar.xposed.godmode.R;
import com.kaisar.xposed.godmode.injection.util.GmResources;
import com.kaisar.xposed.godmode.injection.util.Logger;

import static com.kaisar.xposed.godmode.GodModeApplication.TAG;
import static com.kaisar.xposed.godmode.injection.ViewHelper.TAG_GM_CMP;

/**
 * Created by jrsen on 17-11-4.
 */

@SuppressLint("AppCompatCustomView")
public class CancelView extends View {

    protected final Paint rectPaint = new Paint();
    protected final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected CharSequence text;
    protected Rect statusBarBounds = new Rect();
    protected Rect textLayoutBounds = new Rect();
    protected Rect textBounds = new Rect();
    protected String mViewId = "组件未命名";
    protected Rect idBounds = new Rect();

    public CancelView(Context context) {
        this(context, null);
    }

    public CancelView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CancelView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public CancelView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setTag(TAG_GM_CMP);
        initWidget(context);
    }

    public void setOpViewName(String pName) {
        this.mViewId = pName == null ? "组件未命名" : pName;
        textPaint.getTextBounds(this.mViewId, 0, this.mViewId.length(), idBounds);
        idBounds.offsetTo(0, 0);
    }

    protected void initWidget(Context context) {
        text = GmResources.getText(R.string.top_revert_tip);
        rectPaint.setStyle(Paint.Style.FILL);
        rectPaint.setColor(Color.argb(230, 139, 195, 75));
        textPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15f, getResources().getDisplayMetrics()));
        textPaint.setColor(Color.WHITE);
        textPaint.getTextBounds(text.toString(), 0, text.length(), textBounds);
        textBounds.offsetTo(0, 0);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        setLayoutParams(lp);
        setOpViewName(this.mViewId); //更新id默认位置
    }

    public int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    public void attachToContainer(ViewGroup container) {
        container.addView(this);
    }

    public void detachFromContainer() {
        ViewGroup parent = (ViewGroup) getParent();
        parent.removeView(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        //draw status bar rect
        canvas.drawRect(getStatusBarBounds(), rectPaint);
        Rect textLayoutBounds = getTextLayoutBounds();
        canvas.drawRect(textLayoutBounds, rectPaint);

        //draw text
        float x = textLayoutBounds.centerX() - textBounds.centerX();
        float y = textLayoutBounds.centerY() - textBounds.centerY() - 2;
        canvas.drawText(text, 0, text.length(), x, y, textPaint);
        x = textLayoutBounds.centerX() - idBounds.centerX();
        y = textLayoutBounds.centerY() + idBounds.centerY() * 2 + 2;
        canvas.drawText(this.mViewId, 0, this.mViewId.length(), x, y, textPaint);

        canvas.restore();
    }

    private Rect getStatusBarBounds() {
        if (statusBarBounds.isEmpty())
            statusBarBounds.set(getLeft(), 0, getRight(), getStatusBarHeight());
        return statusBarBounds;
    }

    private Rect getTextLayoutBounds() {
        if (textLayoutBounds.isEmpty()) {
            TypedValue tv = new TypedValue();
            if (getContext().getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
                int actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
                textLayoutBounds.set(getLeft(), getStatusBarHeight(), getRight(), getStatusBarHeight() + actionBarHeight);
            }
        }
        return textLayoutBounds;
    }

    public Rect getRealBounds() {
        return new Rect(statusBarBounds.left, statusBarBounds.top, statusBarBounds.right, textLayoutBounds.bottom);
    }
}
