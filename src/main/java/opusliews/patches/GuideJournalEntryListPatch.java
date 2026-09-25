package opusliews.patches;

import necesse.engine.journal.JournalEntry;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormMouseHover;
import necesse.gfx.forms.presets.containerComponent.journal.FormJournalBiomeEntryComponent;
import necesse.gfx.forms.presets.containerComponent.journal.JournalContainerForm;
import net.bytebuddy.asm.Advice;
import opusliews.journal.GuideJournalEntry;
import opusliews.journal.GuideJournalFormRenderer;
import opusliews.journal.GuideJournalRegistry;

@ModMethodPatch(
		target = FormJournalBiomeEntryComponent.class,
		name = "setupBiomeEntriesAndReturnCurrentHeight",
		arguments = {FormMouseHover.class, JournalEntry.class}
)
public class GuideJournalEntryListPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.FieldValue("journalEntry") JournalEntry journalEntry) {
		return GuideJournalRegistry.isGuideEntry(journalEntry);
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.FieldValue("journalEntry") JournalEntry journalEntry,
			@Advice.FieldValue("client") Client client,
			@Advice.FieldValue("entryContextBox") FormContentBox entryContextBox,
			@Advice.FieldValue("journalForm") JournalContainerForm journalForm,
			@Advice.This FormJournalBiomeEntryComponent component,
			@Advice.Return(readOnly = false) int result
	) {
		if (!GuideJournalRegistry.isGuideEntry(journalEntry)) return;
		result = GuideJournalFormRenderer.renderLeftEntry(
				(GuideJournalEntry)journalEntry,
				client,
				entryContextBox,
				journalForm,
				component.getX(),
				component.getY()
		);
	}
}
