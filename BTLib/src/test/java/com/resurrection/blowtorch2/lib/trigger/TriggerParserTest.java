package com.resurrection.blowtorch2.lib.trigger;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.service.plugin.settings.SettingsOptionXmlTest;

import java.util.regex.Matcher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TriggerParserTest {

	@Test
	public void anchoredLiteralMatchesLineStart() {
		TriggerData trigger = new TriggerData();
		trigger.setInterpretAsRegex(true);
		trigger.setPattern("^You see a dragon here\\.$");
		Matcher matcher = trigger.getMatcher();
		assertTrue(matcher.reset("You see a dragon here.").find());
		assertFalse(matcher.reset("Suddenly, You see a dragon here.").find());
	}

	@Test
	public void wildcardRegexMatchesVariableMiddle() {
		TriggerData trigger = new TriggerData();
		trigger.setInterpretAsRegex(true);
		trigger.setPattern("A .+ dragon appears");
		Matcher matcher = trigger.getMatcher();
		assertTrue(matcher.reset("A fierce red dragon appears").find());
		assertFalse(matcher.reset("dragon appears").find());
	}

	@Test
	public void multilineLiteralMatchesAcrossLines() {
		TriggerData trigger = new TriggerData();
		trigger.setInterpretAsRegex(false);
		trigger.setPattern("second line");
		Matcher matcher = trigger.getMatcher();
		assertTrue(matcher.reset("first line\nsecond line\nthird line").find());
	}

	@Test
	public void saveTriggerToXmlSmoke() throws Exception {
		TriggerData trigger = new TriggerData();
		trigger.setName("dragon");
		trigger.setPattern("A .+ dragon appears");
		trigger.setInterpretAsRegex(true);
		trigger.setSave(true);
		SettingsOptionXmlTest.RecordingXmlSerializer out =
				new SettingsOptionXmlTest.RecordingXmlSerializer();
		TriggerParser.saveTriggerToXML(out, trigger);
		String xml = out.toString();
		assertTrue(xml.contains("<trigger"));
		assertTrue(xml.contains("title=\"dragon\""));
		assertTrue(xml.contains("pattern=\"A .+ dragon appears\""));
		assertTrue(xml.contains("regexp=\"true\""));
		assertTrue(xml.contains("</trigger>"));
	}
}
