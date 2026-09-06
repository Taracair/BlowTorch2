package com.resurrection.blowtorch2.lib.trigger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.ack.AckResponder;
import com.resurrection.blowtorch2.lib.responder.color.ColorAction;
import com.resurrection.blowtorch2.lib.responder.gag.GagAction;
import com.resurrection.blowtorch2.lib.responder.notification.NotificationResponder;
import com.resurrection.blowtorch2.lib.responder.replace.ReplaceResponder;
import com.resurrection.blowtorch2.lib.responder.script.ScriptResponder;
import com.resurrection.blowtorch2.lib.responder.setvariable.SetVariableResponder;
import com.resurrection.blowtorch2.lib.responder.toast.ToastResponder;
import com.resurrection.blowtorch2.lib.trigger.condition.ConditionGroup;
import com.resurrection.blowtorch2.lib.trigger.style.StyleMatchSpec;

import android.os.Parcel;
import android.os.Parcelable;

public class TriggerData implements Parcelable {

	private String name;
	private String pattern;
	private boolean interpretAsRegex;
	private TriggerFireOnce fireOnce;
	
	private boolean fired = false;
	
	private boolean hidden = false;
	
	private boolean enabled = true;
	
	private boolean save = true;
	private int sequence = DEFAULT_SEQUENCE;
	private boolean keepEvaluating = DEFAULT_KEEPEVAL;
	private String group = DEFAULT_GROUP;
	private List<TriggerResponder> responders;
	private ConditionGroup conditions;
	private StyleMatchSpec styleMatch;
	
	private Pattern p = null;
	private Matcher m = null;
	//private Matcher m = null;
	
	public TriggerData() {
		name = "";
		pattern = "";
		interpretAsRegex = false;
		responders = new ArrayList<TriggerResponder>();
		conditions = new ConditionGroup();
		styleMatch = new StyleMatchSpec();
		fireOnce = TriggerFireOnce.OFF;
		hidden = false;
		enabled = true;
		sequence = DEFAULT_SEQUENCE;
		group = DEFAULT_GROUP;
		keepEvaluating = DEFAULT_KEEPEVAL;
		buildData();
	}
	
	public TriggerData copy() {
		TriggerData tmp = new TriggerData();
		tmp.name = this.name;
		tmp.pattern = this.pattern;
		tmp.resolvedPattern = this.resolvedPattern;
		tmp.interpretAsRegex = this.interpretAsRegex;
		tmp.fireOnce = this.fireOnce;
		tmp.hidden = this.hidden;
		tmp.enabled = this.enabled;
		tmp.sequence = this.sequence;
		tmp.group = this.group;
		tmp.keepEvaluating = this.keepEvaluating;
		for(TriggerResponder responder : this.responders) {
			tmp.responders.add(responder.copy());
		}
		tmp.conditions = this.conditions != null ? this.conditions.copy() : new ConditionGroup();
		tmp.styleMatch = this.styleMatch != null ? this.styleMatch.copy() : new StyleMatchSpec();
		tmp.buildData();
		return tmp;
	}
	
	/**
	 * The complaint from the last pattern that would not compile, or null.
	 *
	 * @return Text fit to show the player, naming what is wrong with the pattern.
	 */
	public String getPatternError() {
		return patternError;
	}

	private String patternError;

	/**
	 * Alias-resolved pattern, stored beside raw {@code pattern} (editor/XML).
	 * Crosses the binder so UI tap rules compile the resolved form.
	 */
	private String resolvedPattern;

