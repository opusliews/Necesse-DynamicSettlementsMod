package opusliews.journal;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import necesse.engine.GlobalData;

public class GuideJournalFoldState {
	private static final String fileName = "dynamicsettlements-journal-folds.cfg";
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

		File file = getFile();
		if (!file.exists()) return;

		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String sectionID = line.trim();
				if (!sectionID.isEmpty() && !sectionID.startsWith("#")) {
					collapsedSections.add(sectionID);
				}
			}
		} catch (IOException e) {
			System.err.println("Dynamic Settlements: Could not load journal fold state: " + e.getMessage());
		}
	}

	private static void save() {
		File file = getFile();
		File parent = file.getParentFile();
		if (parent != null && !parent.exists() && !parent.mkdirs()) {
			System.err.println("Dynamic Settlements: Could not create journal fold-state directory: " + parent);
			return;
		}

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
			writer.write("# Dynamic Settlements journal collapsed sections");
			writer.newLine();
			for (String sectionID : collapsedSections) {
				writer.write(sectionID);
				writer.newLine();
			}
		} catch (IOException e) {
			System.err.println("Dynamic Settlements: Could not save journal fold state: " + e.getMessage());
		}
	}

	private static File getFile() {
		return new File(new File(GlobalData.cfgPath(), "mods"), fileName);
	}
}
