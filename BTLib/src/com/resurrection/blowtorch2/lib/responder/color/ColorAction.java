package com.resurrection.blowtorch2.lib.responder.color;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Matcher;

import org.keplerproject.luajava.LuaState;
import org.xmlpull.v1.XmlSerializer;

import android.content.Context;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.service.ColourBleedProbe;
import com.resurrection.blowtorch2.lib.window.TextTree;
import com.resurrection.blowtorch2.lib.window.TextTree.Color;
import com.resurrection.blowtorch2.lib.window.TextTree.Line;
import com.resurrection.blowtorch2.lib.window.TextTree.Text;
import com.resurrection.blowtorch2.lib.window.TextTree.Unit;

public class ColorAction extends TriggerResponder implements Parcelable {

	private int color = DEFAULT_COLOR; //xterm 256 color? otherwise this should be an int.
	private int backgroundColor = DEFAULT_BACKGROUND_COLOR;
	private TriggerColorPaint paint = TriggerColorPaint.legacyDefaults();
	public static int DEFAULT_COLOR = 256;
	public static int DEFAULT_BACKGROUND_COLOR = 232;
	
	public ColorAction(RESPONDER_TYPE pType) {
		super(pType);
		syncLegacyInts();
		this.setFireType(FIRE_WHEN.WINDOW_BOTH);
	}
	
	public ColorAction() {
		super(RESPONDER_TYPE.COLOR);
		syncLegacyInts();
		this.setFireType(FIRE_WHEN.WINDOW_BOTH);
	}

	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	public void writeToParcel(Parcel o, int flags) {
		o.writeInt(color);
		o.writeInt(backgroundColor);
		o.writeString(this.getFireType().getString());
		TriggerColorPaint p = paintOrDefault();
		o.writeInt(p.getFgMode().ordinal());
		o.writeInt(p.getFgXterm());
		o.writeInt(p.getFgRgb());
		o.writeInt(p.getBgMode().ordinal());
		o.writeInt(p.getBgXterm());
		o.writeInt(p.getBgRgb());
		o.writeInt(p.getStyles());
	}

	/**
	 * Peek whether any non-empty {@link Text} remains after the iterator's
	 * current position, then rewind so the caller can keep consuming.
	 */
	private static boolean hasFollowingText(ListIterator<Unit> it) {
		int steps = 0;
		boolean found = false;
		while (it.hasNext()) {
			Unit n = it.next();
			steps++;
			if (n instanceof Text) {
				String s = ((Text) n).getString();
				if (s != null && s.length() > 0) {
					found = true;
					break;
				}
			}
		}
		for (int i = 0; i < steps; i++) {
			it.previous();
		}
		return found;
	}

	/**
	 * The original line still holds its units until {@code setData} below.
	 * A finished line already has its NewLine; the rest of that line will not
	 * arrive in a later packet, so the colour must close here rather than wait
	 * for {@code closeAtLineEnds} — a gag can take the line first.
	 */
	private static boolean endsWithNewLine(Line line) {
		LinkedList<Unit> data = line.getData();
		return !data.isEmpty() && data.getLast() instanceof TextTree.NewLine;
	}

	private static void restoreOrLeaveOpen(Line line, LinkedList<Unit> newLine,
			Color bleed, ListIterator<Unit> it) {
		if (hasFollowingText(it) || endsWithNewLine(line)) {
			if (bleed != null) {
				newLine.add(bleed);
			}
		} else {
			line.setTriggerColorOpen(true);
			if (bleed != null && bleed.getOperations() != null) {
				line.setTriggerColorRestore(bleed.getOperations());
			}
		}
	}

