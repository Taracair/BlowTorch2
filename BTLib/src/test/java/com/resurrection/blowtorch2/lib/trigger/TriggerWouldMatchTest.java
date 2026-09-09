package com.resurrection.blowtorch2.lib.trigger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.resurrection.blowtorch2.lib.trigger.style.StyleLineModel;
import com.resurrection.blowtorch2.lib.trigger.style.StyleMatchSpec;
import com.resurrection.blowtorch2.lib.trigger.style.StyleMatchSpec.Gate;
import com.resurrection.blowtorch2.lib.trigger.style.StyleSnapshot.ColorSpace;
import com.resurrection.blowtorch2.lib.window.TextTree;

import org.junit.Test;

public class TriggerWouldMatchTest {

	private static TriggerData trigger(final String name, final String pattern) {
		TriggerData t = new TriggerData();
		t.setName(name);
		t.setInterpretAsRegex(true);
		t.setPattern(pattern);
		t.setEnabled(true);
		return t;
	}

	@Test
	public void otherPatternOnTheSameLineIsAHit() {
		String chunk = "[chan]: title -- spam\n-- map --\n";
		TriggerData tag = trigger("tag", "\\[chan\\]:");
		int dashOnChan = chunk.indexOf("-- spam");
		int dashOnMap = chunk.indexOf("-- map");
		assertTrue(TriggerWouldMatch.onSameLine(tag, chunk, dashOnChan, null, null, null));
		assertFalse(TriggerWouldMatch.onSameLine(tag, chunk, dashOnMap, null, null, null));
	}

	@Test
	public void disabledOrBlankOtherIsClosed() {
		String line = "[chan]: -- spam";
		TriggerData tag = trigger("tag", "\\[chan\\]:");
		tag.setEnabled(false);
		assertFalse(TriggerWouldMatch.onSameLine(tag, line, 0, null, null, null));
		TriggerData blank = trigger("blank", "");
		assertFalse(TriggerWouldMatch.onSameLine(blank, line, 0, null, null, null));
		assertFalse(TriggerWouldMatch.onSameLine(null, line, 0, null, null, null));
	}

	@Test
	public void alsoOnTheOtherTriggerIsChecked() {
		String chunk = "[chan]: title -- spam\n-- map --\n";
		TriggerData dash = trigger("dash", "--");
		dash.setAlsoContains("[chan]:");
		dash.setAlsoLiteral(true);
		int chan = chunk.indexOf("-- spam");
		int map = chunk.indexOf("-- map");
		assertTrue(TriggerWouldMatch.onSameLine(dash, chunk, chan, null, null, null));
		assertFalse(TriggerWouldMatch.onSameLine(dash, chunk, map, null, null, null));
	}

	@Test
	public void styleOnTheOtherTriggerIsChecked() throws Exception {
		TextTree green = new TextTree();
		green.addBytesImpl("\u001B[32mloot\n".getBytes("UTF-8"));
		StyleLineModel[] greenModels = StyleLineModel.buildTree(green);
		TextTree red = new TextTree();
		red.addBytesImpl("\u001B[31mloot\n".getBytes("UTF-8"));
		StyleLineModel[] redModels = StyleLineModel.buildTree(red);
		int[] starts = new int[] { 0 };
		TriggerData t = new TriggerData();
		t.setName("green");
		t.setPattern("");
		t.setEnabled(true);
		StyleMatchSpec spec = new StyleMatchSpec();
		spec.setFg(Gate.REQUIRE, ColorSpace.ANSI16, 32);
		t.setStyleMatch(spec);
		assertTrue(TriggerWouldMatch.onSameLine(t, "loot\n", 0, greenModels, starts, null));
		assertFalse(TriggerWouldMatch.onSameLine(t, "loot\n", 0, redModels, starts, null));
	}
}