	/**
	 * Paste in the aliases this trigger's pattern names.
	 *
	 * <p>Called by {@code Connection.buildTriggerSystem} for every trigger each
	 * time the system is rebuilt, which is also what happens when an alias is
	 * added, edited or removed -- so a trigger follows the alias it names.
	 *
	 * @param bodies Alias name to body, from
	 *     {@link TriggerAliasReference#bodies}. Null clears any resolution.
	 * @return true when the compiled pattern changed, so a caller rebuilding a
	 *     larger structure knows it has to.
	 */
	public boolean resolveAliases(final java.util.Map<String, String> bodies) {
		String next = bodies == null ? null : TriggerAliasReference.resolve(pattern, bodies);
		if (next != null && next.equals(pattern)) {
			// resolve() hands back the same text when it changed nothing, and a
			// null resolvedPattern is what "compile the pattern itself" means.
			next = null;
		}
		if (next == null ? resolvedPattern == null : next.equals(resolvedPattern)) {
			return false;
		}
		resolvedPattern = next;
		buildData();
		return true;
	}

	/**
	 * The text this trigger actually matches against.
	 *
	 * @return The resolved pattern when an alias was pasted in, otherwise the
	 *     pattern as the player wrote it.
	 */
	public String getEffectivePattern() {
		return resolvedPattern != null ? resolvedPattern : pattern;
	}

