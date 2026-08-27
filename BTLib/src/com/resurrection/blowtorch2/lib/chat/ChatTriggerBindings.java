package com.resurrection.blowtorch2.lib.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import android.os.RemoteException;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.chat.ChatThreadResponder;
import com.resurrection.blowtorch2.lib.service.IConnectionBinder;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;
import com.resurrection.blowtorch2.lib.util.BlowTorchLogger;

/**
 * Round-trip Send-to-thread reply templates for a conversation whose stored
 * id equals the action's thread field (e.g. {@code _vermin}).
 *
 * <p>Match is literal {@link ChatThreadResponder#getThreadId()} equals. A
 * {@code $1} action does not match a conversation named Bob; those threads
 * keep {@code chat.json} as the Send template.
 */
public final class ChatTriggerBindings {

	private ChatTriggerBindings() {
	}

	/**
	 * First matching responder whose template is non-null. Empty string is a
	 * hit (the trigger owns the template, it is just blank).
	 *
	 * @return that template, or null when no matching action exists
	 */
	public static String loadReplyTemplate(IConnectionBinder service, String threadId) {
		if (service == null || threadId == null) {
			return null;
		}
		try {
			String found = firstNonNullTemplate(service.getTriggerData(), threadId);
			if (found != null) {
				return found;
			}
			List<?> plugins = service.getPluginsWithTriggers();
			if (plugins == null) {
				return null;
			}
			for (int i = 0; i < plugins.size(); i++) {
				Object plugin = plugins.get(i);
				if (!(plugin instanceof String)) {
					continue;
				}
				found = firstNonNullTemplate(
						service.getPluginTriggerData((String) plugin), threadId);
				if (found != null) {
					return found;
				}
			}
		} catch (RemoteException e) {
			BlowTorchLogger.logThrowable("ChatTriggerBindings.loadReplyTemplate", e);
		}
		return null;
	}

	/**
	 * True when any Send-to-thread action stores this conversation id, even if
	 * its template is empty.
	 */
	public static boolean hasChatTrigger(IConnectionBinder service, String threadId) {
		if (service == null || threadId == null) {
			return false;
		}
		try {
			if (hasMatchingResponder(service.getTriggerData(), threadId)) {
				return true;
			}
			List<?> plugins = service.getPluginsWithTriggers();
			if (plugins == null) {
				return false;
			}
			for (int i = 0; i < plugins.size(); i++) {
				Object plugin = plugins.get(i);
				if (!(plugin instanceof String)) {
					continue;
				}
				if (hasMatchingResponder(
						service.getPluginTriggerData((String) plugin), threadId)) {
					return true;
				}
			}
		} catch (RemoteException e) {
			BlowTorchLogger.logThrowable("ChatTriggerBindings.hasChatTrigger", e);
		}
		return false;
	}

	/**
	 * Write {@code template} onto every matching Send-to-thread action (main
	 * settings, then each plugin that has triggers). Same from/to split as
	 * {@code TriggerEditorDialog}: {@code from = t.copy()}, mutate {@code t},
	 * then {@code updateTrigger}/{@code updatePluginTrigger}. {@code saveSettings}
	 * once if anything changed.
	 */
	public static void saveReplyTemplate(IConnectionBinder service, String threadId,
			String template) {
		if (service == null || threadId == null) {
			return;
		}
		String tmpl = template == null ? "" : template;
		boolean changed = false;
		try {
			if (saveInMap(service, null, service.getTriggerData(), threadId, tmpl)) {
				changed = true;
			}
			List<?> plugins = service.getPluginsWithTriggers();
			if (plugins != null) {
				for (int i = 0; i < plugins.size(); i++) {
					Object plugin = plugins.get(i);
					if (!(plugin instanceof String)) {
						continue;
					}
					String pluginId = (String) plugin;
					if (saveInMap(service, pluginId,
							service.getPluginTriggerData(pluginId), threadId, tmpl)) {
						changed = true;
					}
				}
			}
			if (changed) {
				service.saveSettings();
			}
		} catch (RemoteException e) {
			BlowTorchLogger.logThrowable("ChatTriggerBindings.saveReplyTemplate", e);
		}
	}

	@SuppressWarnings("rawtypes")
	private static String firstNonNullTemplate(Map map, String threadId) {
		if (map == null) {
			return null;
		}
		for (Object value : map.values()) {
			if (!(value instanceof TriggerData)) {
				continue;
			}
			List<TriggerResponder> responders = ((TriggerData) value).getResponders();
			if (responders == null) {
				continue;
			}
			for (int i = 0; i < responders.size(); i++) {
				ChatThreadResponder chat = matchingChat(responders.get(i), threadId);
				if (chat == null) {
					continue;
				}
				String tmpl = chat.getReplyTemplate();
				if (tmpl != null) {
					return tmpl;
				}
			}
		}
		return null;
	}

	@SuppressWarnings("rawtypes")
	private static boolean hasMatchingResponder(Map map, String threadId) {
		if (map == null) {
			return false;
		}
		for (Object value : map.values()) {
			if (!(value instanceof TriggerData)) {
				continue;
			}
			if (triggerHasMatch((TriggerData) value, threadId)) {
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("rawtypes")
	private static boolean saveInMap(IConnectionBinder service, String plugin,
			Map map, String threadId, String template) throws RemoteException {
		if (map == null) {
			return false;
		}
		ArrayList<TriggerData> hits = new ArrayList<TriggerData>();
		for (Object value : map.values()) {
			if (!(value instanceof TriggerData)) {
				continue;
			}
			TriggerData t = (TriggerData) value;
			if (triggerHasMatch(t, threadId)) {
				hits.add(t);
			}
		}
		boolean any = false;
		for (int i = 0; i < hits.size(); i++) {
			TriggerData t = hits.get(i);
			TriggerData from = t.copy();
			if (!setMatchingTemplates(t, threadId, template)) {
				continue;
			}
			if (plugin == null) {
				service.updateTrigger(from, t);
			} else {
				service.updatePluginTrigger(plugin, from, t);
			}
			any = true;
		}
		return any;
	}

	private static boolean triggerHasMatch(TriggerData t, String threadId) {
		List<TriggerResponder> responders = t.getResponders();
		if (responders == null) {
			return false;
		}
		for (int i = 0; i < responders.size(); i++) {
			if (matchingChat(responders.get(i), threadId) != null) {
				return true;
			}
		}
		return false;
	}

	private static boolean setMatchingTemplates(TriggerData t, String threadId,
			String template) {
		List<TriggerResponder> responders = t.getResponders();
		if (responders == null) {
			return false;
		}
		boolean changed = false;
		for (int i = 0; i < responders.size(); i++) {
			ChatThreadResponder chat = matchingChat(responders.get(i), threadId);
			if (chat == null) {
				continue;
			}
			String current = chat.getReplyTemplate();
			if (current == null) {
				current = "";
			}
			if (!current.equals(template)) {
				chat.setReplyTemplate(template);
				changed = true;
			}
		}
		return changed;
	}

	private static ChatThreadResponder matchingChat(TriggerResponder responder,
			String threadId) {
		if (!(responder instanceof ChatThreadResponder)) {
			return null;
		}
		ChatThreadResponder chat = (ChatThreadResponder) responder;
		if (!threadId.equals(chat.getThreadId())) {
			return null;
		}
		return chat;
	}
}
