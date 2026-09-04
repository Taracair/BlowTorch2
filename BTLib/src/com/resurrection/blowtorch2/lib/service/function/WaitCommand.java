package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.CommandWait;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * {@code .wait} — listed in {@code .help}. The iterator in
 * {@code Connection.processOutputData} consumes the token so the rest of the
 * line can pause; this execute path is the fallback when the whole line is
 * {@code .wait …} and that interceptor did not run.
 */
public class WaitCommand extends SpecialCommand {

	public WaitCommand() {
		this.commandName = "wait";
	}

	@Override
	public Object execute(final Object o, final Connection c) {
		String arg = o == null ? "" : ((String) o);
		CommandWait.Result r = CommandWait.parseArgument(arg);
		if (r.kind == CommandWait.Kind.STOP) {
			c.cancelCommandWaits();
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "[wait cancelled]\n");
			return null;
		}
		if (r.kind == CommandWait.Kind.ERROR) {
			c.sendDataToWindow(getErrorMessage("Wait command usage:",
					r.message == null ? CommandWait.USAGE : r.message));
			return null;
		}
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "[wait " + CommandWait.format(r.delayMs) + "] "
				+ "Nothing after wait on this line — later commands are not delayed. "
				+ "Use north;.wait 5s;south\n");
		return null;
	}
}
