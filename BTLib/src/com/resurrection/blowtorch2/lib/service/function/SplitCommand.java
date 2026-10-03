package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.window.SplitLayout;

/**
 * Two views of the same main buffer: {@code .split 40}, {@code .split top},
 * {@code .split off}. Session layout only — not a second buffer.
 */
public class SplitCommand extends SpecialCommand {

	public SplitCommand() {
		this.commandName = "split";
	}

	@Override
	public Object execute(Object o, Connection c) {
		SplitLayout.Result r = SplitLayout.parse(o == null ? "" : ((String) o));
		if (r.error != null) {
			c.sendDataToWindow(getErrorMessage("Split command usage:", r.error));
			return null;
		}
		boolean on = c.isSplitEnabled();
		int orientation = c.getSplitOrientation();
		int percent = c.getSplitPercent();

		if (SplitLayout.ACTION_STATUS.equals(r.action)) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ SplitLayout.describe(on, orientation, percent) + "\n"
					+ SplitLayout.usage());
			return null;
		}
		if (SplitLayout.ACTION_OFF.equals(r.action)) {
			if (!on) {
				c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
						+ "Split already off.\n");
				return null;
			}
			c.applySplit(false, orientation, percent);
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Split off — one pane.\n");
			return null;
		}
		if (SplitLayout.ACTION_APPLY.equals(r.action)) {
			int nextOrient = r.orientationSet ? r.orientation : orientation;
			if (nextOrient != SplitLayout.ORIENTATION_HORIZONTAL
					&& nextOrient != SplitLayout.ORIENTATION_VERTICAL) {
				nextOrient = SplitLayout.ORIENTATION_HORIZONTAL;
			}
			int nextPercent = r.percentSet ? r.percent : (on ? percent : SplitLayout.DEFAULT_PERCENT);
			nextPercent = SplitLayout.clampPercent(nextPercent);
			if (on && nextOrient == orientation && nextPercent == percent) {
				// Rebuilds drop the panes; the service still thinks this is current.
				// Push again so `.split 40` repairs the UI instead of stopping here.
				c.applySplit(true, nextOrient, nextPercent);
				c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
						+ SplitLayout.describe(true, orientation, percent)
						+ " Already set.\n");
				return null;
			}
			c.applySplit(true, nextOrient, nextPercent);
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ SplitLayout.describe(true, nextOrient, nextPercent) + "\n");
			return null;
		}
		c.sendDataToWindow(getErrorMessage("Split command usage:", SplitLayout.usage()));
		return null;
	}
}
