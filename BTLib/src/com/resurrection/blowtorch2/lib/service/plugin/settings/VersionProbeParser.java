package com.resurrection.blowtorch2.lib.service.plugin.settings;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.xml.sax.SAXException;

import android.content.Context;
import android.util.Log;

import com.resurrection.blowtorch2.lib.util.XmlRootProbe;

/**
 * Works out which settings format a file is in, by looking at its root element.
 *
 * <p>Both questions — is this the version 1 format, and what version does it
 * claim — used to be answered by handing the whole file to a SAX parser, once
 * each. On a real profile that is a quarter of a megabyte streamed twice, on the
 * service main thread, before the parse that actually loads anything. The
 * answers now come from {@link XmlRootProbe}, which stops at the first start
 * tag, and the file is read once no matter how many times these are called.
 */
public class VersionProbeParser extends BasePluginParser {

	/** The root element of the version 1 format. */
	private static final String LEGACY_ROOT = "root";
	/** The root element of the current format. */
	private static final String CURRENT_ROOT = "blowtorch";

	public VersionProbeParser(String location, Context context) {
		super(location, context);
	}

	private XmlRootProbe.Root probed;
	private boolean probeAttempted;

	/**
	 * Read the root element, once, whichever question is asked first.
	 *
	 * @return The root, reporting {@code found() == false} when the file could
	 *         not be opened or holds no markup. Never null.
	 */
	private XmlRootProbe.Root root() {
		if (probeAttempted) {
			return probed;
		}
		probeAttempted = true;
		probed = XmlRootProbe.none();
		InputStream in = null;
		try {
			Log.e("XMLPARSE", "ATTEMPTING VERSION PROBE OF SETTINGS ROOT ELEMENT");
			in = this.openDocumentStream();
			probed = XmlRootProbe.probe(in);
		} catch (FileNotFoundException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"VersionProbeParser.root", e);
		} catch (IOException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"VersionProbeParser.root", e);
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException ignored) {
					// Nothing useful to do about a failed close on a read.
				}
			}
		}
		return probed;
	}

	/**
	 * @return true when this is a version 1 settings file. A file that cannot be
	 *         read is not one, which is what the swallowed parse exception here
	 *         used to mean.
	 */
	public boolean isLegacy() throws FileNotFoundException, IOException, SAXException {
		boolean legacy = LEGACY_ROOT.equals(root().name());
		Log.e("XMLPARSE", legacy
				? "FOUND LEGACY ROOT NODE IN VERSION PROBE LEGACY TEST"
				: "MISSING LEGACY ROOT NODE IN VERSION PROBE LEGACY TEST");
		return legacy;
	}

	/**
	 * @return The declared xmlversion, or -1 when our root element carries none.
	 * @throws SAXException When the file has no readable root element, or a root
	 *         that is not ours. The SAX RootElement this replaced rejected a
	 *         mismatched root the same way, so the caller keeps being able to
	 *         tell "not our file" apart from "our file, no version attribute".
	 */
	public int getVersionNumber() throws FileNotFoundException, IOException, SAXException {
		XmlRootProbe.Root r = root();
		if (!r.found()) {
			throw new SAXException("No root element in settings file: " + path);
		}
		if (!CURRENT_ROOT.equals(r.name())) {
			throw new SAXException("Unexpected root element <" + r.name()
					+ "> in settings file: " + path);
		}
		int version = r.intAttribute("xmlversion", -1);
		Log.e("XMLPARSE", version >= 0
				? "FOUND APPROPRIATE BLOWTORCH ROOT NODE IN V2 SETTINGS FILE - FOUND VERSION " + version
				: "DID NOT FIND APPROPRIATE BLOWTORCH ROOT NOTE VERSION NUMBER IN V2 SETTINGS FILE");
		return version;
	}
}