	private void buildData() {
		//if(p == null || p.equals("")) return;
		patternError = null;
		// The resolved form when an alias was pasted in; the player's text
		// otherwise. Everything below compiles against this, and nothing below
		// may write it back -- getPattern() has to keep returning what the
		// player typed or the profile would be saved with the alias expanded.
		final String source = this.interpretAsRegex
				? stripRegexFormatChars(getEffectivePattern())
				: getEffectivePattern();
		if (this.interpretAsRegex) {
			try {
				this.p = Pattern.compile(source);
			} catch (java.util.regex.PatternSyntaxException bad) {
				// A player's regex is untrusted input, and this runs in two
				// places that must not die on it: the trigger editor, on the UI
				// thread, and the settings parser while loading a profile. An
				// unguarded compile here meant one mistyped bracket could crash
				// the editor, and — once saved — stop the whole profile loading.
				patternError = bad.getDescription() != null
						? bad.getDescription() + " (at position " + bad.getIndex() + ")"
						: bad.getMessage();
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
						"TriggerData.buildData: bad pattern in trigger '" + name + "'", bad);
				// Fall back to matching the text literally. It is what the player
				// typed, so it stays predictable, and a trigger that quietly
				// matches nothing is harder to notice than one that misbehaves.
				this.p = Pattern.compile(Pattern.quote(source));
			}
		} else {
			// Pattern.quote rather than hand-built \Q...\E: a literal trigger
			// containing "\E" ended the quoted span early and left the rest of
			// the text to be read as a regex, which threw on the next bracket.
			this.p = Pattern.compile(Pattern.quote(source));
		}
		this.m = p.matcher("");
	}

	/**
	 * Drop Unicode format characters (zero-width space, BOM, …) from a regex
	 * before compiling. They are what a paste from a chat app leaves in front
	 * of {@code ^}, so {@code ^earned} never matches the start of the line.
	 * The player's stored pattern is unchanged; only the matcher ignores them.
	 * Literal triggers keep the characters — a ZWSP in the game text is real.
	 */
	static String stripRegexFormatChars(final String source) {
		if (source == null || source.isEmpty()) {
			return source;
		}
		int n = source.length();
		int i = 0;
		while (i < n) {
			int cp = source.codePointAt(i);
			if (Character.getType(cp) == Character.FORMAT) {
				break;
			}
			i += Character.charCount(cp);
		}
		if (i >= n) {
			return source;
		}
		StringBuilder out = new StringBuilder(n);
		out.append(source, 0, i);
		while (i < n) {
			int cp = source.codePointAt(i);
			if (Character.getType(cp) != Character.FORMAT) {
				out.appendCodePoint(cp);
			}
			i += Character.charCount(cp);
		}
		return out.toString();
	}
	
	public Matcher getMatcher() {
		return m;
	}

	public boolean equals(Object o) {
		if(o == this) return true;
		if(!(o instanceof TriggerData)) return false;
		TriggerData test = (TriggerData)o;
		if(!test.name.equals(this.name)) return false;
		if(!test.pattern.equals(this.pattern)) return false;
		if(test.interpretAsRegex != this.interpretAsRegex) return false;
		if(test.fireOnce != this.fireOnce) return false;
		if(test.hidden != this.hidden) return false;
		if(test.enabled != this.enabled) return false;
		if(test.sequence != this.sequence) return false;
		if(test.group != this.group) return false;
		if(test.keepEvaluating != this.keepEvaluating) return false;
		ConditionGroup otherCond = test.conditions != null ? test.conditions : new ConditionGroup();
		ConditionGroup myCond = this.conditions != null ? this.conditions : new ConditionGroup();
		if(!otherCond.equals(myCond)) return false;
		StyleMatchSpec otherStyle = test.styleMatch != null ? test.styleMatch : new StyleMatchSpec();
		StyleMatchSpec myStyle = this.styleMatch != null ? this.styleMatch : new StyleMatchSpec();
		if(!otherStyle.equals(myStyle)) return false;
		if(test.responders.size() != this.responders.size()) return false;
		Iterator<TriggerResponder> test_responders = test.responders.iterator();
		Iterator<TriggerResponder> my_responders = this.responders.iterator();
		while(test_responders.hasNext()) {
			TriggerResponder test_responder = test_responders.next();
			TriggerResponder my_responder = my_responders.next();
			
			if(!test_responder.equals(my_responder)) return false;
		}
		
		return true;
	}
	
	public static final Parcelable.Creator<TriggerData> CREATOR = new Parcelable.Creator<TriggerData>() {

		public TriggerData createFromParcel(Parcel arg0) {
			return new TriggerData(arg0);
		}

		public TriggerData[] newArray(int arg0) {
			return new TriggerData[arg0];
		}
	};
	public static final int DEFAULT_SEQUENCE = 10;
	public static final String DEFAULT_GROUP = "";
	public static final boolean DEFAULT_KEEPEVAL = true;
	public TriggerData(Parcel in) {
		readFromParcel(in);
		// readFromParcel uses the pattern-then-flag order, so this rebuild used to
		// be what kept triggers crossing the binder healthy while ones read from
		// the profile XML were not. setInterpretAsRegex rebuilds now, so this is
		// belt and braces rather than load-bearing.
		buildData();
	}
	
	public void readFromParcel(Parcel in) {
		setName(in.readString());
		setPattern(in.readString());
		// After setPattern, which clears it: a resolution belongs to the text it
		// was made from, and here that text has just arrived with it.
		this.resolvedPattern = in.readString();
		setResponders(new ArrayList<TriggerResponder>());
		setInterpretAsRegex( (in.readInt() == 1) ? true : false);
		setFireOnce(TriggerFireOnce.fromParcel(in.readInt()));
		setHidden( (in.readInt() == 1) ? true : false);
		setEnabled( (in.readInt() == 1) ? true : false);
		setSequence((in.readInt()));
		setGroup(in.readString());
		setKeepEvaluating((in.readInt() == 1) ? true : false);
		int numresponders = in.readInt();
		for(int i = 0;i<numresponders;i++) {
			int type = in.readInt();
			switch(type) {
			case TriggerResponder.RESPONDER_TYPE_NOTIFICATION:
				NotificationResponder resp = in.readParcelable(com.resurrection.blowtorch2.lib.responder.notification.NotificationResponder.class.getClassLoader());
				
				responders.add(resp);
				break;
			case TriggerResponder.RESPONDER_TYPE_SPEAK:
				com.resurrection.blowtorch2.lib.responder.speak.SpeakResponder speak =
						in.readParcelable(com.resurrection.blowtorch2.lib.responder.speak.SpeakResponder.class.getClassLoader());

				responders.add(speak);
				break;
			case TriggerResponder.RESPONDER_TYPE_SOUND:
				com.resurrection.blowtorch2.lib.responder.sound.SoundResponder sound =
						in.readParcelable(com.resurrection.blowtorch2.lib.responder.sound.SoundResponder.class.getClassLoader());

				responders.add(sound);
				break;
			case TriggerResponder.RESPONDER_TYPE_TOAST:
				ToastResponder toasty = in.readParcelable(com.resurrection.blowtorch2.lib.responder.toast.ToastResponder.class.getClassLoader());

				responders.add(toasty);
				break;
			case TriggerResponder.RESPONDER_TYPE_ACK:
				AckResponder ack = in.readParcelable(com.resurrection.blowtorch2.lib.responder.ack.AckResponder.class.getClassLoader());
				
				responders.add(ack);
				break;
			case TriggerResponder.RESPONDER_TYPE_SCRIPT:
				ScriptResponder scr = in.readParcelable(com.resurrection.blowtorch2.lib.responder.script.ScriptResponder.class.getClassLoader());
				
				responders.add(scr);
				break;
			case TriggerResponder.RESPONDER_TYPE_GAG:
				GagAction gag = in.readParcelable(com.resurrection.blowtorch2.lib.responder.gag.GagAction.class.getClassLoader());
				responders.add(gag);
				break;
			case TriggerResponder.RESPONDER_TYPE_REPLACE:
				ReplaceResponder rep = in.readParcelable(com.resurrection.blowtorch2.lib.responder.replace.ReplaceResponder.class.getClassLoader());
				responders.add(rep);
				break;
			case TriggerResponder.RESPONDER_TYPE_COLOR:
				ColorAction color = in.readParcelable(com.resurrection.blowtorch2.lib.responder.color.ColorAction.class.getClassLoader());
				responders.add(color);
				break;
			case TriggerResponder.RESPONDER_TYPE_TAP:
				com.resurrection.blowtorch2.lib.responder.tap.TapAction tap =
						in.readParcelable(com.resurrection.blowtorch2.lib.responder.tap.TapAction.class.getClassLoader());
				responders.add(tap);
				break;
			case TriggerResponder.RESPONDER_TYPE_SET_VARIABLE:
				SetVariableResponder setVar = in.readParcelable(com.resurrection.blowtorch2.lib.responder.setvariable.SetVariableResponder.class.getClassLoader());
				responders.add(setVar);
				break;
			case TriggerResponder.RESPONDER_TYPE_CHAT_THREAD:
				com.resurrection.blowtorch2.lib.responder.chat.ChatThreadResponder chat =
						in.readParcelable(com.resurrection.blowtorch2.lib.responder.chat.ChatThreadResponder.class.getClassLoader());
				responders.add(chat);
				break;
			default:
				// Unknown type must still consume the parcelable written after the
				// type int, or every field after responders (including conditions)
				// desynchronises across the binder.
				in.readParcelable(TriggerResponder.class.getClassLoader());
				break;
			}
		}
		conditions = in.readParcelable(ConditionGroup.class.getClassLoader());
		if (conditions == null) {
			conditions = new ConditionGroup();
		}
		styleMatch = in.readParcelable(StyleMatchSpec.class.getClassLoader());
		if (styleMatch == null) {
			styleMatch = new StyleMatchSpec();
		}
	}
	
	//save these for later.
	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel out, int arg1) {
		out.writeString(name);
		out.writeString(pattern);
		// The resolved form travels too. The UI process builds the tappable-word
		// rules from getCompiledPattern() on a trigger it received over the
		// binder, and without this it compiled the alias's *name* -- the word
		// never lit up and could not be pressed. getPattern() is still the raw
		// text, which is what the editor shows and what is written back.
		out.writeString(resolvedPattern);
		out.writeInt( interpretAsRegex ? 1 : 0);
		out.writeInt(fireOnce == null ? 0 : fireOnce.toParcel());
		out.writeInt(hidden ? 1 : 0);
		out.writeInt(enabled ? 1 : 0);
		out.writeInt(sequence);
		out.writeString(group);
		out.writeInt(keepEvaluating ? 1 : 0);
		out.writeInt(responders.size());
		for(TriggerResponder responder : responders) {
			//if(responder instanceof GagAction) {
				
			//} else {
				out.writeInt(responder.getType().getIntVal());
				out.writeParcelable(responder, 0);
			//}
		}
		out.writeParcelable(conditions != null ? conditions : new ConditionGroup(), 0);
		out.writeParcelable(styleMatch != null ? styleMatch : new StyleMatchSpec(), 0);
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setPattern(String pattern) {
		this.pattern = pattern;
		// A resolution belongs to the text it was made from. buildTriggerSystem
		// makes a new one; until then this trigger matches what it now says.
		this.resolvedPattern = null;
		buildData();
	}

	public String getPattern() {
		return pattern;
	}

	public void setInterpretAsRegex(boolean interpretAsRegex) {
		this.interpretAsRegex = interpretAsRegex;
		// Without this the setters were order-dependent, and the two parsers
		// disagreed on the order: TriggerElementListener sets the flag first,
		// HyperSAXParser sets the pattern first. On the second path buildData()
		// ran while interpretAsRegex was still the default false, so it built
		// Pattern.quote(pattern) and nothing rebuilt it — every regex trigger
		// loaded from the saved profile matched only its own pattern text
		// typed out verbatim, and reported groupCount 0 while the flag read true.
		buildData();
	}

	public boolean isInterpretAsRegex() {
		return interpretAsRegex;
	}

	public void setResponders(List<TriggerResponder> responders) {
		this.responders = responders;
	}

	public List<TriggerResponder> getResponders() {
		return responders;
	}

	public void setFireOnce(boolean fireOnce) {
		this.fireOnce = fireOnce ? TriggerFireOnce.UNTIL_ENABLE : TriggerFireOnce.OFF;
	}

	public void setFireOnce(TriggerFireOnce fireOnce) {
		this.fireOnce = fireOnce != null ? fireOnce : TriggerFireOnce.OFF;
	}

	public TriggerFireOnce getFireOnce() {
		return fireOnce != null ? fireOnce : TriggerFireOnce.OFF;
	}

	public boolean isFireOnce() {
		return getFireOnce().quietsAfterFire();
	}

	public void setFired(boolean fired) {
		this.fired = fired;
	}

	public boolean isFired() {
		return fired;
	}

	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}

	public boolean isHidden() {
		return hidden;
	}

	public void setEnabled(boolean enabled) {
		if (enabled && !this.enabled) {
			fired = false;
		}
		this.enabled = enabled;
	}

	public boolean isEnabled() {
		return enabled;
	}
	
	public Pattern getCompiledPattern() {
		return p;
	}
	//public boolean matches(CharSequence text) {
		//m.reset(text);

	public void setSave(boolean save) {
		this.save = save;
	}

	public boolean isSave() {
		return save;
	}

	public int getSequence() {
		return sequence;
	}

	public void setSequence(int sequence) {
		this.sequence = sequence;
	}

	public boolean isKeepEvaluating() {
		return keepEvaluating;
	}

	public void setKeepEvaluating(boolean keepEvaluating) {
		this.keepEvaluating = keepEvaluating;
	}

	public String getGroup() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group;
	}

	public ConditionGroup getConditions() {
		if (conditions == null) {
			conditions = new ConditionGroup();
		}
		return conditions;
	}

	public void setConditions(ConditionGroup conditions) {
		this.conditions = conditions != null ? conditions : new ConditionGroup();
	}

	public StyleMatchSpec getStyleMatch() {
		if (styleMatch == null) {
			styleMatch = new StyleMatchSpec();
		}
		return styleMatch;
	}

	public void setStyleMatch(StyleMatchSpec styleMatch) {
		this.styleMatch = styleMatch != null ? styleMatch : new StyleMatchSpec();
	}

	public boolean isBlankPattern() {
		return pattern == null || pattern.trim().length() == 0;
	}

	/** Style recipe with no text pattern — matched as runs, not regex. */
	public boolean isStyleOnly() {
		return isBlankPattern() && getStyleMatch().isActive();
	}

}
