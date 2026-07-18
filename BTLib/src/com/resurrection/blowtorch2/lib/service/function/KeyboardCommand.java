package com.resurrection.blowtorch2.lib.service.function;

import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.resurrection.blowtorch2.lib.service.Connection;

public class KeyboardCommand extends SpecialCommand {
	String encoding;
	public KeyboardCommand() {
		this.commandName = "keyboard";
		this.encoding = "UTF-8";
	}
	
	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}
	
	public Object execute(Object o,Connection c) {
		boolean failed = false;
		if(o==null) {
			failed = true;
		} else if(((String)o).equals("")) {
			failed = true;
		}
		
		if(failed) {
			c.sendDataToWindow(getErrorMessage("Keyboard (kb) special command usage:",".kb options [message]\n" +
					"Text ops: add, popup, flush, close, clear\n" +
					"Edit ops: sel | selectall, cut, copy, paste\n" +
					"Cursor: start | cursorstart, end | cursorend,\n" +
					"        stepf | stepr (right), stepb | stepl (left),\n" +
					"        stepu (history ↑ / older), stepd (history ↓ / newer)\n" +
					"Examples:\n" +
					"  .kb popup reply   — set text and show IME\n" +
					"  .kb add foo       — append without popup\n" +
					"  .kb flush         — send current input\n" +
					"  .kb sel / .kb cut — select all / cut\n" +
					"  .kb start / .kb end — caret to start / end\n" +
					"  .kb stepf / .kb stepb — caret ±1 character\n" +
					"  .kb stepu / .kb stepd — previous / next command\n"));			return null;
		}

		Pattern p = Pattern.compile(
				"^\\s*(add|popup|flush|close|clear|selectall|sel|copy|cut|paste|"
				+ "cursorstart|start|cursorend|end|"
				+ "stepf|stepr|stepb|stepl|stepu|stepd)"
				+ "{0,1}\\s*(add\\s+|popup\\s+|flush\\s+){0,1}(.*)$",
				Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher((String)o);
		String operation1 = "";
		String operation2 = "";
		String text = "";
		if(m.matches()) {
			operation1 = m.group(1);
			operation2 = m.group(2);
			text = m.group(3);
		}
		boolean doadd = false;
		boolean dopopup = false;
		boolean doflush = false;
		boolean doclear = false;
		boolean doclose = false;
		
		if(operation1 != null && !operation1.equals("")) {
			operation1 = operation1.replaceAll("\\s", "");
			if(operation1.equalsIgnoreCase("add")) {
				doadd = true;
			}
			if(operation1.equalsIgnoreCase("popup")) {
				dopopup = true;
			}
		}
		if(operation2 != null && !operation2.equals("")) {
			operation2 = operation2.replaceAll("\\s", "");
			if(operation2.equalsIgnoreCase("add")) {
				doadd = true;
			}
			if(operation2.equalsIgnoreCase("popup")) {
				dopopup = true;
			}
		}
		
		if(operation1 != null && !operation1.equals("")) {
			String op = operation1.toLowerCase();
			if(op.equals("flush")) {
				doflush = true;
			} else if(op.equals("clear")) {
				doclear = true;
			} else if(op.equals("close")) {
				doclose = true;
			} else if(op.equals("selectall") || op.equals("sel")) {
				c.getService().doInputBarSelectAll();
				return null;
			} else if(op.equals("copy")) {
				c.getService().doInputBarCopy();
				return null;
			} else if(op.equals("cut")) {
				c.getService().doInputBarCut();
				return null;
			} else if(op.equals("paste")) {
				c.getService().doInputBarPaste();
				return null;
			} else if(op.equals("cursorstart") || op.equals("start")) {
				c.getService().doInputBarCursorToStart();
				return null;
			} else if(op.equals("cursorend") || op.equals("end")) {
				c.getService().doInputBarCursorToEnd();
				return null;
			} else if(op.equals("stepf") || op.equals("stepr")) {
				c.getService().doInputBarCursorStep(1);
				return null;
			} else if(op.equals("stepb") || op.equals("stepl")) {
				c.getService().doInputBarCursorStep(-1);
				return null;
			} else if(op.equals("stepu")) {
				c.getService().doInputBarCursorVertical(-1);
				return null;
			} else if(op.equals("stepd")) {
				c.getService().doInputBarCursorVertical(1);
				return null;
			}
		}
		
		Boolean foo = new Boolean(true);
		try {
			text = new String(c.doKeyboardAliasReplace(text.getBytes(encoding),foo),encoding);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		
		c.getService().doShowKeyboard(text,dopopup,doadd,doflush,doclear,doclose);
		return null;
	}
}
