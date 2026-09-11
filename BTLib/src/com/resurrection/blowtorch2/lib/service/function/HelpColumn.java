package com.resurrection.blowtorch2.lib.service.function;

/**
 * Pads {@code .help} command names and wraps descriptions so continuation
 * lines start under the description, not under the name column.
 */
public final class HelpColumn {

	public static final int NAME_WIDTH = 18;
	/** Used when NAWS/terminal columns are missing or too narrow to leave a description. */
	public static final int DEFAULT_WIDTH = 48;

	private HelpColumn() {
	}

	public static String pad(final String name) {
		StringBuilder b = new StringBuilder(name == null ? "" : name);
		while (b.length() < NAME_WIDTH) {
			b.append(' ');
		}
		return b.toString();
	}

	/** Leading {@code "  "} + padded name width + the separating space. */
	public static int continuationIndent(final String name) {
		return 2 + pad(name).length() + 1;
	}

	/**
	 * Wrap at {@code reportedColumns} when that is wider than the name column
	 * plus a short description; otherwise {@link #DEFAULT_WIDTH}.
	 */
	public static int wrapWidth(final int reportedColumns) {
		if (reportedColumns > NAME_WIDTH + 8) {
			return reportedColumns;
		}
		return DEFAULT_WIDTH;
	}

	/**
	 * One help row, including the leading two spaces. Internal newlines, no
	 * trailing newline. Empty description is the padded name only.
	 */
	public static String formatRow(final String name, final String description,
			final int width) {
		String padded = pad(name);
		if (description == null || description.length() == 0) {
			return "  " + padded;
		}
		int indent = 2 + padded.length() + 1;
		int budget = width - indent;
		if (budget < 1) {
			budget = 1;
		}
		String head = "  " + padded + " ";
		String cont = spaces(indent);
		StringBuilder out = new StringBuilder();
		int i = 0;
		int n = description.length();
		while (i < n && description.charAt(i) == ' ') {
			i++;
		}
		boolean first = true;
		while (i < n) {
			int take = takeCount(description, i, budget);
			if (!first) {
				out.append('\n');
			}
			out.append(first ? head : cont);
			out.append(description, i, i + take);
			first = false;
			i += take;
			while (i < n && description.charAt(i) == ' ') {
				i++;
			}
		}
		if (first) {
			return "  " + padded;
		}
		return out.toString();
	}

	private static int takeCount(final String text, final int start, final int budget) {
		int remaining = text.length() - start;
		if (remaining <= budget) {
			return remaining;
		}
		int lastSpace = text.lastIndexOf(' ', start + budget - 1);
		if (lastSpace >= start) {
			int take = lastSpace - start;
			if (take > 0) {
				return take;
			}
		}
		return budget;
	}

	private static String spaces(final int n) {
		StringBuilder b = new StringBuilder(n);
		for (int i = 0; i < n; i++) {
			b.append(' ');
		}
		return b.toString();
	}
}
