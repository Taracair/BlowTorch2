package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.gauge.WidgetCommandParser;
import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * Overlay gauges: {@code .widget}, also {@code .gauge}.
 *
 * <p>Parse is {@link WidgetCommandParser}; the service store applies the
 * result. List and help do not mutate.
 */
public class WidgetCommand extends SpecialCommand {

	public static final String ALIAS_NAME = "gauge";

	public WidgetCommand() {
		this.commandName = "widget";
	}

	public static String usage() {
		return WidgetCommandParser.usage();
	}

	@Override
	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim();
		WidgetCommandParser.Result r = WidgetCommandParser.parse(arg);
		if (r.error != null) {
			c.sendDataToWindow(getErrorMessage("Widget command usage:", r.error));
			return null;
		}
		if (WidgetCommandParser.ACTION_HELP.equals(r.action)) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ WidgetCommandParser.usage());
			return null;
		}
		if (WidgetCommandParser.ACTION_LIST.equals(r.action)) {
			c.sendDataToWindow(listText(c));
			return null;
		}
		String note = c.applyGaugeWidget(r);
		if (r.error != null) {
			c.sendDataToWindow(getErrorMessage("Widget command error", r.error));
			return null;
		}
		if (note != null && note.length() > 0) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor() + note + "\n");
		}
		return null;
	}

	static String listText(final Connection c) {
		StringBuilder sb = new StringBuilder();
		sb.append("\n").append(Colorizer.getBrightCyanColor())
				.append("Widgets")
				.append(Colorizer.getWhiteColor()).append(" (")
				.append(c.areGaugeWidgetsEnabled() ? "enabled" : "disabled")
				.append("):\n");
		sb.append(c.formatGaugeWidgetList());
		return sb.toString();
	}
}
