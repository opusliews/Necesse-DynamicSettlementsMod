package opusliews.patches;

import java.util.ArrayList;

import necesse.engine.journal.JournalEntry;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.registries.JournalRegistry;
import net.bytebuddy.asm.Advice;
import opusliews.journal.GuideJournalRegistry;

@ModMethodPatch(
		target = JournalRegistry.class,
		name = "getJournalEntries",
		arguments = {}
)
public class GuideJournalOrderPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.Return(readOnly = false) Iterable result) {
		if (result == null || !isJournalEntryListBuildCall()) return;

		ArrayList<JournalEntry> guideEntries = new ArrayList<>();
		ArrayList<JournalEntry> otherEntries = new ArrayList<>();

		for (Object value : result) {
			if (!(value instanceof JournalEntry)) continue;

			JournalEntry entry = (JournalEntry)value;
			if (GuideJournalRegistry.isGuideEntry(entry)) {
				guideEntries.add(entry);
			} else {
				otherEntries.add(entry);
			}
		}

		if (guideEntries.isEmpty()) return;

		guideEntries.addAll(otherEntries);
		result = guideEntries;
	}

	public static boolean isJournalEntryListBuildCall() {
		for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
			if ("necesse.gfx.forms.presets.containerComponent.journal.JournalContainerForm".equals(frame.getClassName())
					&& "updateJournalEntries".equals(frame.getMethodName())) {
				return true;
			}
		}
		return false;
	}
}
