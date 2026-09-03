package com.resurrection.blowtorch2.lib.responder.color;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.button.ColorPickerDialog;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.TriggerResponderEditorDoneListener;
import com.resurrection.blowtorch2.lib.service.Colorizer;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.TabWidget;
import android.widget.TextView;

public class ColorActionEditor extends Dialog {

	private static final int TAB_256 = 0;
	private static final int TAB_RGB = 1;
	private static final int PREVIEW_KEEP_FG = 0xFFBBBBBB;

	private final TriggerResponder original;
	private final TriggerResponderEditorDoneListener finish_with;

	private TriggerColorPaint paint;
	private boolean editingForeground = true;
	private boolean lastFgWasRgb;
	private boolean lastBgWasRgb;
	private boolean suppress;

	private TextView preview;
	private CheckBox colorText;
	private CheckBox colorBackground;
	private CheckBox defaultPaper;
	private RadioGroup channelGroup;
	private RadioButton foregroundRadio;
	private RadioButton backgroundRadio;
	private TabHost host;
	private Xterm256PaletteView palette;
	private EditText hexField;
	private Button wheelButton;
	private CheckBox styleBold;
	private CheckBox styleFaint;
	private CheckBox styleItalic;
	private CheckBox styleUnderline;
	private CheckBox styleReverse;
	private CheckBox styleStrike;

	public ColorActionEditor(Context context, TriggerResponder original,
			TriggerResponderEditorDoneListener listener) {
		super(context);
		this.original = original;
		finish_with = listener;
	}

