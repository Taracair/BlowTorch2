package com.resurrection.blowtorch2.lib.service;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import android.os.Handler;

import com.resurrection.blowtorch2.lib.http.HttpRequestFetch;
import com.resurrection.blowtorch2.lib.service.plugin.Plugin;

/**
 * One pool per connection. Threads are daemon so a read still inside the
 * 10 second timeout does not keep the service process up after shutdown.
 * The worker never touches the plugin Lua state.
 */
public final class HttpRequestRunner {

	private final Object lock = new Object();
	private ExecutorService pool;
	private boolean closed;
	private int seq;

	/** The id, or null when this connection is already shutting down. */
	public Integer start(final Connection host, final Plugin plugin, final String method,
			final String url, final byte[] body, final Map<String, String> headers,
			final boolean followRedirects, final String function) {
		synchronized (lock) {
			if (closed || host.httpClosed() || host.mHandler == null) {
				return null;
			}
			if (pool == null) {
				pool = Executors.newFixedThreadPool(2, new ThreadFactory() {
					public Thread newThread(Runnable r) {
						Thread thread = new Thread(r, "bt-http");
						thread.setDaemon(true);
						return thread;
					}
				});
			}
			final int id = ++seq;
			pool.execute(new Runnable() {
				public void run() {
					HttpRequestFetch.Outcome outcome = HttpRequestFetch.fetch(method, url, body,
							headers, followRedirects);
					if (host.httpClosed()) {
						return;
					}
					Handler handler = host.mHandler;
					if (handler == null) {
						return;
					}
					handler.sendMessage(handler.obtainMessage(Connection.MESSAGE_HTTP_RESULT,
							new Delivery(plugin, function, id, outcome)));
				}
			});
			return Integer.valueOf(id);
		}
	}

	/** Does not wait. A blocked read ends on its own timeout. */
	public void shutdown() {
		ExecutorService running;
		synchronized (lock) {
			closed = true;
			running = pool;
			pool = null;
		}
		if (running != null) {
			running.shutdownNow();
		}
	}

	public static final class Delivery {
		public final Plugin plugin;
		public final String function;
		public final int id;
		public final HttpRequestFetch.Outcome outcome;

		Delivery(Plugin plugin, String function, int id, HttpRequestFetch.Outcome outcome) {
			this.plugin = plugin;
			this.function = function;
			this.id = id;
			this.outcome = outcome;
		}
	}
}
