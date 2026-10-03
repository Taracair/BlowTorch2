package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.WindowToken;
import com.resurrection.blowtorch2.lib.window.PrefixPickLoupe;

/**
 * {@code .copy loupe} — size and zoom for the two-finger copy widget.
 *
 * <pre>
 * .copy / .copy loupe — print size and zoom
 * .copy loupe size N / .copy size N
 * .copy loupe zoom N / .copy zoom N
 * .copy loupe default
 * </pre>
 *
 * Same tokens as {@link PickCommand} loupe; separate option keys.
 */
public class CopyCommand extends SpecialCommand {

	public CopyCommand() {
		this.commandName = "copy";
	}

	@Override
	public Object execute(Object o, Connection c) {
		String raw = o == null ? "" : o.toString().trim();
		if (raw.length() == 0 || PickCommand.isLoupeCommand(raw)) {
			return executeLoupe(raw.length() == 0 ? "loupe" : raw, c);
		}
		c.sendDataToWindow(getErrorMessage(
				"Copy loupe usage:",
				".copy / .copy loupe         — print size and zoom\n"
						+ ".copy loupe size N     — magnifier size 50–200 (118 default)\n"
						+ ".copy loupe zoom N     — magnifier zoom 150–350 (200 = 2×)\n"
						+ ".copy loupe default\n"
						+ "Also: Options → Window → Copy loupe size / zoom."));
		return null;
	}

	private Object executeLoupe(final String raw, final Connection c) {
		String[] tok = raw.toLowerCase(Locale.US).trim().split("\\s+");
		int size = c.getMainWindowIntegerOption("copy_loupe_size",
				WindowToken.DEFAULT_COPY_LOUPE_SIZE);
		int zoom = c.getMainWindowIntegerOption("copy_loupe_zoom",
				WindowToken.DEFAULT_COPY_LOUPE_ZOOM);
		size = PrefixPickLoupe.clampSize(size);
		zoom = PrefixPickLoupe.clampZoom(zoom);
		String verb = tok[0];
		int i = 0;
		if (verb.equals("loupe")) {
			i = 1;
		}
		if (i >= tok.length || tok[i].equals("status")) {
			return loupeStatus(c, size, zoom);
		}
		if (tok[i].equals("default") || tok[i].equals("reset")) {
			if (!c.updateMainWindowIntegerOption("copy_loupe_size",
					PrefixPickLoupe.DEFAULT_SIZE)
					|| !c.updateMainWindowIntegerOption("copy_loupe_zoom",
							PrefixPickLoupe.DEFAULT_ZOOM)) {
				c.sendDataToWindow(getErrorMessage("Copy loupe error",
						"There is no game window to change yet."));
				return null;
			}
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Copy loupe size " + PrefixPickLoupe.DEFAULT_SIZE
					+ ", zoom " + PrefixPickLoupe.DEFAULT_ZOOM + ".\n");
			return null;
		}
		boolean wantSize = tok[i].equals("size");
		boolean wantZoom = tok[i].equals("zoom");
		if (!wantSize && !wantZoom) {
			c.sendDataToWindow(getErrorMessage("Copy loupe usage:",
					".copy loupe | .copy loupe size 118 | .copy loupe zoom 200 | .copy loupe default\n"
							+ "Size 50–200. Zoom 150–350 (200 = 2×). Also Options → Window."));
			return null;
		}
		if (i + 1 >= tok.length) {
			return loupeStatus(c, size, zoom);
		}
		Integer n = PickCommand.parsePercent(tok[i + 1]);
		if (n == null) {
			c.sendDataToWindow(getErrorMessage("Copy loupe usage:",
					".copy loupe size 118 | .copy loupe zoom 200\n"
							+ "Size 50–200. Zoom 150–350 (200 = 2×)."));
			return null;
		}
		int use = wantSize ? PrefixPickLoupe.clampSize(n.intValue())
				: PrefixPickLoupe.clampZoom(n.intValue());
		String key = wantSize ? "copy_loupe_size" : "copy_loupe_zoom";
		if (!c.updateMainWindowIntegerOption(key, use)) {
			c.sendDataToWindow(getErrorMessage("Copy loupe error",
					"There is no game window to change yet."));
			return null;
		}
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ (wantSize ? "Copy loupe size " : "Copy loupe zoom ")
				+ use + ".\n");
		return null;
	}

	private static Object loupeStatus(final Connection c, final int size,
			final int zoom) {
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "Copy loupe size " + size + ", zoom " + zoom + ".\n"
				+ ".copy loupe size N | .copy loupe zoom N | .copy loupe default\n"
				+ "Also: Options → Window → Copy loupe size / zoom.\n");
		return null;
	}
}
