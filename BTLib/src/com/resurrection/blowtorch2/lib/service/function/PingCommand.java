package com.resurrection.blowtorch2.lib.service.function;

import java.util.concurrent.atomic.AtomicBoolean;

import android.os.Handler;

import com.resurrection.blowtorch2.lib.launcher.BuiltinTutorial;
import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.util.IcmpPing;

/**
 * {@code .ping} sends one ICMP echo to this world's host. It is not sent to
 * the world.
 */
public class PingCommand extends SpecialCommand {

	private final AtomicBoolean inFlight = new AtomicBoolean(false);

	public PingCommand() {
		this.commandName = "ping";
	}

	@Override
	public Object execute(final Object o, final Connection c) {
		String arg = o == null ? "" : ((String) o).trim();
		if (arg.length() > 0) {
			c.sendDataToWindow(getErrorMessage("Ping usage",
					"Just .ping — it uses this world's host."));
			return null;
		}
		String host = IcmpPing.sanitize(c.getHost());
		if (host == null) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "This world has no host to ping.\n");
			return null;
		}
		if (BuiltinTutorial.isTutorialHost(host)) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Starter Tutorial is offline; there is no host to ping.\n");
			return null;
		}
		if (!inFlight.compareAndSet(false, true)) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Already pinging.\n");
			return null;
		}
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "Pinging " + host + "…\n");
		final Handler handler = c.getHandler();
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				String line;
				try {
					line = IcmpPing.run(host);
				} finally {
					inFlight.set(false);
				}
				final String report = line;
				if (handler != null) {
					handler.post(new Runnable() {
						@Override
						public void run() {
							if (c.getHandler() == null) {
								return;
							}
							c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
									+ report + "\n");
						}
					});
				}
			}
		}, "bt-icmp-ping");
		try {
			t.start();
		} catch (RuntimeException e) {
			inFlight.set(false);
			throw e;
		}
		return null;
	}
}