	/**
	 * A colour code runs until the next one. Whatever CSI is last on a
	 * finished colour-triggered line (trigger paint, a restore, a leftover
	 * 38;5;n) is what {@code Window.onDraw} still holds for the next
	 * uncoloured line, which should have stayed default grey. Put a full
	 * reset immediately before the newline. Unfinished lines must not get
	 * this: their rest can still arrive in the next packet.
	 */
	private static void ensureStreamResetBeforeNewLine(LinkedList<Unit> newLine,
			TextTree tree) {
		if (newLine == null || newLine.isEmpty()) {
			return;
		}
		if (!(newLine.getLast() instanceof TextTree.NewLine)) {
			return;
		}
		if (newLine.size() >= 2) {
			Unit prev = newLine.get(newLine.size() - 2);
			if (prev instanceof Color && isBareReset((Color) prev)) {
				return;
			}
		}
		newLine.add(newLine.size() - 1, tree.makeColor(Collections.singletonList(
				Integer.valueOf(0))));
	}

	/**
	 * Only a lone SGR 0 is already a stream close. {@code 0;36} resets then
	 * paints cyan; {@code 38;5;0} uses 0 as an xterm index. Those must still
	 * get a real {@code [0m} after them.
	 */
	private static boolean isBareReset(Color c) {
		List<Integer> ops = c.getOperations();
		return ops != null && ops.size() == 1 && ops.get(0).intValue() == 0;
	}

	/**
	 * 0, 16 and 231 are the editor's "foreground only" sentinels: do not paint
	 * a background, and close whatever CSI background was already open.
	 */
	static boolean skipsBackgroundPaint(int backgroundColor) {
		return backgroundColor == 0 || backgroundColor == 16
				|| backgroundColor == 231;
	}

	private void addPaint(Line line, LinkedList<Unit> newLine, TextTree tree) {
		TriggerColorPaint spec = paintOrDefault();
		List<Integer> fg = spec.toForegroundSgrOps();
		if (!fg.isEmpty()) {
			if (spec.getFgMode() == TriggerColorPaint.FgMode.XTERM) {
				newLine.add(line.newColor(spec.getFgXterm()));
			} else {
				newLine.add(triggerColor(tree, fg));
			}
		}
		List<Integer> bg = spec.toBackgroundSgrOps();
		if (!bg.isEmpty()) {
			if (spec.getBgMode() == TriggerColorPaint.BgMode.XTERM) {
				newLine.add(line.newBackgroundColor(spec.getBgXterm()));
			} else {
				newLine.add(triggerColor(tree, bg));
			}
		}
		List<Integer> styleOn = spec.toStyleOnOps();
		if (!styleOn.isEmpty()) {
			newLine.add(triggerColor(tree, styleOn));
		}
	}

	private static Color triggerColor(TextTree tree, List<Integer> ops) {
		Color c = tree.makeColor(ops);
		c.setTriggerPaint(true);
		return c;
	}

	/**
	 * After the match, the rest of the line must not keep the paint. A
	 * foreground-only paint has to name a default background or a MUD
	 * {@code 48;5;n} sitting before the match stays open (the neon block);
	 * that branch also drops background-only units so they cannot become
	 * the restore foreground. An explicit background paint used to restore
	 * the pre-match SGR as-is ({@code 1}, {@code 22}), which does not close
	 * {@code 48;5;n} or {@code 38;5;n}, so the trigger's paint ran to the
	 * newline. That branch keeps bleed (including a MUD background) and
	 * adds 39 / 49 when bleed names no foreground / background.
	 *
	 * <p>KEEP background does not emit 49: the MUD background stays on the
	 * matched span. RESET (old sentinels 0/16/231) still emits 49.
	 */
	private Color colorAfterMatch(TextTree tree, Color bleed) {
		TriggerColorPaint spec = paintOrDefault();
		if (spec.paintsBackground()) {
			List<Integer> ops = (bleed != null && bleed.getOperations() != null)
					? new ArrayList<Integer>(bleed.getOperations())
					: new ArrayList<Integer>();
			if (spec.paintsForeground() && !listNamesForeground(ops)) {
				ops.add(Integer.valueOf(39));
			}
			ops.addAll(spec.toStyleOffOps());
			return tree.makeRestoreColor(ops);
		}
		List<Integer> fg = foregroundOps(bleed);
		if (spec.paintsForeground() && !listNamesForeground(fg)) {
			ArrayList<Integer> withFg = new ArrayList<Integer>(fg);
			withFg.add(Integer.valueOf(39));
			fg = withFg;
		}
		fg.addAll(spec.toStyleOffOps());
		if (spec.resetsBackground()) {
			return tree.makeRestoreColor(fg);
		}
		if (fg.isEmpty()) {
			return null;
		}
		return tree.makeColor(fg);
	}

