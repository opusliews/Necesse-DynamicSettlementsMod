package opusliews.journal;

import necesse.engine.journal.JournalEntry;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.StoryObjectiveRegistry;
import necesse.engine.storyObjectives.StoryObjective;
import necesse.level.maps.biomes.Biome;

public class GuideJournalEntry extends JournalEntry {
	private final GameMessage title;
	private final String unlockStoryObjectiveStringID;

	public GuideJournalEntry(Biome biome, GameMessage title, String unlockStoryObjectiveStringID) {
		super(biome);
		this.title = title;
		this.unlockStoryObjectiveStringID = unlockStoryObjectiveStringID;
		this.toggleIsHidden = false;
	}

	@Override
	public GameMessage getLocalization() {
		return title;
	}

	@Override
	public boolean canDiscoverWithLevelIdentifier(necesse.engine.util.LevelIdentifier levelIdentifier) {
		return false;
	}

	@Override
	public boolean isDiscovered(Client client) {
		return isUnlocked(client == null ? null : client.storyManager);
	}

	@Override
	public boolean isDiscovered(ServerClient client) {
		return isUnlocked(client == null ? null : client.storyManager);
	}

	private boolean isUnlocked(necesse.engine.storyObjectives.StoryObjectiveManager manager) {
		if (unlockStoryObjectiveStringID == null || unlockStoryObjectiveStringID.isEmpty()) return true;
		if (manager == null) return false;

		int objectiveID = StoryObjectiveRegistry.getObjectiveID(unlockStoryObjectiveStringID);
		if (objectiveID < 0) return false;
		StoryObjective objective = manager.getObjective(objectiveID);
		return objective != null && objective.isCompleted();
	}
}
