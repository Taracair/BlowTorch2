package com.resurrection.blowtorch2.lib.button;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Lines {@code .icon} prints. The folder sentence is filled in by the caller. */
public final class IconReports {

	private IconReports() {
	}

	public static String status(final String folderLine) {
		if (folderLine == null || folderLine.length() == 0) {
			return "No icon folder.\n.icon folder chooses one.\n";
		}
		return folderLine + "\n.icon set LOOK sword.png\n";
	}

	public static String sets(final Map<String, List<String>> catalog, final String folderLine) {
		StringBuilder sb = new StringBuilder();
		sb.append("Built-in icons:\n");
		if (catalog == null || catalog.isEmpty()) {
			sb.append("  (none)\n");
		} else {
			List<String> names = new ArrayList<String>(catalog.keySet());
			Collections.sort(names);
			for (int i = 0; i < names.size(); i++) {
				String name = names.get(i);
				List<String> ids = catalog.get(name);
				int n = ids == null ? 0 : ids.size();
				sb.append("  ").append(name).append(" (").append(n).append(")\n");
			}
		}
		if (folderLine != null && folderLine.length() > 0) {
			sb.append(folderLine).append("\n");
		}
		return sb.toString();
	}

	public static String oneSet(final String set, final List<String> ids) {
		StringBuilder sb = new StringBuilder();
		sb.append(set).append(":\n");
		if (ids == null || ids.isEmpty()) {
			sb.append("  (none)\n");
			return sb.toString();
		}
		List<String> sorted = new ArrayList<String>(ids);
		Collections.sort(sorted);
		for (int i = 0; i < sorted.size(); i++) {
			sb.append("  ").append(sorted.get(i)).append("\n");
		}
		return sb.toString();
	}

	public static String files(final String folderName, final List<String> names,
			final List<String> folders) {
		String title = folderName == null || folderName.length() == 0 ? "Icon folder" : folderName;
		StringBuilder sb = new StringBuilder();
		sb.append(title).append(":\n");
		if (folders != null && !folders.isEmpty()) {
			List<String> dirs = new ArrayList<String>(folders);
			Collections.sort(dirs);
			for (int i = 0; i < dirs.size(); i++) {
				sb.append("  ").append(dirs.get(i)).append("/\n");
			}
			sb.append("  A file in a subfolder: .icon set <label> ").append(dirs.get(0))
					.append("/<file>.png\n");
		}
		if ((names == null || names.isEmpty()) && folders != null && !folders.isEmpty()) {
			return sb.toString();
		}
		if (names == null || names.isEmpty()) {
			sb.append("  (none)\n");
			return sb.toString();
		}
		List<String> sorted = new ArrayList<String>(names);
		Collections.sort(sorted);
		for (int i = 0; i < sorted.size(); i++) {
			sb.append("  ").append(sorted.get(i)).append("\n");
		}
		return sb.toString();
	}
}
