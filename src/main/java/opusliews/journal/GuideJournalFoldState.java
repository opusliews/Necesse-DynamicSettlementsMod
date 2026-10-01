package opusliews.journal;

import necesse.engine.GlobalData;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class GuideJournalFoldState {
	private static final String stateDirectoryName = "dynamicsettlements";
	private static final String stateFileName = "journal-state.dat";
	private static final int fileMagic = 0x44534A46; // DSJF
	private static final int fileVersion = 1;
	private static final int maxStoredSections = 10000;
	private static final int maxSectionIDBytes = 4096;
	private static final Set<String> collapsedSections = new HashSet<>();
	private static boolean loaded;

	public static boolean isCollapsed(String sectionID) {
		ensureLoaded();
		return collapsedSections.contains(sectionID);
	}

	public static void toggle(String sectionID) {
		ensureLoaded();
		if (!collapsedSections.remove(sectionID)) {
			collapsedSections.add(sectionID);
		}
		save();
	}

	public static Set<String> getCollapsedSections() {
		ensureLoaded();
		return Collections.unmodifiableSet(collapsedSections);
	}

	private static void ensureLoaded() {
		if (loaded) return;
		loaded = true;
		collapsedSections.clear();

		File stateFile = getStateFile();
		if (stateFile.exists()) {
			loadStateFile(stateFile);
		}
	}

	private static void loadStateFile(File file) {
		try (DataInputStream input = new DataInputStream(new FileInputStream(file))) {
			if (input.readInt() != fileMagic) {
				throw new IOException("Invalid journal state file header");
			}

			int version = input.readInt();
			if (version != fileVersion) {
				throw new IOException("Unsupported journal state version: " + version);
			}

			int count = input.readInt();
			if (count < 0 || count > maxStoredSections) {
				throw new IOException("Invalid journal state section count: " + count);
			}

			for (int i = 0; i < count; i++) {
				int byteLength = input.readInt();
				if (byteLength < 0 || byteLength > maxSectionIDBytes) {
					throw new IOException("Invalid journal section ID length: " + byteLength);
				}

				byte[] bytes = new byte[byteLength];
				input.readFully(bytes);
				String sectionID = new String(bytes, StandardCharsets.UTF_8);
				if (!sectionID.isEmpty()) collapsedSections.add(sectionID);
			}
		} catch (EOFException e) {
			collapsedSections.clear();
			System.err.println("Dynamic Settlements: Journal fold-state file was truncated: " + e.getMessage());
		} catch (IOException e) {
			collapsedSections.clear();
			System.err.println("Dynamic Settlements: Could not load journal fold state: " + e.getMessage());
		}
	}

	private static void save() {
		File file = getStateFile();
		File parent = file.getParentFile();
		if (parent != null && !parent.exists() && !parent.mkdirs()) {
			System.err.println("Dynamic Settlements: Could not create journal state directory: " + parent);
			return;
		}

		try (DataOutputStream output = new DataOutputStream(new FileOutputStream(file))) {
			List<byte[]> storedSectionIDs = new ArrayList<>();
			for (String sectionID : collapsedSections) {
				byte[] bytes = sectionID.getBytes(StandardCharsets.UTF_8);
				if (bytes.length <= maxSectionIDBytes) storedSectionIDs.add(bytes);
			}

			output.writeInt(fileMagic);
			output.writeInt(fileVersion);
			output.writeInt(storedSectionIDs.size());

			for (byte[] bytes : storedSectionIDs) {
				output.writeInt(bytes.length);
				output.write(bytes);
			}
		} catch (IOException e) {
			System.err.println("Dynamic Settlements: Could not save journal fold state: " + e.getMessage());
		}
	}

	private static File getStateFile() {
		return new File(new File(new File(GlobalData.cfgPath(), "mods"), stateDirectoryName), stateFileName);
	}
}
