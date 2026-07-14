package com.offsetnull.bt.window;

import android.content.Context;
import androidx.appcompat.widget.Toolbar;
import android.util.AttributeSet;
import android.view.MotionEvent;

public class NoTouchActionBar extends Toolbar {

    public NoTouchActionBar(Context c) {
        super(c);
        initPassthrough();
    }

    public NoTouchActionBar(Context c, AttributeSet s) {
        super(c, s);
        initPassthrough();
    }

    public NoTouchActionBar(Context c, AttributeSet s, int d) {
        super(c, s, d);
        initPassthrough();
    }

    private void initPassthrough() {
        setClickable(false);
        setFocusable(false);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return false;
    }
}
