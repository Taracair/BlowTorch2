package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * {@code .ping} is kept so the line is not sent to the world. There is no
 * overlay: typical MUDs do not answer Timing Mark or GMCP {@code Core.Ping},
 * and timing a typed command is not network ping.
 */
public class PingCommand extends SpecialCommand {

	public PingCommand() {
		this.commandName = "ping";
	}

	@Override
	public Object execute(final Object o, final Connection c) {
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "There is no ping overlay. This client cannot measure ICMP "
				+ "or TCP RTT to the world, and Timing Mark / Core.Ping stay "
				+ "unanswered on typical MUDs.\n");
		return null;
	}
}
