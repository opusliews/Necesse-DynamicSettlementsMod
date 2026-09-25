package opusliews.network;

import java.util.ArrayList;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.util.GameRandom;
import necesse.inventory.InventoryItem;
import opusliews.journal.GuideJournalRegistry;
import opusliews.journal.GuideJournalRegistry.SectionEntry;
import opusliews.story.GuideStoryObjectiveRegistry;

public class PacketCompleteJournalSection extends Packet {
	private final String challengeStringID;

	public PacketCompleteJournalSection(String challengeStringID) {
		this.challengeStringID = challengeStringID;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextString(challengeStringID);
	}

	public PacketCompleteJournalSection(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		challengeStringID = reader.getNextString();
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		if (!client.checkHasRequestedSelf() || client.isDead()) return;

		SectionEntry section = GuideJournalRegistry.getSectionByChallengeStringID(challengeStringID);
		if (section == null || section.challenge == null || section.buttonText == null) return;
		if (!section.challenge.isJournalEntryDiscovered(client)) return;
		if (!section.isVisible(client)) return;
		if (section.challenge.isCompleted(client)) return;
		if (!section.areItemRequirementsMet(client)) return;

		section.challenge.markCompleted(client);

		if (section.storyObjectiveToComplete != null && !section.storyObjectiveToComplete.isEmpty()) {
			GuideStoryObjectiveRegistry.complete(client, section.storyObjectiveToComplete);
		}

		if (section.reward != null && !section.reward.items.isEmpty()) {
			section.challenge.markClaimed(client);
			client.forceCombineNewStats();
			ArrayList rewards = section.reward.getNewList(GameRandom.globalRandom, 1.0F, client);
			for (Object value : rewards) {
				InventoryItem item = (InventoryItem)value;
				client.playerMob.getInv().addItemsDropRemaining(item, "reward", client.playerMob, true, true, true);
			}
		}
	}
}