	private static boolean namesForeground(Color c) {
		return c != null && listNamesForeground(c.getOperations());
	}

	private static boolean listNamesForeground(List<Integer> ops) {
		if (ops == null) {
			return false;
		}
		for (int i = 0; i < ops.size(); i++) {
			int op = ops.get(i).intValue();
			if (op == 38 || op == 0 || op == 39
					|| (op >= 30 && op <= 37) || (op >= 90 && op <= 97)) {
				return true;
			}
			if (op == 48) {
				if (i + 1 < ops.size() && ops.get(i + 1).intValue() == 5) {
					i += 2;
				} else if (i + 1 < ops.size() && ops.get(i + 1).intValue() == 2) {
					i += 4;
				}
			}
		}
		return false;
	}

	/** Copy SGR ops that are not a background, for a foreground-only restore. */
	static List<Integer> foregroundOps(Color bleed) {
		ArrayList<Integer> out = new ArrayList<Integer>();
		if (bleed == null || bleed.getOperations() == null) {
			return out;
		}
		List<Integer> ops = bleed.getOperations();
		for (int i = 0; i < ops.size(); i++) {
			int op = ops.get(i).intValue();
			if (op == 48) {
				// Land on the last value of 48;5;n / 48;2;r;g;b so the for-loop
				// increment skips it. A continue after i += 2 is the same in
				// Java; without the increment, xterm index 1/2/38 would be
				// copied as bold / a 38-intro.
				if (i + 1 < ops.size() && ops.get(i + 1).intValue() == 5) {
					i += 2;
				} else if (i + 1 < ops.size() && ops.get(i + 1).intValue() == 2) {
					i += 4;
				}
				continue;
			}
			if ((op >= 40 && op <= 47) || op == 49
					|| (op >= 100 && op <= 107)) {
				continue;
			}
			out.add(Integer.valueOf(op));
			if (op == 38) {
				if (i + 1 < ops.size() && ops.get(i + 1).intValue() == 5) {
					out.add(ops.get(i + 1));
					if (i + 2 < ops.size()) {
						out.add(ops.get(i + 2));
					}
					i += 2;
				} else if (i + 1 < ops.size() && ops.get(i + 1).intValue() == 2) {
					out.add(ops.get(i + 1));
					for (int k = 0; k < 3 && i + 2 + k < ops.size(); k++) {
						out.add(ops.get(i + 2 + k));
					}
					i += 4;
				}
			}
		}
		return out;
	}

