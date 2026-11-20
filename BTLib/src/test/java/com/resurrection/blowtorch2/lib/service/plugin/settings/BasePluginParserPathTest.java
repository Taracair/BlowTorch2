package com.resurrection.blowtorch2.lib.service.plugin.settings;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

/**
 * Pick-file import copies the XML into app cache and passes that absolute path.
 * A bare profile name stays a file inside the app files directory.
 */
public class BasePluginParserPathTest {

	@Test
	public void absoluteCachePathReadsTheFile() throws Exception {
		File dir = new File(System.getProperty("java.io.tmpdir"),
				"bt-import-" + System.nanoTime());
		assertTrueMkdir(dir);
		File xml = new File(dir, "import_settings_saf.xml");
		byte[] body = "<blowtorch xmlversion=\"2\"/>".getBytes(StandardCharsets.UTF_8);
		FileOutputStream out = new FileOutputStream(xml);
		try {
			out.write(body);
		} finally {
			out.close();
		}
		try {
			BasePluginParser parser = new BasePluginParser(xml.getAbsolutePath(), null);
			assertArrayEquals(body, parser.snapshotDocumentBytes());
		} finally {
			xml.delete();
			dir.delete();
		}
	}

	@Test
	public void bareProfileNameIsNotReadFromTheWorkingDirectory() throws Exception {
		File decoy = new File("bt-settings-import-decoy.xml");
		byte[] body = "<blowtorch xmlversion=\"2\"/>".getBytes(StandardCharsets.UTF_8);
		FileOutputStream out = new FileOutputStream(decoy);
		try {
			out.write(body);
		} finally {
			out.close();
		}
		try {
			BasePluginParser parser = new BasePluginParser(decoy.getName(), null);
			try {
				byte[] got = parser.snapshotDocumentBytes();
				fail("bare profile name was opened as a filesystem path (" + got.length + " bytes)");
			} catch (Exception expected) {
				// openFileInput, not a FileInputStream of the working directory.
			}
		} finally {
			decoy.delete();
		}
	}

	private static void assertTrueMkdir(File dir) {
		if (!dir.mkdir()) {
			throw new IllegalStateException("could not create " + dir.getAbsolutePath());
		}
	}
}
