package opusliews.patches;

import necesse.engine.modLoader.annotations.ModConstructorPatch;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.presets.containerComponent.journal.JournalContainerForm;
import necesse.inventory.container.AdventureJournalContainer;
import net.bytebuddy.asm.Advice;
import opusliews.journal.GuideJournalSelectionState;

@ModConstructorPatch(
		target = JournalContainerForm.class,
		arguments = {Client.class, AdventureJournalContainer.class}
)
public class JournalSelectionRestorePatch {
	@Advice.OnMethodEnter
	public static void onEnter() {
		String categoryStringID = GuideJournalSelectionState.getLastSelectedCategoryStringID();
		if (categoryStringID != null) JournalContainerForm.lastOpenBiomeEntry = categoryStringID;
	}
}