	@Override
	public boolean doResponse(Context c, TextTree tree,int lineNumber,ListIterator<TextTree.Line> iterator,Line line, int pstart, int pend,String matched,
			Object source, String displayname,String host,int port, int triggernumber,
			boolean windowIsOpen, Handler dispatcher,
			HashMap<String, String> captureMap, LuaState L, String name,String encoding) {
		//well. this is sort of duplication of effort from the replacer action. but whatever.
		//int start = matched.start();
		//int end = matched.end()-1;
		if(windowIsOpen) {
			if(this.getFireType() == FIRE_WHEN.WINDOW_CLOSED || this.getFireType() == FIRE_WHEN.WINDOW_NEVER) return false;
		} else {
			if(this.getFireType() == FIRE_WHEN.WINDOW_OPEN || this.getFireType() == FIRE_WHEN.WINDOW_NEVER) return false;
		}
		
		int end = pend + 1 + tree.getModCount();
		int start = pstart + tree.getModCount();
		Unit u = null;
		line.resetIterator();
		ListIterator<Unit> it = line.getIterator();
		
		LinkedList<Unit> newLine = new LinkedList<Unit>();
		
		int working = 0;
		
		// Colour in effect at the match: start from what this line opened in,
		// then walk units. tree.getBleedColor() is the previous line on the
		// first line of a chunk. Earlier trigger paint on this line counts —
		// skipping it restored the raw MUD colour under a channel trigger
		// ("says" magenta, then the original green through to the newline).
		Color bleed = line.getServerColorAtStart() != null
				? tree.makeRestoreColor(line.getServerColorAtStart())
				: tree.getBleedColor();

		int splitAt = 0;
		boolean preEmptiveChop = false;
		int preEmptiveChopAt = 0;
		while(it.hasNext()) {
			u = it.next();
			boolean done = false;
			if(u instanceof TextTree.Text) {
				Text t = (Text)u;
				int startofunit = working;
				int endofunit = working + t.getString().length()-1;
				
				working += t.getString().length();
				
				if(endofunit >= start) {
					//pre-emptive replace. replaced text is entirely contained in the text unit
					splitAt = start - startofunit;
					
					done = true;
					if(endofunit >= end) {
						preEmptiveChop = true;
						preEmptiveChopAt = endofunit-end;
					}
				} else {
					newLine.add(u);
				}
			} else {
				if (u instanceof TextTree.Color) {
					Color previous = (Color) u;
					if (paintOrDefault().paintsBackground() || namesForeground(previous)) {
						bleed = previous;
					}
				}
				newLine.add(u);
			}
			
			if(done) {
				break;
			}
		}
		
		if(splitAt > 0) {
			Text pre = line.newText(((Text)u).getString().substring(0,splitAt));
			newLine.add(pre);
		}
		
		//here is where we would insert replaced text if this were a replacer.
		//instead, this is where we insert a new color unit denoting which color we would like.
		addPaint(line, newLine, tree);
		newLine.add(line.newText(matched));
		Color restore = colorAfterMatch(tree, bleed);
		if(preEmptiveChop) {
			// Restore the pre-match colour where the match ends, when there is
			// text after it on the line, or when the line is already finished.
			//
			// A match that runs to the end of the text that has arrived is the
			// awkward case. Restoring here painted the rest of the line, which
			// can come in the next TCP packet, with the colour the line started
			// in (_chatnet "…it off…"): that half-sentence belongs to the match.
			// Leaving the colour open and doing nothing else painted every line
			// under it — a colour code runs until the next one. So leave it
			// open only while the line is unfinished, and mark it: the colour
			// is closed at the end of the line it belongs to, wherever that
			// turns out to be. A finished line restores now — a gag can take
			// it before closeAtLineEnds runs.
			if(preEmptiveChopAt > 0) {
				int length = ((Text)u).getString().length();
				Text post = line.newText(((Text)u).getString().substring(length-preEmptiveChopAt,length));
				if (restore != null) {
					newLine.add(restore);
				}
				newLine.add(post);
			} else if (restore != null) {
				restoreOrLeaveOpen(line, newLine, restore, it);
			}
		} else {
			//normal "find and chop" procedure.
			boolean done = false;
			int chopAt = 0;
			Unit chop = null;
			while(it.hasNext()) {
				chop = it.next();
				if(chop instanceof Text) {
					Text t = (Text)chop;
					int startofunit = working;
					int endofunit = startofunit + t.getString().length()-1;
					working += t.getString().length();
					if(end <= endofunit) {
						chopAt = endofunit - end;
						done = true;
					}
				}
				if(done) {
					break;
				}
			}
			
			if(chopAt > 0) {
				int length = ((Text)chop).getString().length();
				Text post = line.newText(((Text)chop).getString().substring(length-chopAt,length));
				if (restore != null) {
					newLine.add(restore);
				}
				newLine.add(post);
			} else if (restore != null) {
				restoreOrLeaveOpen(line, newLine, restore, it);
			}
		}
		
		//finish out units if there are any.
		while(it.hasNext()) {
			newLine.add(it.next());
		}
		ensureStreamResetBeforeNewLine(newLine, tree);
		
		//here is where we would do tree pruning/data updating.
		
		//set line's data
		line.setData(newLine);
		line.resetIterator();
		it = line.getIterator();
		ColourBleedProbe probe = ColourBleedProbe.bound();
		if (probe != null) {
			probe.recordColor(name, matched, color, backgroundColor, bleed, restore, line);
		}
		return false;
	}

