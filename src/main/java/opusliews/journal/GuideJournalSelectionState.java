package opusliews.journal;

import necesse.engine.journal.JournalEntry;

public final class GuideJournalSelectionState {
	private static String lastSelectedCategoryStringID;

	private GuideJournalSelectionState() {
	}

	public static void remember(JournalEntry entry) {
		if (entry != null) lastSelectedCategoryStringID = entry.getStringID();
	}

	public static String getLastSelectedCategoryStringID() {
		return lastSelectedCategoryStringID;
	}
}
