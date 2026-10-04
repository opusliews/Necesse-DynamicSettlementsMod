package opusliews.journal;

import necesse.engine.journal.JournalChallenge;
import necesse.engine.network.client.Client;
import necesse.engine.network.packet.PacketCharacterStatsUpdate;
import necesse.engine.network.server.ServerClient;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormFlow;
import necesse.inventory.lootTable.LootTable;
import opusliews.logging.Logging;

public class GuideJournalSectionChallenge extends JournalChallenge {
	private final LootTable reward;

	public GuideJournalSectionChallenge(LootTable reward) {
		this.reward = reward;
	}

	@Override
	public void markCompleted(ServerClient serverClient) {
		boolean wasCompleted = isCompleted(serverClient);
		super.markCompleted(serverClient);

		if (!wasCompleted && isCompleted(serverClient)) {
			serverClient.sendPacket(new PacketCharacterStatsUpdate(serverClient.characterStats()));
			if (Logging.logEnabled) Logging.logMessage("[GuideJournal] Sent immediate completion stat sync challenge=" + getStringID() + " player=" + serverClient.getName());
		}
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