	@Override
	public TriggerResponder copy() {
		ColorAction tmp = new ColorAction(RESPONDER_TYPE.COLOR);
		tmp.setPaint(paintOrDefault());
		tmp.setFireType(this.getFireType());
		return tmp;
	}
	
	public boolean equals(Object o) {
		if(o == this) return true;
		if(!(o instanceof ColorAction)) return false;
		ColorAction b= (ColorAction)o;
		ColorAction a = this;
		if(!a.paintOrDefault().equals(b.paintOrDefault())) return false;
		if(a.getFireType() != b.getFireType()) return false;
		
		return true;
	}
	
	public ColorAction(Parcel in) {
		super(RESPONDER_TYPE.COLOR);
		
		readFromParcel(in);
	}

	private void readFromParcel(Parcel in) {
		this.color = in.readInt();
		this.backgroundColor = in.readInt();
		
		String fireType = in.readString();
		if(fireType.equals(FIRE_WINDOW_OPEN)) {
			setFireType(FIRE_WHEN.WINDOW_OPEN);
		} else if (fireType.equals(FIRE_WINDOW_CLOSED)) {
			setFireType(FIRE_WHEN.WINDOW_CLOSED);
		} else if (fireType.equals(FIRE_ALWAYS)) {
			setFireType(FIRE_WHEN.WINDOW_BOTH);
		} else if (fireType.equals(FIRE_NEVER)) {
			setFireType(FIRE_WHEN.WINDOW_NEVER);
		} else {
			setFireType(FIRE_WHEN.WINDOW_BOTH);
		}
		int fgMode = in.readInt();
		int fgXterm = in.readInt();
		int fgRgb = in.readInt();
		int bgMode = in.readInt();
		int bgXterm = in.readInt();
		int bgRgb = in.readInt();
		int styles = in.readInt();
		paint = TriggerColorPaint.fromParcelFields(fgMode, fgXterm, fgRgb,
				bgMode, bgXterm, bgRgb, styles);
		syncLegacyInts();
	}
	
	public static Parcelable.Creator<ColorAction> CREATOR = new Parcelable.Creator<ColorAction>() {

		public ColorAction createFromParcel(Parcel source) {
			return new ColorAction(source);
		}

		public ColorAction[] newArray(int size) {
			return new ColorAction[size];
		}
		
	};

	@Override
	public void saveResponderToXML(XmlSerializer out)
			throws IllegalArgumentException, IllegalStateException, IOException {
		// TODO Auto-generated method stub
		ColorActionParser.saveColorActionToXML(out,this);
	}

	public void setColor(int color) {
		this.color = color;
		paintOrDefault().setForegroundXterm(color);
	}

	public int getColor() {
		return color;
	}
	
	public void setBackgroundColor(int color) {
		paintOrDefault().setBackgroundLegacyIndex(color);
		syncLegacyInts();
	}
	
	public int getBackgroundColor() {
		return backgroundColor;
	}

	public TriggerColorPaint getPaint() {
		return paintOrDefault();
	}

	public void setPaint(TriggerColorPaint spec) {
		this.paint = spec == null
				? TriggerColorPaint.legacyDefaults()
				: spec.copy();
		syncLegacyInts();
	}

	private TriggerColorPaint paintOrDefault() {
		if (paint == null) {
			paint = TriggerColorPaint.legacyDefaults();
		}
		return paint;
	}

	private void syncLegacyInts() {
		TriggerColorPaint spec = paintOrDefault();
		this.color = spec.legacyForegroundInt();
		this.backgroundColor = spec.legacyBackgroundInt();
	}
	
}