	@Override
	public void onCreate(Bundle b) {
		this.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		this.getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		this.setContentView(R.layout.responder_color_dialog);

		preview = (TextView) findViewById(R.id.color_preview);
		colorText = (CheckBox) findViewById(R.id.color_text_check);
		colorBackground = (CheckBox) findViewById(R.id.color_background_check);
		defaultPaper = (CheckBox) findViewById(R.id.color_default_paper_check);
		channelGroup = (RadioGroup) findViewById(R.id.color_channel_group);
		foregroundRadio = (RadioButton) findViewById(R.id.color_channel_foreground);
		backgroundRadio = (RadioButton) findViewById(R.id.color_channel_background);
		palette = (Xterm256PaletteView) findViewById(R.id.color_xterm_palette);
		hexField = (EditText) findViewById(R.id.color_hex);
		wheelButton = (Button) findViewById(R.id.color_wheel);
		styleBold = (CheckBox) findViewById(R.id.color_style_bold);
		styleFaint = (CheckBox) findViewById(R.id.color_style_faint);
		styleItalic = (CheckBox) findViewById(R.id.color_style_italic);
		styleUnderline = (CheckBox) findViewById(R.id.color_style_underline);
		styleReverse = (CheckBox) findViewById(R.id.color_style_reverse);
		styleStrike = (CheckBox) findViewById(R.id.color_style_strike);

		host = (TabHost) findViewById(android.R.id.tabhost);
		host.setup();
		TabSpec tab256 = host.newTabSpec("256");
		tab256.setIndicator("256");
		tab256.setContent(R.id.color_tab_256);
		TabSpec tabRgb = host.newTabSpec("RGB");
		tabRgb.setIndicator("RGB");
		tabRgb.setContent(R.id.color_tab_rgb);
		host.addTab(tab256);
		host.addTab(tabRgb);
		styleTabs();
		sizeScrollForScreen();

		if (original != null) {
			paint = ((ColorAction) original).getPaint().copy();
		} else {
			paint = TriggerColorPaint.legacyDefaults();
		}
		lastFgWasRgb = paint.getFgMode() == TriggerColorPaint.FgMode.RGB;
		lastBgWasRgb = paint.getBgMode() == TriggerColorPaint.BgMode.RGB;
		if (paint.paintsForeground()) {
			editingForeground = true;
		} else if (paint.paintsBackground()) {
			editingForeground = false;
		} else {
			editingForeground = true;
		}

		wireListeners();
		loadWidgetsFromPaint();
		updatePreview();

		findViewById(R.id.done).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				doExit();
			}
		});
		findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				ColorActionEditor.this.dismiss();
			}
		});
	}

	private void wireListeners() {
		CompoundButton.OnCheckedChangeListener colorBoxes =
				new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (suppress) {
					return;
				}
				onColorFlagsChanged(buttonView);
			}
		};
		colorText.setOnCheckedChangeListener(colorBoxes);
		colorBackground.setOnCheckedChangeListener(colorBoxes);
		defaultPaper.setOnCheckedChangeListener(colorBoxes);

		channelGroup.setOnCheckedChangeListener(
				new RadioGroup.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				if (suppress) {
					return;
				}
				editingForeground = checkedId == R.id.color_channel_foreground;
				showPickerForActiveChannel();
				updatePreview();
			}
		});

		host.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
			@Override
			public void onTabChanged(String tabId) {
				if (suppress) {
					return;
				}
				if ("RGB".equals(tabId) && activeChannelPaintable()
						&& activeChannelIsXterm()) {
					int argb = Colorizer.get256ColorValue(
							Integer.valueOf(activeXterm()));
					setHexText(TriggerColorPaint.formatHex(argb & 0x00FFFFFF));
				}
				updatePreview();
			}
		});

		palette.setOnIndexSelectedListener(
				new Xterm256PaletteView.OnIndexSelectedListener() {
			@Override
			public void onIndexSelected(int index) {
				if (suppress || !activeChannelPaintable()) {
					return;
				}
				applyXterm(index);
				int argb = Colorizer.get256ColorValue(Integer.valueOf(index));
				setHexText(TriggerColorPaint.formatHex(argb & 0x00FFFFFF));
				updatePreview();
			}
		});

		hexField.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				if (suppress || !activeChannelPaintable()) {
					return;
				}
				String raw = s.toString().trim();
				int hexDigits = raw.startsWith("#") ? raw.length() - 1 : raw.length();
				if (hexDigits != 6 && hexDigits != 8) {
					return;
				}
				Integer parsed = TriggerColorPaint.parseHexRgb(raw);
				if (parsed == null) {
					return;
				}
				applyRgb(parsed.intValue());
				updatePreview();
			}
		});
		hexField.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if (hasFocus || suppress || !activeChannelPaintable()) {
					return;
				}
				Integer parsed = TriggerColorPaint.parseHexRgb(
						hexField.getText().toString());
				if (parsed == null) {
					setHexText(TriggerColorPaint.formatHex(activeRgbForField()));
				} else {
					applyRgb(parsed.intValue());
					updatePreview();
				}
			}
		});

		wheelButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				openWheel();
			}
		});

		CompoundButton.OnCheckedChangeListener styles =
				new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (suppress) {
					return;
				}
				applyStylesFromWidgets(paint);
				updatePreview();
			}
		};
		styleBold.setOnCheckedChangeListener(styles);
		styleFaint.setOnCheckedChangeListener(styles);
		styleItalic.setOnCheckedChangeListener(styles);
		styleUnderline.setOnCheckedChangeListener(styles);
		styleReverse.setOnCheckedChangeListener(styles);
		styleStrike.setOnCheckedChangeListener(styles);
	}

	private void loadWidgetsFromPaint() {
		suppress = true;
		colorText.setChecked(paint.paintsForeground());
		colorBackground.setChecked(paint.paintsBackground());
		defaultPaper.setChecked(paint.resetsBackground());
		styleBold.setChecked(paint.hasStyle(TriggerColorPaint.STYLE_BOLD));
		styleFaint.setChecked(paint.hasStyle(TriggerColorPaint.STYLE_FAINT));
		styleItalic.setChecked(paint.hasStyle(TriggerColorPaint.STYLE_ITALIC));
		styleUnderline.setChecked(paint.hasStyle(TriggerColorPaint.STYLE_UNDERLINE));
		styleReverse.setChecked(paint.hasStyle(TriggerColorPaint.STYLE_REVERSE));
		styleStrike.setChecked(paint.hasStyle(TriggerColorPaint.STYLE_STRIKE));
		applyChannelRadioState();
		showPickerForActiveChannel();
		suppress = false;
	}

	private void onColorFlagsChanged(CompoundButton source) {
		if (source == defaultPaper) {
			if (defaultPaper.isChecked()) {
				suppress = true;
				colorBackground.setChecked(false);
				suppress = false;
				paint.setBackgroundReset();
			} else if (!colorBackground.isChecked()) {
				paint.setBackgroundKeep();
			}
		} else if (source == colorBackground) {
			if (colorBackground.isChecked()) {
				suppress = true;
				defaultPaper.setChecked(false);
				suppress = false;
				restoreBackgroundPaint();
			} else if (defaultPaper.isChecked()) {
				paint.setBackgroundReset();
			} else {
				paint.setBackgroundKeep();
			}
		} else if (source == colorText) {
			if (colorText.isChecked()) {
				restoreForegroundPaint();
			} else {
				paint.setForegroundKeep();
			}
		}

		if (paint.paintsForeground() && !paint.paintsBackground()) {
			editingForeground = true;
		} else if (paint.paintsBackground() && !paint.paintsForeground()) {
			editingForeground = false;
		}

		suppress = true;
		applyChannelRadioState();
		showPickerForActiveChannel();
		suppress = false;
		updatePreview();
	}

	private void restoreForegroundPaint() {
		if (lastFgWasRgb) {
			paint.setForegroundRgb(paint.getFgRgb());
		} else {
			paint.setForegroundXterm(paint.getFgXterm());
		}
	}

	private void restoreBackgroundPaint() {
		if (lastBgWasRgb) {
			paint.setBackgroundRgb(paint.getBgRgb());
		} else {
			paint.setBackgroundXterm(paint.getBgXterm());
		}
	}

	private void applyChannelRadioState() {
		boolean fgOn = paint.paintsForeground();
		boolean bgOn = paint.paintsBackground();
		foregroundRadio.setEnabled(fgOn);
		backgroundRadio.setEnabled(bgOn);
		if (editingForeground && !fgOn && bgOn) {
			editingForeground = false;
		} else if (!editingForeground && !bgOn && fgOn) {
			editingForeground = true;
		}
		int checkId = editingForeground
				? R.id.color_channel_foreground
				: R.id.color_channel_background;
		if (channelGroup.getCheckedRadioButtonId() != checkId) {
			channelGroup.check(checkId);
		}
	}

	private void showPickerForActiveChannel() {
		boolean rgb = editingForeground ? lastFgWasRgb : lastBgWasRgb;
		if (editingForeground) {
			if (paint.getFgMode() == TriggerColorPaint.FgMode.RGB) {
				rgb = true;
			} else if (paint.getFgMode() == TriggerColorPaint.FgMode.XTERM) {
				rgb = false;
			}
		} else {
			if (paint.getBgMode() == TriggerColorPaint.BgMode.RGB) {
				rgb = true;
			} else if (paint.getBgMode() == TriggerColorPaint.BgMode.XTERM) {
				rgb = false;
			}
		}
		int tab = rgb ? TAB_RGB : TAB_256;
		boolean was = suppress;
		suppress = true;
		palette.setSelectedIndex(activeXterm());
		setHexText(TriggerColorPaint.formatHex(activeRgbForField()));
		if (host.getCurrentTab() != tab) {
			host.setCurrentTab(tab);
		}
		boolean canPick = activeChannelPaintable();
		hexField.setEnabled(canPick);
		wheelButton.setEnabled(canPick);
		palette.setEnabled(canPick);
		suppress = was;
	}

	private int activeRgbForField() {
		if (editingForeground) {
			if (paint.getFgMode() == TriggerColorPaint.FgMode.RGB) {
				return paint.getFgRgb();
			}
			return Colorizer.get256ColorValue(Integer.valueOf(paint.getFgXterm()))
					& 0x00FFFFFF;
		}
		if (paint.getBgMode() == TriggerColorPaint.BgMode.RGB) {
			return paint.getBgRgb();
		}
		return Colorizer.get256ColorValue(Integer.valueOf(paint.getBgXterm()))
				& 0x00FFFFFF;
	}

	private int activeXterm() {
		return editingForeground ? paint.getFgXterm() : paint.getBgXterm();
	}

	private boolean activeChannelIsXterm() {
		if (editingForeground) {
			return paint.getFgMode() == TriggerColorPaint.FgMode.XTERM;
		}
		return paint.getBgMode() == TriggerColorPaint.BgMode.XTERM;
	}

	private boolean activeChannelPaintable() {
		if (editingForeground) {
			return paint.paintsForeground();
		}
		return paint.paintsBackground();
	}

	private void applyXterm(int index) {
		if (editingForeground) {
			paint.setForegroundXterm(index);
			lastFgWasRgb = false;
		} else {
			paint.setBackgroundXterm(index);
			lastBgWasRgb = false;
		}
	}

	private void applyRgb(int packed) {
		if (editingForeground) {
			paint.setForegroundRgb(packed);
			lastFgWasRgb = true;
		} else {
			paint.setBackgroundRgb(packed);
			lastBgWasRgb = true;
		}
	}

	private void applyStylesFromWidgets(TriggerColorPaint spec) {
		spec.setStyle(TriggerColorPaint.STYLE_BOLD, styleBold.isChecked());
		spec.setStyle(TriggerColorPaint.STYLE_FAINT, styleFaint.isChecked());
		spec.setStyle(TriggerColorPaint.STYLE_ITALIC, styleItalic.isChecked());
		spec.setStyle(TriggerColorPaint.STYLE_UNDERLINE, styleUnderline.isChecked());
		spec.setStyle(TriggerColorPaint.STYLE_REVERSE, styleReverse.isChecked());
		spec.setStyle(TriggerColorPaint.STYLE_STRIKE, styleStrike.isChecked());
	}

	private void setHexText(String text) {
		boolean was = suppress;
		suppress = true;
		hexField.setText(text);
		if (hexField.getText() != null) {
			hexField.setSelection(hexField.getText().length());
		}
		suppress = was;
	}

	private void styleTabs() {
		TabWidget widget = host.getTabWidget();
		int title = getContext().getResources().getColor(R.color.chrome_title_text, null);
		int bar = getContext().getResources().getColor(R.color.chrome_title_bar, null);
		for (int i = 0; i < widget.getChildCount(); i++) {
			View tab = widget.getChildAt(i);
			tab.setBackgroundColor(bar);
			TextView label = (TextView) tab.findViewById(android.R.id.title);
			if (label != null) {
				label.setTextColor(title);
			}
		}
	}

	private void sizeScrollForScreen() {
		View scroll = findViewById(R.id.color_editor_scroll);
		if (scroll == null) {
			return;
		}
		float density = getContext().getResources().getDisplayMetrics().density;
		int screenH = getContext().getResources().getDisplayMetrics().heightPixels;
		int cap = (int) (screenH * 0.55f);
		int desired = (int) (400 * density);
		int min = (int) (220 * density);
		ViewGroup.LayoutParams lp = scroll.getLayoutParams();
		lp.height = Math.max(min, Math.min(desired, cap));
		scroll.setLayoutParams(lp);
	}

	private void openWheel() {
		if (!activeChannelPaintable()) {
			return;
		}
		int rgb = activeRgbForField();
		Integer typed = TriggerColorPaint.parseHexRgb(hexField.getText().toString());
		if (typed != null) {
			rgb = typed.intValue();
		}
		ColorPickerDialog picker = new ColorPickerDialog(getContext(),
				new ColorPickerDialog.OnColorChangedListener() {
					@Override
					public void colorChanged(int color) {
						applyRgb(color & 0x00FFFFFF);
						setHexText(TriggerColorPaint.formatHex(color & 0x00FFFFFF));
						updatePreview();
					}
				}, 0xFF000000 | rgb);
		picker.show();
	}

	private TriggerColorPaint paintFromWidgets() {
		TriggerColorPaint spec = paint.copy();
		if (colorText.isChecked()) {
			if (spec.getFgMode() == TriggerColorPaint.FgMode.KEEP) {
				if (lastFgWasRgb) {
					spec.setForegroundRgb(spec.getFgRgb());
				} else {
					spec.setForegroundXterm(spec.getFgXterm());
				}
			}
		} else {
			spec.setForegroundKeep();
		}

		if (defaultPaper.isChecked()) {
			spec.setBackgroundReset();
		} else if (!colorBackground.isChecked()) {
			spec.setBackgroundKeep();
		} else if (!spec.paintsBackground()) {
			if (lastBgWasRgb) {
				spec.setBackgroundRgb(spec.getBgRgb());
			} else {
				spec.setBackgroundXterm(spec.getBgXterm());
			}
		}

		applyStylesFromWidgets(spec);
		return spec;
	}

	private void updatePreview() {
		int fg;
		switch (paint.getFgMode()) {
		case RGB:
			fg = 0xFF000000 | paint.getFgRgb();
			break;
		case XTERM:
			fg = Colorizer.get256ColorValue(Integer.valueOf(paint.getFgXterm()));
			break;
		case KEEP:
		default:
			fg = PREVIEW_KEEP_FG;
			break;
		}
		int bg;
		switch (paint.getBgMode()) {
		case RGB:
			bg = 0xFF000000 | paint.getBgRgb();
			break;
		case XTERM:
			bg = Colorizer.get256ColorValue(Integer.valueOf(paint.getBgXterm()));
			break;
		case RESET:
		case KEEP:
		default:
			bg = getContext().getResources().getColor(R.color.chrome_preview, null);
			break;
		}
		if (paint.hasStyle(TriggerColorPaint.STYLE_REVERSE)) {
			int tmp = fg;
			fg = bg;
			bg = tmp;
		}
		if (paint.hasStyle(TriggerColorPaint.STYLE_FAINT)) {
			fg = (fg & 0x00FFFFFF) | 0x88000000;
		}
		preview.setTextColor(fg);
		preview.setBackgroundColor(bg);
		int face = Typeface.NORMAL;
		boolean bold = paint.hasStyle(TriggerColorPaint.STYLE_BOLD);
		boolean italic = paint.hasStyle(TriggerColorPaint.STYLE_ITALIC);
		if (bold && italic) {
			face = Typeface.BOLD_ITALIC;
		} else if (bold) {
			face = Typeface.BOLD;
		} else if (italic) {
			face = Typeface.ITALIC;
		}
		preview.setTypeface(Typeface.SANS_SERIF, face);
		int flags = preview.getPaintFlags();
		flags &= ~(Paint.UNDERLINE_TEXT_FLAG | Paint.STRIKE_THRU_TEXT_FLAG);
		if (paint.hasStyle(TriggerColorPaint.STYLE_UNDERLINE)) {
			flags |= Paint.UNDERLINE_TEXT_FLAG;
		}
		if (paint.hasStyle(TriggerColorPaint.STYLE_STRIKE)) {
			flags |= Paint.STRIKE_THRU_TEXT_FLAG;
		}
		preview.setPaintFlags(flags);
	}

	protected void doExit() {
		TriggerColorPaint spec = paintFromWidgets();
		ColorAction action = new ColorAction();
		action.setPaint(spec);
		if (original != null) {
			action.setFireType(original.getFireType());
			finish_with.editTriggerResponder(action, original);
		} else {
			finish_with.newTriggerResponder(action);
		}
		this.dismiss();
	}
}
