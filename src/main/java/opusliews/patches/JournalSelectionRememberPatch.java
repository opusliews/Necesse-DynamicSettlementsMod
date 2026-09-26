package opusliews.patches;

import necesse.engine.journal.JournalEntry;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.presets.containerComponent.journal.FormJournalEntryComponent;
import necesse.inventory.lootTable.LootList;
import net.bytebuddy.asm.Advice;
import opusliews.journal.GuideJournalSelectionState;

@ModMethodPatch(
		target = FormJournalEntryComponent.class,
		name = "setupBiomeData",
		arguments = {JournalEntry.class, LootList.class, Client.class}
)
public class JournalSelectionRememberPatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.Argument(0) JournalEntry entry) {
		GuideJournalSelectionState.remember(entry);
	}
}
