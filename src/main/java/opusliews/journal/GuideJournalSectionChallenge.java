package opusliews.journal;

import necesse.engine.journal.JournalChallenge;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormFlow;
import necesse.inventory.lootTable.LootTable;

public class GuideJournalSectionChallenge extends JournalChallenge {
	private final LootTable reward;

	public GuideJournalSectionChallenge(LootTable reward) {
		this.reward = reward;
	}

	@Override
	public void addJournalFormContent(Client client, FormContentBox contentBox, FormFlow flow) {
		// Guide journal entries are rendered by GuideJournalFormRenderer.
	}

	@Override
	public LootTable getReward() {
		return reward == null ? new LootTable() : reward;
	}
}
