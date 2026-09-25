package opusliews.patches;

import necesse.engine.journal.JournalEntry;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.presets.containerComponent.journal.FormJournalEntryComponent;
import necesse.inventory.lootTable.LootList;
import net.bytebuddy.asm.Advice;
import opusliews.journal.GuideJournalEntry;
import opusliews.journal.GuideJournalFormRenderer;
import opusliews.journal.GuideJournalRegistry;

@ModMethodPatch(
		target = FormJournalEntryComponent.class,
		name = "setupBiomeData",
		arguments = {JournalEntry.class, LootList.class, Client.class}
)
public class GuideJournalEntryContentPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.Argument(0) JournalEntry entry,
			@Advice.Argument(2) Client client,
			@Advice.FieldValue("entryContextBox") FormContentBox entryContextBox
	) {
		if (!GuideJournalRegistry.isGuideEntry(entry)) return false;
		GuideJournalFormRenderer.renderEntry((GuideJournalEntry)entry, client, entryContextBox);
		return true;
	}
}
