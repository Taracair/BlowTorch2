package com.resurrection.blowtorch2.lib.alias;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.os.Handler;
import android.os.Message;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder.FIRE_WHEN;
import com.resurrection.blowtorch2.lib.responder.setvariable.SetVariableOp;
import com.resurrection.blowtorch2.lib.responder.setvariable.SetVariableResponder;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * Set Variable on an alias: same action as a trigger, fired when the player
 * types a matching line rather than when the game prints one.
 *
 * <p>Apply still goes through {@code MESSAGE_SET_VARIABLE} on the connection
 * handler. Alias replacement can run on that thread already, but posting keeps
 * the same ordering as a trigger's Set Variable and does not mutate the session
 * map in the middle of expanding {@code With}.
 */
public final class AliasSetVariables {

	private AliasSetVariables() {
	}

	/**
	 * Capture map used for Set Variable, the same map {@code With} spends.
	 */
	public static HashMap<String, String> captures(AliasData alias, String wholeInput,
			String matched) {
		Map<String, String> raw = AliasExpansion.captures(alias, wholeInput, matched);
		if (raw instanceof HashMap) {
			return (HashMap<String, String>) raw;
		}
		if (raw == null) {
			return new HashMap<String, String>();
		}
		return new HashMap<String, String>(raw);
	}

	/**
	 * Ops after {@code $1} translation, ignoring fire-when. Empty list when
	 * the alias has none or every name is empty.
	 */
	public static List<SetVariableOp> ops(AliasData alias, Map<String, String> captures) {
		List<SetVariableOp> out = new ArrayList<SetVariableOp>();
		if (alias == null) {
			return out;
		}
		List<SetVariableResponder> list = alias.getSetVariables();
		if (list == null || list.isEmpty()) {
			return out;
		}
		HashMap<String, String> map = asHashMap(captures);
		for (SetVariableResponder r : list) {
			if (r == null) {
				continue;
			}
			String key = r.translate(r.getVariableName(), map);
			String val = r.translate(r.getVariableValue(), map);
			if (key == null || key.length() == 0) {
				continue;
			}
			out.add(new SetVariableOp(key, val != null ? val : "", r.getMode(), r.isPersist()));
		}
		return out;
	}

	/**
	 * Honour fire-when the way {@code SetVariableResponder.doResponse} does, then
	 * post each op. No Open/Closed boxes on the alias editor, so rows stay
	 * {@code WINDOW_BOTH} unless a plugin XML said otherwise.
	 */
	public static void dispatch(AliasData alias, Map<String, String> captures,
			Handler dispatcher, boolean windowIsOpen) {
		if (alias == null || dispatcher == null) {
			return;
		}
		List<SetVariableResponder> list = alias.getSetVariables();
		if (list == null || list.isEmpty()) {
			return;
		}
		HashMap<String, String> map = asHashMap(captures);
		for (SetVariableResponder r : list) {
			if (r == null || !shouldFire(r, windowIsOpen)) {
				continue;
			}
			String key = r.translate(r.getVariableName(), map);
			String val = r.translate(r.getVariableValue(), map);
			if (key == null || key.length() == 0) {
				continue;
			}
			Message msg = dispatcher.obtainMessage(Connection.MESSAGE_SET_VARIABLE);
			msg.obj = new SetVariableOp(key, val != null ? val : "", r.getMode(), r.isPersist());
			dispatcher.sendMessage(msg);
		}
	}

	static boolean shouldFire(SetVariableResponder r, boolean windowIsOpen) {
		if (r == null || r.getFireType() == null) {
			return true;
		}
		FIRE_WHEN fire = r.getFireType();
		if (windowIsOpen) {
			return fire != FIRE_WHEN.WINDOW_CLOSED && fire != FIRE_WHEN.WINDOW_NEVER;
		}
		return fire != FIRE_WHEN.WINDOW_OPEN && fire != FIRE_WHEN.WINDOW_NEVER;
	}

	private static HashMap<String, String> asHashMap(Map<String, String> captures) {
		if (captures instanceof HashMap) {
			return (HashMap<String, String>) captures;
		}
		if (captures == null) {
			return new HashMap<String, String>();
		}
		return new HashMap<String, String>(captures);
	}
}
