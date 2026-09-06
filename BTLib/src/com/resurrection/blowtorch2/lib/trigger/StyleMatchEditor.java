package com.resurrection.blowtorch2.lib.trigger;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.trigger.style.StyleColorToken;
import com.resurrection.blowtorch2.lib.trigger.style.StyleMatchSpec;
import com.resurrection.blowtorch2.lib.trigger.style.StyleMatchSpec.ColorMode;
import com.resurrection.blowtorch2.lib.trigger.style.StyleMatchSpec.Combine;
import com.resurrection.blowtorch2.lib.trigger.style.StyleMatchSpec.Extras;
import com.resurrection.blowtorch2.lib.trigger.style.StyleMatchSpec.Gate;

/**
 * MATCH STYLE block in the trigger editor. Ignore / Require / Forbid per layer.
 */
final class StyleMatchEditor {

	private static final String[] GATES = new String[] { "Ignore", "Require", "Forbid" };
	private static final int COLOR_TEXT = 0xFFE8E8E8;

	private final Context context;
	private Spinner combine;
	private Spinner extras;
	private Spinner colorMode;
	private LayerRow fg;
	private LayerRow bg;
	private LayerRow bright;
	private LayerRow weight;
	private LayerRow italic;
	private LayerRow underline;
	private LayerRow doubleUnderline;
	private LayerRow strike;
	private LayerRow reverse;
	private LayerRow faint;
	private LayerRow blink;
	private LayerRow href;
	private LayerRow text;
	private CheckBox textRegex;
	private View header;
	private View body;
	private TextView chevron;
	private boolean expanded;
	private boolean gestureHidden;

	StyleMatchEditor(final Context context) {
		this.context = context;
	}

	void bind(final View root, final StyleMatchSpec spec) {
		StyleMatchSpec src = spec == null ? new StyleMatchSpec() : spec;
		header = root.findViewById(R.id.trigger_style_header);
		body = root.findViewById(R.id.trigger_style_body);
		chevron = (TextView) root.findViewById(R.id.trigger_style_chevron);
		expanded = src.isActive();
		if (header != null) {
			header.setOnClickListener(new View.OnClickListener() {
				public void onClick(final View v) {
					expanded = !expanded;
					applyBodyVisibility();
				}
			});
		}
		applyBodyVisibility();
		combine = (Spinner) root.findViewById(R.id.trigger_style_combine);
		extras = (Spinner) root.findViewById(R.id.trigger_style_extras);
		colorMode = (Spinner) root.findViewById(R.id.trigger_style_color_mode);
		wireEnum(combine, new String[] { "ALL layers", "ANY layer" },
				src.getCombine() == Combine.ANY ? 1 : 0);
		wireEnum(extras, new String[] { "Extra attributes OK", "No extra attributes" },
				src.getExtras() == Extras.FORBID ? 1 : 0);
		wireEnum(colorMode, new String[] { "Exact recipe", "Looks the same" },
				src.getColorMode() == ColorMode.LOOKS ? 1 : 0);
		LinearLayout layers = (LinearLayout) root.findViewById(R.id.trigger_style_layers);
		if (layers == null) {
			return;
		}
		layers.removeAllViews();
		fg = addColor(layers, "Foreground", src.getFgGate(),
				StyleColorToken.format(src.getFgSpace(), src.getFgCode()));
		bg = addColor(layers, "Background", src.getBgGate(),
				StyleColorToken.format(src.getBgSpace(), src.getBgCode()));
		bright = addFlag(layers, "Bright (SGR 1)", src.getBright());
		weight = addFlag(layers, "Bold (weight)", src.getWeight());
		italic = addFlag(layers, "Italic", src.getItalic());
		underline = addFlag(layers, "Underline", src.getUnderline());
		doubleUnderline = addFlag(layers, "Double underline", src.getDoubleUnderline());
		strike = addFlag(layers, "Strike", src.getStrike());
		reverse = addFlag(layers, "Reverse", src.getReverse());
		faint = addFlag(layers, "Faint", src.getFaint());
		blink = addFlag(layers, "Blink", src.getBlink());
		href = addValue(layers, "Link (OSC 8)", src.getHref(), src.getHrefValue());
		text = addValue(layers, "Run text",
				src.getTextGate() != Gate.IGNORE ? src.getTextGate()
						: ((src.getText() != null && src.getText().length() > 0)
								? Gate.REQUIRE : Gate.IGNORE),
				src.getText());
		textRegex = new CheckBox(context);
		textRegex.setText("Run text is a regex");
		textRegex.setTextColor(COLOR_TEXT);
		textRegex.setChecked(src.isTextRegex());
		layers.addView(textRegex);
	}

	void setGestureHidden(final boolean hidden) {
		gestureHidden = hidden;
		if (header != null) {
			header.setVisibility(hidden ? View.GONE : View.VISIBLE);
		}
		applyBodyVisibility();
	}

	private void applyBodyVisibility() {
		if (body != null) {
			body.setVisibility((!gestureHidden && expanded) ? View.VISIBLE : View.GONE);
		}
		if (chevron != null) {
			chevron.setText(expanded ? "▾" : "▸");
		}
	}

