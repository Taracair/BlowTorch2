package com.resurrection.blowtorch2.lib.button;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.app.Dialog;
import android.content.Context;
import android.graphics.*;
import android.graphics.Path.Direction;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class ColorPickerDialog extends Dialog {

    public interface OnColorChangedListener {
        void colorChanged(int color);
    }

    private OnColorChangedListener mListener;
    private int mInitialColor;
    //private ButtonEditorDialog.COLOR_FIELDS whichfield;

    private static class ColorPickerView extends View implements SeekBar.OnSeekBarChangeListener {
        private Paint mPaint;
        private Paint mCenterPaint;
        private int[] mColors;
        //private ButtonEditorDialog.COLOR_FIELDS thefield;
        private Path circle_path;
        private Paint mCenterCircle;

        ColorPickerView(Context c, int color) {
            super(c);
           // thefield = usethisfield;
            int alphapart = (0xFF000000&color);
            mColors = new int[] {
                (alphapart|0x00FF0000), (alphapart|0x00FF00FF), (alphapart|0x000000FF), (alphapart|0x0000FFFF), (alphapart|0x0000FF00),
                		(alphapart|0x00FFFF00), (alphapart|0x00FF0000)
            };
            Shader s = new SweepGradient(0, 0, mColors, null);

            //get the screen density.
            float scale = this.getContext().getResources().getDisplayMetrics().density;
            
            //CENTER_X = 125;
            //CENTER_Y = 125;
            //CENTER_RADIUS = 32;
            
            CENTER_X = (int) (83*scale);
            CENTER_Y = (int) (83*scale);
            CENTER_RADIUS = (int) (21*scale);
            
            mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mPaint.setShader(s);
            mPaint.setStyle(Paint.Style.STROKE);
            //mPaint.setStrokeWidth(42);
            mPaint.setStrokeWidth(28*scale);

            mCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mCenterPaint.setColor(color);
            mCenterPaint.setStrokeWidth(3*scale);

            mCenterCircle = new Paint(Paint.ANTI_ALIAS_FLAG);
            mCenterCircle.setColor(0xFFAAAAAA);
            mCenterCircle.setStrokeWidth(2);
            mCenterCircle.setStyle(Paint.Style.STROKE);
            //circle_path = new Path();
           // circle_path.addCircle(0, 0, (float) (CENTER_X - mPaint.getStrokeWidth()*0.5f*0.5), Direction.CW);
            //Matrix m = new Matrix();
            //m.reset();
           // m.postRotate(-90);
           // circle_path.transform(m);
        }

        private boolean mTrackingCenter;
        private boolean mHighlightCenter;

        @Override 
        protected void onDraw(Canvas canvas) {
        	//float scale = this.getContext().getResources().getDisplayMetrics().density;
        	//Log.e("COLORPICK","CENTER_X:" + CENTER_X + " || mPaint.strokewidth" + mPaint.getStrokeWidth() );
        	
            float r = CENTER_X - mPaint.getStrokeWidth()*0.5f;
        	
        	//CENTER_RADIUS = (int) (21*scale);
        	//float r = CENTER_RADIUS;
            circle_path = new Path();
            //circle_path.addCircle(0, 0, (float) (r*0.5), Direction.CW);
            float nr = (float) (mCenterPaint.getStrokeWidth() + CENTER_RADIUS + 5 * this.getContext().getResources().getDisplayMetrics().density);
            circle_path.addOval(new RectF(-nr, -nr, nr, nr), Direction.CW);
            Matrix m = new Matrix();
            m.reset();
            m.postRotate(-190);
            circle_path.transform(m);

            canvas.translate(CENTER_X, CENTER_X);

            canvas.drawPath(circle_path, mCenterCircle);
            
            canvas.drawOval(new RectF(-r, -r, r, r), mPaint);            
            canvas.drawCircle(0, 0, CENTER_RADIUS, mCenterPaint);

            if (mTrackingCenter) {
                int c = mCenterPaint.getColor();
                mCenterPaint.setStyle(Paint.Style.STROKE);

                if (mHighlightCenter) {
                    mCenterPaint.setAlpha(0xFF);
                } else {
                    mCenterPaint.setAlpha(0x80);
                }
                canvas.drawCircle(0, 0,
                                  CENTER_RADIUS + mCenterPaint.getStrokeWidth(),
                                  mCenterPaint);

                mCenterPaint.setStyle(Paint.Style.FILL);
                mCenterPaint.setColor(c);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        	float scale = this.getContext().getResources().getDisplayMetrics().density;
            setMeasuredDimension((int)(83*scale)*2, (int)(83*scale)*2);
            
        }

        private  int CENTER_X = 125;
        private int  CENTER_Y = 125;
        private int CENTER_RADIUS = 32;

        private int ave(int s, int d, float p) {
            return s + java.lang.Math.round(p * (d - s));
        }

        private int interpColor(int colors[], float unit) {
            if (unit <= 0) {
                return colors[0];
            }
            if (unit >= 1) {
                return colors[colors.length - 1];
            }

            float p = unit * (colors.length - 1);
            int i = (int)p;
            p -= i;

            // now p is just the fractional part [0...1) and i is the index
            int c0 = colors[i];
            int c1 = colors[i+1];
            int a = ave(Color.alpha(c0), Color.alpha(c1), p);
            int r = ave(Color.red(c0), Color.red(c1), p);
            int g = ave(Color.green(c0), Color.green(c1), p);
            int b = ave(Color.blue(c0), Color.blue(c1), p);

            return Color.argb(a, r, g, b);
        }

        private static final float PI = 3.1415926f;

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() - CENTER_X;
            float y = event.getY() - CENTER_Y;
            boolean inCenter = java.lang.Math.sqrt(x*x + y*y) <= CENTER_RADIUS;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mTrackingCenter = inCenter;
                    if (inCenter) {
                        mHighlightCenter = true;
                        invalidate();
                        break;
                    }
                case MotionEvent.ACTION_MOVE:
                    if (mTrackingCenter) {
                        if (mHighlightCenter != inCenter) {
                            mHighlightCenter = inCenter;
                            invalidate();
                        }
                    } else {
                        float angle = (float)java.lang.Math.atan2(y, x);
                        // need to turn angle [-PI ... PI] into unit [0....1]
                        float unit = angle/(2*PI);
                        if (unit < 0) {
                            unit += 1;
                        }
                        mCenterPaint.setColor(interpColor(mColors, unit));
                        invalidate();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (mTrackingCenter) {
                        mTrackingCenter = false;
                        invalidate();
                    }
                    break;
            }
            return true;
        }

        int currentColor() {
            return mCenterPaint.getColor();
        }

        void showColor(int color) {
            doUpdate((color >>> 24) & 0xFF, color);
        }

		public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {
			doUpdate(arg0.getProgress(),mCenterPaint.getColor());
		}
		
		public void doUpdate(int newAlpha,int color) {
			//Log.e("COLORPICKER","SEEKBAR UPDATE WITH" + newAlpha);
			int modalpha = newAlpha << 24;
			//Log.e("COLORPICKER","MOD ALPHA IS 0x" + Integer.toHexString(modalpha));
            mColors = new int[] {
                    (modalpha|0x00FF0000), (modalpha|0x00FF00FF), (modalpha|0x000000FF), (modalpha|0x0000FFFF), (modalpha|0x0000FF00),
                    (modalpha|0x00FFFF00), (modalpha|0x00FF0000)
                };
                Shader s = new SweepGradient(0, 0, mColors, null);

                //mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                //mPaint.setShader(s);
                //mPaint.setStyle(Paint.Style.STROKE);
                //mPaint.setStrokeWidth(42);

                //mCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                //mCenterPaint.setColor((modalpha|(color&0x00FFFFFF)));
                //mCenterPaint.setStrokeWidth(5);
                
                float scale = this.getContext().getResources().getDisplayMetrics().density;
                
                mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                mPaint.setShader(s);
                mPaint.setStyle(Paint.Style.STROKE);
                //mPaint.setStrokeWidth(42);
                mPaint.setStrokeWidth(28*scale);

                mCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                mCenterPaint.setColor((modalpha|(color&0x00FFFFFF)));
                mCenterPaint.setStrokeWidth(3*scale);
                
                this.invalidate();
		}

		public void onStartTrackingTouch(SeekBar seekBar) {
			
		}

		public void onStopTrackingTouch(SeekBar seekBar) {
			
		}
    }

    public ColorPickerDialog(Context context,
                             OnColorChangedListener listener,
                             int initialColor) {
        super(context);

        

        
        mListener = listener;
        mInitialColor = initialColor;
        //whichfield = fieldtouse;
    }
    
    public ColorPickerDialog(Context context,
            OnColorChangedListener listener,
            double initialColor) {
    		super(context);




    		mListener = listener;
    		mInitialColor = (int)initialColor;
    		//whichfield = fieldtouse;
    }
    
    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float scale = this.getContext().getResources().getDisplayMetrics().density;

        this.getWindow().setBackgroundDrawableResource(
                com.resurrection.blowtorch2.lib.R.drawable.dialog_window_crawler1);
        this.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        int alphapart = ((mInitialColor & 0xFF000000) >> 24) & 0x000000FF;

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(getContext(), null, 0,
                com.resurrection.blowtorch2.lib.R.style.BlowTorch_Chrome_Title);
        title.setText("COLOR PICKER");
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (42 * scale)));

        final ColorPickerView view = new ColorPickerView(getContext(), mInitialColor);
        LinearLayout.LayoutParams wheelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        wheelLp.gravity = Gravity.CENTER_HORIZONTAL;
        wheelLp.topMargin = (int) (5 * scale);
        root.addView(view, wheelLp);

        final SeekBar alphaBar = new SeekBar(getContext());
        alphaBar.setMax(255);
        alphaBar.setProgress(alphapart);
        alphaBar.setOnSeekBarChangeListener(view);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                (int) (166.66f * scale), LinearLayout.LayoutParams.WRAP_CONTENT);
        barLp.gravity = Gravity.CENTER_HORIZONTAL;
        barLp.topMargin = (int) (5 * scale);
        root.addView(alphaBar, barLp);

        LinearLayout presets = new LinearLayout(getContext());
        presets.setOrientation(LinearLayout.HORIZONTAL);
        presets.setGravity(Gravity.CENTER_VERTICAL);
        presets.setWeightSum(6f);
        LinearLayout.LayoutParams presetLp = new LinearLayout.LayoutParams(
                (int) (166.66f * scale), LinearLayout.LayoutParams.WRAP_CONTENT);
        presetLp.gravity = Gravity.CENTER_HORIZONTAL;
        presetLp.topMargin = (int) (5 * scale);
        presetLp.bottomMargin = (int) (8 * scale);
        // Six equal cells: five greys plus black. Wider tiles used to clip the
        // last swatch on the old 167dp card.
        int[] presetColors = new int[] {
                0xFF3A3A3A, 0xFF666666, 0xFF999999, 0xFFCCCCCC, 0xFFFFFFFF, 0xFF000000};
        int swatchSize = (int) (22 * scale);
        int gap = (int) (3 * scale);
        for (int i = 0; i < presetColors.length; i++) {
            final int presetColor = presetColors[i];
            View presetSwatch = new View(getContext());
            LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(
                    0, swatchSize, 1f);
            swatchParams.setMargins(gap, 0, gap, 0);
            presetSwatch.setLayoutParams(swatchParams);
            GradientDrawable cell = new GradientDrawable();
            cell.setColor(presetColor);
            cell.setCornerRadius(4 * scale);
            cell.setStroke(Math.max(1, (int) scale), 0xFF383E46);
            presetSwatch.setBackground(cell);
            presetSwatch.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    int alpha = alphaBar.getProgress() << 24;
                    view.showColor(alpha | (presetColor & 0x00FFFFFF));
                }
            });
            presets.addView(presetSwatch);
        }
        root.addView(presets, presetLp);

        LinearLayout footer = new LinearLayout(getContext());
        footer.setOrientation(LinearLayout.HORIZONTAL);
        int minButton = (int) (44 * scale);
        Button cancel = new Button(getContext());
        cancel.setText("Cancel");
        cancel.setMinHeight(minButton);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dismiss();
            }
        });
        Button done = new Button(getContext());
        done.setText("Done");
        done.setMinHeight(minButton);
        done.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mListener != null) {
                    mListener.colorChanged(view.currentColor());
                }
                dismiss();
            }
        });
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        footer.addView(cancel, half);
        footer.addView(done, half);
        root.addView(footer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        Window window = getWindow();
        if (window != null) {
            int width = (int) (getContext().getResources().getDisplayMetrics().widthPixels
                    * 0.92f);
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }
    }
}
