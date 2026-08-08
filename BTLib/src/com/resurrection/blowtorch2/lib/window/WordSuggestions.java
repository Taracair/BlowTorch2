package com.resurrection.blowtorch2.lib.window;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Words the game has just used, offered back while you type.
 *
 * <p><b>Why this is not the keyboard's job.</b> Gboard completes from a language
 * dictionary and from what you have typed before, and it has no idea what is on
 * screen. The words that are slow to type in a MUD are exactly the ones it will
 * never know: a mob called <i>grizzled</i>, a player called <i>Tonkatsu</i>, an
 * item called <i>gnarled oaken staff</i>. Worse, it actively corrects them into
 * English — type "grizz" and it offers "grid", "grim", "grip".
 *
 * <p>So this keeps a small, recent vocabulary of what the world actually said,
 * and completes from that. Type {@code k gri} and it offers {@code grizzled},
 * because the mob walked in three lines ago.
 *
 * <p>Newest first, deliberately: the thing that just arrived is nearly always
 * the thing you are about to name. A bounded store, because this lives in the UI
 * process for the length of a session.
 *
 * <p><b>What "recent" counts in.</b> Lines, not words. A word count is a poor
 * proxy: a quiet hour of a few lines keeps names from hours ago alive, while one
 * noisy room description evicts everything you were just looking at. The window
 * is the last {@link #DEFAULT_MAX_LINES} lines the world sent, so "recent" means
 * the same thing here as it does on screen. The word cap below is only a memory
 * backstop for a world that sends very wide lines.
 */
public final class WordSuggestions {

	/**
	 * Hard cap on distinct words, as a memory backstop only — the line window is
	 * the rule. Set high enough that a normal 300 lines (roughly 800–1500
	 * distinct words past {@link #MIN_WORD_LENGTH}) never reaches it, or the cap
	 * would silently become the real rule again.
	 */
	public static final int DEFAULT_MAX_WORDS = 4000;

	/**
	 * How many of the world's most recent lines count as "fresh". Player-settable;
	 * see {@link #setMaxLines}.
	 *
	 * <p>The one place this number is written down. The option default, the
	 * "unchanged, do not persist" comparison in the settings parser and the
	 * completer itself all read it from here: if they disagree the parser quietly
	 * stops saving the value the player chose, and nothing fails loudly.
	 */
	public static final int DEFAULT_MAX_LINES = 300;

	/** Above this a window is no longer a window. The one place this is written. */
	public static final int MAX_LINES = 5000;

	/**
	 * How many suggestions fit on the strip without it becoming a wall — and so
	 * how high {@code .complete N} goes. Written here rather than in the two
	 * processes that need it, which would drift.
	 *
	 * <p>The strip scrolls sideways, so this is not a "what fits" number: it is
	 * how many are worth reading before the list stops being a shortcut. Anything
	 * that prints the range must read it from here — {@code .complete 1..N} in
	 * the command's own help was written out by hand once and went stale.
	 */
	public static final int MAX_ON_STRIP = 8;

	/**
	 * How solid the chips are, as a percentage, when they are drawn over the game
	 * text. Only the backing fades — the words themselves stay fully opaque at
	 * every setting, because a suggestion you cannot read is worse than none.
	 */
	public static final int DEFAULT_OPACITY = 75;

	/** Below this the chips stop being findable at all. */
	public static final int MIN_OPACITY = 10;

	/**
	 * Where the chips are drawn: floating over the game text, resting on the
	 * input bar.
	 *
	 * <p>One place, three values, rather than two switches. Two booleans could
	 * say "no bar but float it", which is not a thing, and the player had to work
	 * out that one of them silently turned the other off. The order matters: this
	 * is the index into the option's item list, so items are added in this order
	 * and nothing is inserted in the middle.
	 */
	public static final int WHERE_FLOATING = 0;

	/** In a strip in the layout, below the game window. */
	public static final int WHERE_BAR = 1;

	/**
	 * Nowhere. Suggestions are still worked out — the ghost still draws and
	 * {@code .suggest N} still picks — there is simply no bar of chips.
	 */
	public static final int WHERE_NONE = 2;

	/**
	 * Floating, because the strip below the game window takes height while it
	 * shows: the game text jumps under the thumb on every letter.
	 */
	public static final int DEFAULT_WHERE = WHERE_FLOATING;

	/**
	 * Below this a loose match is noise: two or three letters are a subsequence
	 * of half the vocabulary, and the strip fills with words sharing nothing with
	 * what you meant.
	 */
	public static final int MIN_LOOSE_PREFIX_LENGTH = 4;

	/**
	 * Shorter than this is not worth completing — you have typed most of it by
	 * the time the suggestion appears, and short words are the common ones that
	 * would crowd out the useful proper nouns.
	 */
	public static final int MIN_WORD_LENGTH = 4;

	/** Below this many typed characters, everything matches and nothing helps. */
	public static final int MIN_PREFIX_LENGTH = 2;

	/**
	 * Most words a phrase may run to, the first one included.
	 *
	 * <p>Three, because that is what a MUD name costs — "grizzled cave troll",
	 * "gnarled oaken staff". Four starts swallowing the verb after the name, and
	 * there is nothing here that knows where a name ends: that would be grammar,
	 * and a part-of-speech tagger was weighed and rejected as tens of megabytes
	 * of English a MUD does not speak.
	 */
	public static final int PHRASE_MAX_WORDS = 3;

	/**
	 * A partial word held back across a chunk boundary can only be a word.
	 * Past this it is a wall of letters — a banner, an ASCII map row — and
	 * holding it would keep swallowing the chunk after it.
	 */
	private static final int MAX_PENDING_LENGTH = 64;

	/** A word as it was spelled, the line it was last seen on, and what followed. */
	private static final class Seen {
		private final String spelling;
		private final int line;
		/**
		 * The key of the word that last came immediately after this one, or null.
		 *
		 * <p>Mutable, unlike the rest: the successor is only known once the next
		 * word arrives, which is after this entry exists. Re-seeing a word makes a
		 * fresh entry, so a name that moves to a new neighbour forgets the old one
		 * rather than keeping both.
		 */
		private String next;

		Seen(final String spelling, final int line) {
			this.spelling = spelling;
			this.line = line;
		}
	}

	/**
	 * Insertion-ordered, so iterating backwards gives newest first. Keyed by the
	 * lower-cased word, valued by the spelling as it appeared — a player is
	 * "Tonkatsu" and the completion should say so.
	 *
	 * <p>Insertion order is also line order: a word re-seen is removed and put
	 * back, so its stamp only ever moves forward. That is what lets the window be
	 * trimmed from the front instead of scanned.
	 */
	private final LinkedHashMap<String, Seen> words =
			new LinkedHashMap<String, Seen>();

	/**
	 * How many command words of each kind are remembered.
	 *
	 * <p>Bounded like everything else here. These are words the player types, not
	 * words the world sends, so the store fills far more slowly than
	 * {@link #words} and this is generous.
	 */
	public static final int MAX_ROLE_WORDS = 500;

	/**
	 * Words the player has started a command with.
	 *
	 * <p>Free knowledge: the first word of a command a player sent <em>is</em> a
	 * verb this world understands, so nothing has to be taught or shipped.
	 * Insertion-ordered and re-inserted on every sighting, same as {@link #words},
	 * so the oldest falls out of the front when the cap is reached.
	 */
	private final LinkedHashSet<String> verbs = new LinkedHashSet<String>();

	/** Words the player has typed after the command word. */
	private final LinkedHashSet<String> objects = new LinkedHashSet<String>();

	/** How many verbs keep a list of what the player aims them at. */
	public static final int MAX_VERBS_PAIRED = 100;

	/** How many different targets are remembered per verb. */
	public static final int MAX_OBJECTS_PER_VERB = 40;

	/**
	 * For each verb, what the player has aimed it at, and how often.
	 *
	 * <p>{@code kill} goes with the thing you later {@code flee} from;
	 * {@code wear} with the thing you {@code remove}. A count of what has
	 * followed what is a few kilobytes and needs no grammar — it is a record of
	 * how this player plays this world, not a claim about English.
	 *
	 * <p>Insertion-ordered at both levels so the oldest falls out of the front
	 * when a cap is reached, the same rule the vocabulary uses.
	 */
	private final LinkedHashMap<String, LinkedHashMap<String, Integer>> verbObjects =
			new LinkedHashMap<String, LinkedHashMap<String, Integer>>();

	/** Whether what usually follows this verb is allowed to lead. */
	private boolean pairRanking = false;

	/** Whether where the caret sits is allowed to reorder the suggestions. */
	private boolean rankByPosition = false;

	/**
	 * Commands whose remainder is prose, not a thing in the room.
	 *
	 * <p>Without this the object store fills with ordinary English within a few
	 * minutes of a chatty world — {@code say i think we should go north} would
	 * teach "think", "should" and "north" as things you point commands at, and
	 * the ranking would then push them above the mob you are fighting.
	 */
	private static final java.util.Set<String> SPEECH_VERBS =
			new java.util.HashSet<String>(java.util.Arrays.asList(
					"say", "sayto", "tell", "whisper", "emote", "emo", "pmote",
					"chat", "gossip", "shout", "yell", "ooc", "reply", "page",
					"group", "gt", "gtell", "clan", "guild", "newbie", "answer",
					"note", "board", "describe", "title"));

	private final int maxWords;

	/** Lines the world has sent this session; the clock the window measures. */
	private int linesSeen = 0;

	private int maxLines = DEFAULT_MAX_LINES;

	/**
	 * The newest line that actually contained a word, which is what the window is
	 * measured back from. Not {@link #linesSeen}: a world that sends blank lines,
	 * or a chunk that ends in a newline, would otherwise push vocabulary out of
	 * the window without having said anything.
	 */
	private int lastWordLine = 0;

	/** Fall back to a letters-in-order match when the exact prefix finds nothing. */
	private boolean looseMatching = false;

	/** Offer the words that followed, not only the word itself. */
	private boolean phrases = false;

	/**
	 * Whether the plain word comes before the phrase built on it.
	 *
	 * <p>Only ever changes the order of a word against its own phrase, so with
	 * {@link #setPhrases} off it does nothing at all — and it never touches the
	 * loose-matching pass, which builds no phrases.
	 */
	private boolean shortestFirst = false;

	/**
	 * The tail of the last chunk when it stopped in the middle of a word.
	 *
	 * <p>Text arrives here as whole TCP chunks, so a word can be cut in half by
	 * the network: {@code "a griz"} then {@code "zled troll"}. Learned as they
	 * fall, that teaches "griz" and drops "zled" for being short — a word the
	 * world never said, offered back to the player. Held instead and glued to
	 * the front of the next chunk.
	 */
	private String pending = "";

	/**
	 * The word a phrase would continue from, or null when it may not continue.
	 *
	 * <p>Cleared at a line end and at every word that is not stored — a short
	 * one, a number. Without that, "a sword of power" would join "sword" to
	 * "power" across the dropped "of" and offer a thing that was never written.
	 */
	private String lastKey = null;

	public WordSuggestions() {
		this(DEFAULT_MAX_WORDS);
	}

	public WordSuggestions(final int maxWords) {
		this.maxWords = maxWords > 0 ? maxWords : DEFAULT_MAX_WORDS;
	}

	/**
	 * How many recent lines count as fresh.
	 *
	 * @param lines the window; zero or less turns the window off, leaving only
	 *        the word cap — for a player who wants everything the session said.
	 */
	public void setMaxLines(final int lines) {
		this.maxLines = lines;
		prune();
	}

	public int getMaxLines() {
		return maxLines;
	}

	/**
	 * Whether a mistyped or shortened word still finds its completion.
	 *
	 * <p>Off by default. It only ever runs when the exact prefix found nothing,
	 * so a player who types accurately never sees a different answer than before
	 * — but a player who does not gets {@code grzld} → {@code grizzled}.
	 *
	 * @param on true to allow the fallback.
	 */
	public void setLooseMatching(final boolean on) {
		this.looseMatching = on;
	}

	public boolean isLooseMatching() {
		return looseMatching;
	}

	/**
	 * Whether a match also offers the words that followed it.
	 *
	 * <p>Off by default: with it on the top suggestion for a prefix changes from
	 * a word to a phrase, and that is the sort of change a player has to ask for.
	 * On, {@code gri} offers "grizzled cave troll" above "grizzled" — the phrase
	 * first, because typing the rest of a mob's name on a phone is the thing this
	 * exists to save, and the plain word stays right underneath it.
	 *
	 * @param on true to offer phrases.
	 */
	public void setPhrases(final boolean on) {
		this.phrases = on;
	}

	public boolean isPhrases() {
		return phrases;
	}

	/**
	 * Whether a word comes before the phrase built on it.
	 *
	 * <p>Off by default, which is the order phrases shipped with: the whole name
	 * first, because typing "grizzled cave troll" on a phone is what that
	 * feature exists to save.
	 *
	 * <p>On, {@code expl} offers "explosive" and then "explosive crates". Four
	 * letters typed is not yet a request for the long form, and a player working
	 * from the ghost — which shows one suggestion — sees the short one, which is
	 * more often the one meant.
	 *
	 * <p>Does nothing with phrases off: there is no second form to order
	 * against. Does nothing to the loose-matching pass either, which offers
	 * whole words only.
	 *
	 * @param on true to put the word first.
	 */
	public void setShortestFirst(final boolean on) {
		this.shortestFirst = on;
	}

	public boolean isShortestFirst() {
		return shortestFirst;
	}

	/**
	 * Whether the caret's place in the line may reorder the suggestions.
	 *
	 * <p>Off by default, and it is an option rather than a rule because it is the
	 * kind of help that is wrong some of the time: at the start of a line it
	 * lifts words the player has used as commands, everywhere else it lifts words
	 * they have used as things. A world where that guess does not hold, or a
	 * player who has learned where their suggestions sit, is better off without
	 * it.
	 *
	 * <p>It only ever <em>reorders</em>. Every suggestion reachable with this off
	 * is reachable with it on, so turning it on cannot hide an answer — it can
	 * only move it.
	 *
	 * @param on true to rank by position.
	 */
	public void setRankByPosition(final boolean on) {
		this.rankByPosition = on;
	}

	public boolean isRankByPosition() {
		return rankByPosition;
	}

	/**
	 * Take note of a command the player sent.
	 *
	 * <p>Kept apart from {@link #learn}: this never adds a word to what gets
	 * suggested, only to what is known about where words belong. A player typing
	 * a name the world never used should not make that name completable — the
	 * whole point is completing what the <em>world</em> said.
	 *
	 * <p>Recorded whatever {@link #setRankByPosition} says, so switching the
	 * option on works on the next keystroke instead of after a session of
	 * relearning. It is one short line per command sent.
	 *
	 * @param line the command as the player typed it. Callers must not pass a
	 *        masked line: a password's first word would become a verb.
	 */
	public void learnCommand(final String line) {
		if (line == null) {
			return;
		}
		String[] parts = line.trim().split("\\s+");
		String verb = null;
		for (int i = 0; i < parts.length; i++) {
			String key = commandWord(parts[i]);
			if (key == null) {
				continue;
			}
			if (verb == null) {
				verb = key;
				remember(verbs, key);
				if (SPEECH_VERBS.contains(key)) {
					// The rest of this line is prose.
					return;
				}
			} else if (key.length() >= MIN_WORD_LENGTH) {
				// Object side only: a word shorter than that is never stored in
				// the vocabulary either, so it could not be lifted by anything.
				// The verb side has no such floor — say, get, put and eat are all
				// shorter than it, and dropping "say" would let a line of chat
				// through as if it named things.
				remember(objects, key);
				rememberPair(verb, key);
			}
		}
	}

	/** A command token reduced to the word inside it, or null if there is none. */
	private static String commandWord(final String raw) {
		StringBuilder b = new StringBuilder(raw.length());
		for (int i = 0; i < raw.length(); i++) {
			if (isWordChar(raw.charAt(i))) {
				b.append(raw.charAt(i));
			}
		}
		// A leading ' is the say alias on most worlds and a trailing - is a dash
		// left by punctuation; neither is part of the word.
		while (b.length() > 0 && !Character.isLetterOrDigit(b.charAt(0))) {
			b.deleteCharAt(0);
		}
		while (b.length() > 0 && !Character.isLetterOrDigit(b.charAt(b.length() - 1))) {
			b.deleteCharAt(b.length() - 1);
		}
		if (b.length() == 0) {
			return null;
		}
		return b.toString().toLowerCase(Locale.US);
	}

	/**
	 * Note that this verb was aimed at this word.
	 *
	 * @param verb the command word, already lower-cased.
	 * @param object what followed it.
	 */
	private void rememberPair(final String verb, final String object) {
		if (verb == null || object == null || verb.equals(object)) {
			return;
		}
		LinkedHashMap<String, Integer> seen = verbObjects.remove(verb);
		if (seen == null) {
			seen = new LinkedHashMap<String, Integer>();
		}
		Integer count = seen.remove(object);
		seen.put(object, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
		while (seen.size() > MAX_OBJECTS_PER_VERB) {
			java.util.Iterator<String> it = seen.keySet().iterator();
			it.next();
			it.remove();
		}
		// Removed and re-put at both levels, so a verb still in use never falls
		// out of the front while one abandoned months ago sits there.
		verbObjects.put(verb, seen);
		while (verbObjects.size() > MAX_VERBS_PAIRED) {
			java.util.Iterator<String> it = verbObjects.keySet().iterator();
			it.next();
			it.remove();
		}
	}

	/**
	 * Whether what usually follows a verb is offered first after that verb.
	 *
	 * <p>Off by default and separate from {@link #setRankByPosition}, which it
	 * needs to be on to do anything. Ranking by position says "after a command
	 * word, things come first"; this says which things — {@code kill } offers
	 * what you have killed before, ahead of what you have merely worn.
	 *
	 * <p>More opinionated than ranking by position, which is why it is its own
	 * switch: it is a record of how you have played, and it will be wrong the
	 * first time you do something new. Like ranking, it only ever reorders.
	 *
	 * @param on true to let the pairing lead.
	 */
	public void setPairRanking(final boolean on) {
		this.pairRanking = on;
	}

	public boolean isPairRanking() {
		return pairRanking;
	}

	/** Put a word at the newest end of a bounded set. */
	private static void remember(final LinkedHashSet<String> set, final String key) {
		// Remove first, so a word used again moves to the end rather than keeping
		// the position where it would fall out of the front soonest.
		set.remove(key);
		set.add(key);
		while (set.size() > MAX_ROLE_WORDS) {
			java.util.Iterator<String> it = set.iterator();
			it.next();
			it.remove();
		}
	}

	/** How many lines the world has sent since this completer started. */
	public int linesSeen() {
		return linesSeen;
	}

	/**
	 * Take the words out of text the game sent.
	 *
	 * @param text any incoming text; null and empty are ignored. May be several
	 *        lines — this arrives as whole TCP chunks, not a line at a time — so
	 *        the newlines inside it are what advances the window.
	 */
	public void learn(final String text) {
		if (text == null || text.length() == 0) {
			return;
		}
		String work = pending.length() > 0 ? pending + text : text;
		pending = "";
		int start = -1;
		for (int i = 0; i <= work.length(); i++) {
			char c = i < work.length() ? work.charAt(i) : '\n';
			boolean part = i < work.length() && isWordChar(c);
			if (part && start < 0) {
				start = i;
			} else if (!part && start >= 0) {
				if (i == work.length() && work.length() - start <= MAX_PENDING_LENGTH) {
					// The chunk stopped in the middle of a word. Whatever the rest
					// of it is, it is in the next packet.
					pending = work.substring(start);
				} else {
					lastKey = addWord(work.substring(start, i));
				}
				start = -1;
			}
			// After the word closes: a word ending at the newline still belongs
			// to the line it ended.
			if (i < work.length() && c == '\n') {
				linesSeen++;
				// A phrase never runs past the end of a line.
				lastKey = null;
			}
		}
		prune();
	}

	/** Drop what has fallen out of the window, then out of the word cap. */
	private void prune() {
		java.util.Iterator<Map.Entry<String, Seen>> it = words.entrySet().iterator();
		if (maxLines > 0) {
			while (it.hasNext()) {
				if (it.next().getValue().line > lastWordLine - maxLines) {
					break;
				}
				it.remove();
			}
		}
		while (words.size() > maxWords) {
			java.util.Iterator<String> keys = words.keySet().iterator();
			keys.next();
			keys.remove();
		}
	}

	private static boolean isWordChar(final char c) {
		return Character.isLetterOrDigit(c) || c == '\'' || c == '-';
	}

	/**
	 * Store one word.
	 *
	 * @return its key, or null when it was not worth storing — which is also the
	 *         signal that a phrase may not run through this position.
	 */
	private String addWord(final String raw) {
		if (raw.length() < MIN_WORD_LENGTH) {
			return null;
		}
		// All-digits is a number, not a name: "1234" completes nothing useful
		// and pushes real words out of a bounded store.
		boolean anyLetter = false;
		for (int i = 0; i < raw.length(); i++) {
			if (Character.isLetter(raw.charAt(i))) {
				anyLetter = true;
				break;
			}
		}
		if (!anyLetter) {
			return null;
		}
		String key = raw.toLowerCase(Locale.US);
		// Remove before put so a word seen again moves to the newest end rather
		// than keeping its original position. It also gets today's line stamp, so
		// a name the world keeps repeating never falls out of the window.
		words.remove(key);
		words.put(key, new Seen(raw, linesSeen));
		if (lastKey != null && !lastKey.equals(key)) {
			Seen before = words.get(lastKey);
			if (before != null) {
				before.next = key;
			}
		}
		lastWordLine = linesSeen;
		return key;
	}

	/**
	 * Completions for what is being typed, newest first.
	 *
	 * @param prefix the partial word; shorter than {@link #MIN_PREFIX_LENGTH}
	 *        gives nothing.
	 * @param max how many to return.
	 * @return never null, possibly empty.
	 */
	public List<String> suggest(final String prefix, final int max) {
		return suggest(prefix, max, false);
	}

	/**
	 * Completions for what is being typed, best first.
	 *
	 * @param prefix the partial word.
	 * @param max how many to return.
	 * @param atLineStart true when nothing precedes the partial word on the input
	 *        line, so the player is typing a command rather than its target.
	 *        Ignored unless {@link #setRankByPosition} is on.
	 * @return never null, possibly empty.
	 */
	public List<String> suggest(final String prefix, final int max,
			final boolean atLineStart) {
		return suggest(prefix, max, atLineStart, null);
	}

	/**
	 * Completions for what is being typed, best first.
	 *
	 * @param prefix the partial word.
	 * @param max how many to return.
	 * @param atLineStart true when nothing precedes the partial word on the line.
	 * @param leadingVerb the first word already on the line, lower-cased, or null
	 *        when there is none. Used only by {@link #setPairRanking}, to put
	 *        what usually follows <em>this</em> command ahead of what merely
	 *        follows commands in general.
	 * @return never null, possibly empty.
	 */
	public List<String> suggest(final String prefix, final int max,
			final boolean atLineStart, final String leadingVerb) {
		List<String> out = new ArrayList<String>();
		if (prefix == null || prefix.length() < MIN_PREFIX_LENGTH || max <= 0) {
			return out;
		}
		String needle = prefix.toLowerCase(Locale.US);
		// Forward once collecting only matches, rather than copying the whole
		// store to walk it backwards. This runs on every keystroke, and a line
		// window holds several times what the old 500-word cap did, so the cost
		// has to follow the number of matches and not the size of the vocabulary.
		List<String> matches = new ArrayList<String>();
		for (Map.Entry<String, Seen> e : words.entrySet()) {
			// Not the word you have already finished typing.
			if (e.getKey().length() > needle.length() && e.getKey().startsWith(needle)) {
				matches.add(e.getKey());
			}
		}
		rankByPosition(matches, atLineStart, leadingVerb);
		for (int i = matches.size() - 1; i >= 0 && out.size() < max; i--) {
			String key = matches.get(i);
			String phrase = phrases ? phraseFrom(key) : null;
			Seen s = words.get(key);
			String word = s == null ? null : s.spelling;
			// Which of the two forms of one word leads. The phrase first by
			// default: it is the part that is slow to type, and the plain word
			// is one tap further down. Shortest first is the other reading, and
			// it is the right one for a player who has typed four letters and
			// said nothing yet about wanting the whole name.
			String first = shortestFirst ? word : phrase;
			String second = shortestFirst ? phrase : word;
			if (first != null && out.size() < max) {
				out.add(first);
			}
			if (second != null && out.size() < max) {
				out.add(second);
			}
		}
		if (out.isEmpty() && looseMatching
				&& needle.length() >= MIN_LOOSE_PREFIX_LENGTH) {
			suggestLoosely(needle, max, out);
		}
		return out;
	}

	/**
	 * Move the words that fit this place in the line to the front of the answer.
	 *
	 * <p>{@code matches} is oldest-first and read backwards, so "to the front of
	 * the answer" means "to the end of this list". The two groups keep their own
	 * order inside themselves, which is what makes this a reordering and not a
	 * different result: with the option off, or with nothing yet known about the
	 * player's commands, the list comes out exactly as it went in.
	 *
	 * <p>Nothing is dropped. A word the player has never used as a command still
	 * follows the ones they have — further down the strip, not gone. That is the
	 * whole contract: a suggestion that was reachable yesterday is reachable
	 * today.
	 *
	 * @param matches candidate keys, oldest first; reordered in place.
	 * @param atLineStart true when the caret is on the first word of the line.
	 */
	private void rankByPosition(final List<String> matches, final boolean atLineStart,
			final String leadingVerb) {
		if (!rankByPosition || matches.size() < 2) {
			return;
		}
		java.util.Set<String> favoured = atLineStart ? verbs : objects;
		// What this particular command has been aimed at before. Only after the
		// command word: at the start of a line there is no verb to pair with yet.
		final java.util.Map<String, Integer> paired =
				(!atLineStart && pairRanking && leadingVerb != null)
					? verbObjects.get(leadingVerb) : null;
		if (favoured.isEmpty()) {
			// Nothing is a known target yet, and the pairing cannot lift what the
			// object store does not hold, so there is nothing to reorder either way.
			return;
		}
		List<String> rest = new ArrayList<String>(matches.size());
		List<String> lifted = new ArrayList<String>(matches.size());
		List<String> withThisVerb = new ArrayList<String>(matches.size());
		for (int i = 0; i < matches.size(); i++) {
			String key = matches.get(i);
			// The pairing lifts only what the object store still counts as a
			// target. The two are written together, but they are not the same
			// size — the pair maps hold up to MAX_VERBS_PAIRED * MAX_OBJECTS_PER_VERB
			// between them, several times MAX_ROLE_WORDS — so without this gate a
			// word long since evicted from `objects` would be lifted past
			// everything by a store that outlived the one meant to bound it.
			if (paired != null && paired.containsKey(key) && favoured.contains(key)) {
				withThisVerb.add(key);
			} else if (favoured.contains(key)) {
				lifted.add(key);
			} else {
				rest.add(key);
			}
		}
		if (lifted.isEmpty() && withThisVerb.isEmpty()) {
			return;
		}
		if (withThisVerb.size() > 1) {
			// Read back to front, so the most-used pairing has to end up last:
			// ascending here means most-used first in the answer. The opposite of
			// the comparator in describeLearned, which sorts for a person reading
			// a list top-down and therefore puts most-used first directly. Both
			// are right; neither is a copy of the other to be "corrected".
			// A stable sort, so two things done equally often keep the order they
			// already had, which is newest-said-first.
			final java.util.Map<String, Integer> counts = paired;
			java.util.Collections.sort(withThisVerb, new java.util.Comparator<String>() {
				@Override
				public int compare(String a, String b) {
					int ca = counts.get(a) == null ? 0 : counts.get(a).intValue();
					int cb = counts.get(b) == null ? 0 : counts.get(b).intValue();
					return ca - cb;
				}
			});
		}
		matches.clear();
		matches.addAll(rest);
		matches.addAll(lifted);
		matches.addAll(withThisVerb);
	}

	/**
	 * Second pass for a word that was typed wrong: every letter of what you typed
	 * appears in the word, in that order, gaps allowed. {@code grzld} finds
	 * {@code grizzled}.
	 *
	 * <p>Only reached when the exact prefix found nothing, so it can never
	 * displace a correct answer — the accurate typist's strip is unchanged.
	 *
	 * <p>The first letter must still match. Without that anchor the match is too
	 * loose to be useful: dropping it makes {@code rzld} find every word with
	 * those letters anywhere, and the one you meant is not near the front.
	 */
	private void suggestLoosely(final String needle, final int max,
			final List<String> out) {
		List<String> matches = new ArrayList<String>();
		for (Map.Entry<String, Seen> e : words.entrySet()) {
			if (isSubsequence(needle, e.getKey())) {
				matches.add(e.getValue().spelling);
			}
		}
		for (int i = matches.size() - 1; i >= 0 && out.size() < max; i--) {
			out.add(matches.get(i));
		}
	}

	/**
	 * The word and what followed it, up to {@link #PHRASE_MAX_WORDS}.
	 *
	 * <p>Stops early at a word that has fallen out of the window, so a phrase can
	 * never name something the player can no longer see, and at a word already in
	 * this phrase — "sword sword" repeated would otherwise chase its own tail.
	 *
	 * @return the phrase, or null when nothing followed and it would only be the
	 *         word again.
	 */
	private String phraseFrom(final String key) {
		StringBuilder out = new StringBuilder();
		java.util.HashSet<String> used = new java.util.HashSet<String>();
		String at = key;
		int count = 0;
		while (at != null && count < PHRASE_MAX_WORDS && used.add(at)) {
			Seen s = words.get(at);
			if (s == null) {
				break;
			}
			if (count > 0) {
				out.append(' ');
			}
			out.append(s.spelling);
			at = s.next;
			count++;
		}
		return count >= 2 ? out.toString() : null;
	}

	/** Do the letters of {@code needle} appear in {@code word}, in order? */
	private static boolean isSubsequence(final String needle, final String word) {
		if (word.length() <= needle.length()) {
			return false;
		}
		if (word.charAt(0) != needle.charAt(0)) {
			return false;
		}
		int at = 0;
		for (int i = 0; i < word.length() && at < needle.length(); i++) {
			if (word.charAt(i) == needle.charAt(at)) {
				at++;
			}
		}
		return at == needle.length();
	}

	/**
	 * Forget what the player's own commands taught.
	 *
	 * <p>Separate from {@link #clear} because it has a separate lifetime: the
	 * vocabulary is this session on this world, and this is every session on it.
	 */
	public void clearCommandKnowledge() {
		verbs.clear();
		objects.clear();
		verbObjects.clear();
	}

	/**
	 * What the player's commands have taught, as an XML body.
	 *
	 * <p>Stored in the app's settings folder, which the world backup already
	 * zips by extension — so this rides along with an exported world instead of
	 * needing an export of its own.
	 *
	 * @return the body; empty when there is nothing worth writing.
	 */
	public String exportCommandKnowledge() {
		if (verbs.isEmpty() && objects.isEmpty() && verbObjects.isEmpty()) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		out.append("<commandknowledge>\n");
		for (String v : verbs) {
			out.append("  <verb w=\"").append(escape(v)).append("\"/>\n");
		}
		for (String o : objects) {
			out.append("  <target w=\"").append(escape(o)).append("\"/>\n");
		}
		for (Map.Entry<String, LinkedHashMap<String, Integer>> e : verbObjects.entrySet()) {
			for (Map.Entry<String, Integer> t : e.getValue().entrySet()) {
				out.append("  <pair v=\"").append(escape(e.getKey()))
					.append("\" t=\"").append(escape(t.getKey()))
					.append("\" n=\"").append(t.getValue()).append("\"/>\n");
			}
		}
		out.append("</commandknowledge>\n");
		return out.toString();
	}

	/**
	 * Read back what {@link #exportCommandKnowledge} wrote.
	 *
	 * <p>Deliberately forgiving. This file can be hand-edited, can arrive inside
	 * somebody else's exported world, and can be half-written if the phone died
	 * mid-save. A row it cannot read is skipped rather than thrown: a damaged
	 * file costs the pairings, not the ability to play.
	 *
	 * @param body what was stored; replaces whatever is held.
	 */
	public void importCommandKnowledge(final String body) {
		clearCommandKnowledge();
		if (body == null || body.length() == 0) {
			return;
		}
		java.util.regex.Matcher m = KNOWLEDGE_ROW.matcher(body);
		while (m.find()) {
			String kind = m.group(1);
			if ("verb".equals(kind)) {
				remember(verbs, unescape(m.group(2)));
			} else if ("target".equals(kind)) {
				remember(objects, unescape(m.group(2)));
			} else {
				String verb = unescape(m.group(3));
				String target = unescape(m.group(4));
				int n;
				try {
					n = Integer.parseInt(m.group(5));
				} catch (NumberFormatException e) {
					n = 1;
				}
				// Replayed rather than assigned, so one path builds the counts and
				// the caps apply to a restored file exactly as they do to play.
				if (n > MAX_OBJECTS_PER_VERB) {
					n = MAX_OBJECTS_PER_VERB;
				}
				for (int i = 0; i < n; i++) {
					rememberPair(verb, target);
				}
			}
		}
	}

	/** One row of the stored knowledge: a verb, a target, or a pairing. */
	private static final java.util.regex.Pattern KNOWLEDGE_ROW =
			java.util.regex.Pattern.compile(
				"<(verb|target) w=\"([^\"]*)\"/>"
				+ "|<pair v=\"([^\"]*)\" t=\"([^\"]*)\" n=\"(\\d+)\"/>");

	private static String escape(final String in) {
		return in == null ? "" : in.replace("&", "&amp;").replace("\"", "&quot;")
				.replace("<", "&lt;").replace(">", "&gt;");
	}

	private static String unescape(final String in) {
		return in == null ? "" : in.replace("&quot;", "\"").replace("&lt;", "<")
				.replace("&gt;", ">").replace("&amp;", "&");
	}

	/** A one-line summary of what has been learned from the player's commands. */
	public String describeCommandKnowledge() {
		return verbs.size() + " verbs, " + objects.size() + " targets, "
				+ verbObjects.size() + " verbs with pairings";
	}

	/**
	 * What the player's commands have taught, in words, newest verb first.
	 *
	 * <p>Read-only: a report about the store must not disturb it, the same rule
	 * {@code CommandKeeper.peekNewest} had to learn. Capped in both directions,
	 * because this is printed into the game window and a hundred verbs would
	 * scroll away the thing that was being read.
	 *
	 * @param maxVerbs how many verbs to describe.
	 * @param maxObjects how many targets to name per verb.
	 * @return one line per verb, or a sentence saying there is nothing yet.
	 */
	public String describeLearned(final int maxVerbs, final int maxObjects) {
		if (verbObjects.isEmpty()) {
			return verbs.isEmpty()
					? "Nothing learned from your commands yet."
					: verbs.size() + " command words so far, but nothing has been"
						+ " aimed at anything yet.";
		}
		List<String> verbList = new ArrayList<String>(verbObjects.keySet());
		StringBuilder out = new StringBuilder();
		int shown = 0;
		for (int i = verbList.size() - 1; i >= 0 && shown < maxVerbs; i--, shown++) {
			String verb = verbList.get(i);
			LinkedHashMap<String, Integer> seen = verbObjects.get(verb);
			if (seen == null || seen.isEmpty()) {
				continue;
			}
			List<String> targets = new ArrayList<String>(seen.keySet());
			// Most-used first, read top-down by a person. rankByPosition sorts the
			// same map the other way round because its list is read back to front
			// — see the note there before changing either.
			final LinkedHashMap<String, Integer> counts = seen;
			java.util.Collections.sort(targets, new java.util.Comparator<String>() {
				@Override
				public int compare(String a, String b) {
					int ca = counts.get(a) == null ? 0 : counts.get(a).intValue();
					int cb = counts.get(b) == null ? 0 : counts.get(b).intValue();
					return cb - ca;
				}
			});
			out.append(verb).append(": ");
			for (int j = 0; j < targets.size() && j < maxObjects; j++) {
				if (j > 0) {
					out.append(", ");
				}
				out.append(targets.get(j)).append(" (")
					.append(counts.get(targets.get(j))).append(")");
			}
			if (targets.size() > maxObjects) {
				out.append(", and ").append(targets.size() - maxObjects).append(" more");
			}
			out.append("\n");
		}
		if (verbList.size() > shown) {
			out.append("and ").append(verbList.size() - shown)
				.append(" more command words.\n");
		}
		return out.toString();
	}

	/**
	 * Everything learned so far is dropped — a new world, a new vocabulary.
	 *
	 * <p>The session vocabulary only. What the player's own commands taught is
	 * kept per world in a file now, so wiping it here would throw away on every
	 * connect the very thing that is supposed to build up across them. Use
	 * {@link #clearCommandKnowledge} for that, deliberately.
	 */
	public void clear() {
		words.clear();
		linesSeen = 0;
		lastWordLine = 0;
		pending = "";
		lastKey = null;
	}

	public int size() {
		return words.size();
	}

	/**
	 * The partial word immediately before the caret, which is what a completion
	 * would replace.
	 *
	 * @param text the input bar's contents.
	 * @param caret where the cursor is.
	 * @return the partial word, empty when the caret is not at the end of one.
	 */
	public static String wordBefore(final String text, final int caret) {
		if (text == null) {
			return "";
		}
		int end = caret;
		if (end < 0) {
			end = 0;
		}
		if (end > text.length()) {
			end = text.length();
		}
		int start = end;
		while (start > 0 && isWordChar(text.charAt(start - 1))) {
			start--;
		}
		return text.substring(start, end);
	}

	/** The new contents and caret after accepting a completion. */
	public static final class Completion {
		private final String text;
		private final int caret;

		Completion(final String text, final int caret) {
			this.text = text;
			this.caret = caret;
		}

		public String text() {
			return text;
		}

		public int caret() {
			return caret;
		}
	}

	/**
	 * Replace the partial word before the caret with the whole one, and leave a
	 * space so the next word can be typed straight away.
	 *
	 * @param text the input bar's contents.
	 * @param caret where the cursor is.
	 * @param word the completion that was chosen.
	 * @return the new text and caret; never null.
	 */
	public static Completion complete(final String text, final int caret,
			final String word) {
		String existing = text == null ? "" : text;
		if (word == null || word.length() == 0) {
			return new Completion(existing, caret);
		}
		int end = caret;
		if (end < 0) {
			end = 0;
		}
		if (end > existing.length()) {
			end = existing.length();
		}
		int start = end;
		while (start > 0 && isWordChar(existing.charAt(start - 1))) {
			start--;
		}
		StringBuilder out = new StringBuilder();
		out.append(existing, 0, start);
		out.append(word);
		int newCaret = out.length();
		String after = existing.substring(end);
		if (after.length() == 0 || !Character.isWhitespace(after.charAt(0))) {
			out.append(' ');
			newCaret = out.length();
		}
		out.append(after);
		return new Completion(out.toString(), newCaret);
	}
}