	StyleMatchSpec collect() {
		StyleMatchSpec spec = new StyleMatchSpec();
		if (combine != null) {
			spec.setCombine(combine.getSelectedItemPosition() == 1
					? Combine.ANY : Combine.ALL);
		}
		if (extras != null) {
			spec.setExtras(extras.getSelectedItemPosition() == 1
					? Extras.FORBID : Extras.ALLOW);
		}
		if (colorMode != null) {
			spec.setColorMode(colorMode.getSelectedItemPosition() == 1
					? ColorMode.LOOKS : ColorMode.EXACT);
		}
		applyColor(spec, true, fg);
		applyColor(spec, false, bg);
		if (bright != null) {
			spec.setBright(bright.gate());
		}
		if (weight != null) {
			spec.setWeight(weight.gate());
		}
		if (italic != null) {
			spec.setItalic(italic.gate());
		}
		if (underline != null) {
			spec.setUnderline(underline.gate());
		}
		if (doubleUnderline != null) {
			spec.setDoubleUnderline(doubleUnderline.gate());
		}
		if (strike != null) {
			spec.setStrike(strike.gate());
		}
		if (reverse != null) {
			spec.setReverse(reverse.gate());
		}
		if (faint != null) {
			spec.setFaint(faint.gate());
		}
		if (blink != null) {
			spec.setBlink(blink.gate());
		}
		if (href != null) {
			spec.setHref(href.gate(), href.value());
		}
		if (text != null && text.gate() != Gate.IGNORE
				&& text.value() != null && text.value().trim().length() > 0) {
			spec.setText(text.value());
			spec.setTextGate(text.gate());
			spec.setTextRegex(textRegex != null && textRegex.isChecked());
		}
		return spec;
	}

	private void wireEnum(final Spinner spinner, final String[] labels, final int sel) {
		if (spinner == null) {
			return;
		}
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(context,
				R.layout.spinner_item_dark, labels);
		adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark);
		spinner.setAdapter(adapter);
		spinner.setSelection(sel, false);
	}

	private LayerRow addFlag(final LinearLayout parent, final String label,
			final Gate gate) {
		return addRow(parent, label, gate, null, false);
	}

	private LayerRow addColor(final LinearLayout parent, final String label,
			final Gate gate, final String token) {
		return addRow(parent, label, gate, token, true);
	}

	private LayerRow addValue(final LinearLayout parent, final String label,
			final Gate gate, final String value) {
		return addRow(parent, label, gate, value, true);
	}

	private LayerRow addRow(final LinearLayout parent, final String label,
			final Gate gate, final String value, final boolean hasValue) {
		LinearLayout row = new LinearLayout(context);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(0, 4, 0, 4);
		TextView name = new TextView(context);
		name.setText(label);
		name.setTextColor(COLOR_TEXT);
		name.setTextSize(13f);
		LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0,
				LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f);
		row.addView(name, nameLp);
		Spinner spin = new Spinner(context);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(context,
				R.layout.spinner_item_dark, GATES);
		adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark);
		spin.setAdapter(adapter);
		spin.setPopupBackgroundResource(android.R.color.black);
		int pos = 0;
		if (gate == Gate.REQUIRE) {
			pos = 1;
		} else if (gate == Gate.FORBID) {
			pos = 2;
		}
		spin.setSelection(pos, false);
		LinearLayout.LayoutParams spinLp = new LinearLayout.LayoutParams(0,
				LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f);
		row.addView(spin, spinLp);
		final EditText field;
		if (hasValue) {
			field = new EditText(context);
			field.setText(value == null ? "" : value);
			field.setSingleLine(true);
			field.setTextColor(COLOR_TEXT);
			field.setTextSize(13f);
			field.setTypeface(Typeface.MONOSPACE);
			field.setHint("ansi:32");
			LinearLayout.LayoutParams fieldLp = new LinearLayout.LayoutParams(0,
					LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f);
			row.addView(field, fieldLp);
			field.setVisibility(pos == 0 ? View.INVISIBLE : View.VISIBLE);
			spin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				public void onItemSelected(final AdapterView<?> parent, final View view,
						final int position, final long id) {
					field.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);
				}
				public void onNothingSelected(final AdapterView<?> parent) {
				}
			});
		} else {
			field = null;
		}
		parent.addView(row);
		return new LayerRow(spin, field);
	}

	private static void applyColor(final StyleMatchSpec spec, final boolean fg,
			final LayerRow row) {
		if (row == null) {
			return;
		}
		Gate g = row.gate();
		if (g == Gate.IGNORE) {
			if (fg) {
				spec.setFg(Gate.IGNORE, spec.getFgSpace(), spec.getFgCode());
			} else {
				spec.setBg(Gate.IGNORE, spec.getBgSpace(), spec.getBgCode());
			}
			return;
		}
		StyleColorToken tok = StyleColorToken.parse(row.value());
		if (tok == null) {
			return;
		}
		if (fg) {
			spec.setFg(g, tok.space, tok.code);
		} else {
			spec.setBg(g, tok.space, tok.code);
		}
	}

	private static final class LayerRow {
		final Spinner spinner;
		final EditText field;

		LayerRow(final Spinner spinner, final EditText field) {
			this.spinner = spinner;
			this.field = field;
		}

		Gate gate() {
			int p = spinner.getSelectedItemPosition();
			if (p == 1) {
				return Gate.REQUIRE;
			}
			if (p == 2) {
				return Gate.FORBID;
			}
			return Gate.IGNORE;
		}

		String value() {
			if (field == null || field.getText() == null) {
				return "";
			}
			return field.getText().toString();
		}
	}
}
