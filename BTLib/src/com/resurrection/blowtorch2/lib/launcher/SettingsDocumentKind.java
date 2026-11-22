package com.resurrection.blowtorch2.lib.launcher;

import java.io.IOException;
import java.io.Reader;

import com.resurrection.blowtorch2.lib.util.XmlRootProbe;

/**
 * A picked XML file is either a launcher server list or one world's settings.
 * The launcher list is {@code <root><launcher>}. A world file is
 * {@code <blowtorch>}, or a BlowTorch 1 {@code <root>} whose first child is
 * not {@code launcher}.
 */
public final class SettingsDocumentKind {

	public enum Kind {
		LAUNCHER_LIST,
		WORLD_SETTINGS,
		UNKNOWN
	}

	private SettingsDocumentKind() {
	}

	public static Kind classify(Reader reader) throws IOException {
		XmlRootProbe.Outline outline = XmlRootProbe.outline(reader);
		return of(outline.root(), outline.firstChild());
	}

	static Kind of(String root, String firstChild) {
		if ("blowtorch".equals(root)) {
			return Kind.WORLD_SETTINGS;
		}
		if ("root".equals(root) && "launcher".equals(firstChild)) {
			return Kind.LAUNCHER_LIST;
		}
		if ("root".equals(root) && firstChild != null) {
			return Kind.WORLD_SETTINGS;
		}
		return Kind.UNKNOWN;
	}
}
