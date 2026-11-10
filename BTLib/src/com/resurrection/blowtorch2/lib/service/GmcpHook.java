package com.resurrection.blowtorch2.lib.service;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code OnGmcp} on one message. The module name is not rewritten. The body
 * is UTF-8 JSON text, or empty when the message had none. The same byte
 * array means leave that body. A new empty array drops the message.
 */
public final class GmcpHook {

	/** Body after the hook. {@code dropped} means no consumer should see it. */
	public static final class Result {
		public final boolean dropped;
		/** Null when the message had no body and was not dropped. */
		public final String body;

		private Result(final boolean dropped, final String body) {
			this.dropped = dropped;
			this.body = body;
		}
	}

	/** One plugin. {@code module} is the message being rewritten. */
	public interface Step {
		/**
		 * @param body the JSON bytes so far, never null. Empty when there was no body.
		 * @return replacement bytes, or null to keep {@code body}
		 */
		byte[] apply(String module, byte[] body) throws Exception;
	}

	/**
	 * Connection supplies this. A null result is treated as "leave the body".
	 */
	public interface Rewriter {
		Result rewrite(String module, String body);
	}

	private GmcpHook() {
	}

	public static Result leave(final String body) {
		return new Result(false, body);
	}

	public static Result drop() {
		return new Result(true, null);
	}

	/** False for a disabled plugin and for a plugin with no {@code OnGmcp}. */
	public static boolean include(final boolean enabled, final boolean offers) {
		return enabled && offers;
	}

	/**
	 * @param body JSON text, or null when the message named a module only.
	 *             Empty filters return that same body.
	 */
	public static Result apply(final String module, final String body,
			final List<Step> steps) throws UnsupportedEncodingException {
		if (steps == null || steps.isEmpty()) {
			return leave(body);
		}
		final String name = module == null ? "" : module;
		List<InboundTextHook.Filter> filters = new ArrayList<InboundTextHook.Filter>(steps.size());
		for (int i = 0; i < steps.size(); i++) {
			final Step step = steps.get(i);
			if (step == null) {
				continue;
			}
			filters.add(new InboundTextHook.Filter() {
				@Override
				public byte[] apply(final byte[] text) throws Exception {
					return step.apply(name, text);
				}
			});
		}
		byte[] in = body == null ? new byte[0] : body.getBytes("UTF-8");
		byte[] out = InboundTextHook.apply(in, filters);
		if (out == in) {
			return leave(body);
		}
		if (out.length == 0) {
			return drop();
		}
		if (Arrays.equals(out, in)) {
			return leave(body);
		}
		return new Result(false, new String(out, "UTF-8"));
	}
}
