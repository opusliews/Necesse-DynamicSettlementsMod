package opusliews.journal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import necesse.engine.journal.JournalEntry;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.registries.BiomeRegistry;
import necesse.engine.registries.JournalChallengeRegistry;
import necesse.engine.registries.JournalRegistry;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.StoryObjectiveRegistry;
import necesse.engine.storyObjectives.StoryObjective;
import necesse.inventory.lootTable.LootTable;

public class GuideJournalRegistry {
	private static final Map<String, CategoryEntry> categories = new LinkedHashMap<>();
	private static final Map<String, SectionEntry> sections = new LinkedHashMap<>();

	public static void registerCategory(String stringID, GameMessage title, String unlockStoryObjectiveStringID) {
		if (stringID == null || stringID.isEmpty()) throw new IllegalArgumentException("Journal category stringID cannot be empty");
		if (title == null) throw new IllegalArgumentException("Journal category title cannot be null: " + stringID);
		if (categories.containsKey(stringID) || JournalRegistry.getJournalEntry(stringID) != null) {
			throw new IllegalArgumentException("Journal category is already registered: " + stringID);
		}

		GuideJournalEntry journalEntry = new GuideJournalEntry(BiomeRegistry.FOREST, title, unlockStoryObjectiveStringID);
		JournalRegistry.registerJournalEntry(stringID, journalEntry);
		categories.put(stringID, new CategoryEntry(journalEntry));
	}

	public static void registerInfoSection(String categoryStringID, String sectionStringID, GameMessage title, GameMessage body) {
		registerInfoSection(categoryStringID, sectionStringID, title, new GameMessage[]{body});
	}

	public static void registerInfoSection(String categoryStringID, String sectionStringID, GameMessage title, GameMessage[] body) {
		registerSectionInternal(categoryStringID, sectionStringID, title, body, null, null, null);
	}

	public static void registerInfoSectionAfterObjective(String categoryStringID, String sectionStringID, GameMessage title, GameMessage body, String revealAfterObjectiveStringID) {
		registerInfoSectionAfterObjective(categoryStringID, sectionStringID, title, new GameMessage[]{body}, revealAfterObjectiveStringID);
	}

	public static void registerInfoSectionAfterObjective(String categoryStringID, String sectionStringID, GameMessage title, GameMessage[] body, String revealAfterObjectiveStringID) {
		registerSectionInternal(categoryStringID, sectionStringID, title, body, null, null, null, revealAfterObjectiveStringID, new String[0]);
	}

	public static void registerProgressSection(String categoryStringID, String sectionStringID, GameMessage title, GameMessage body, GuideJournalProgressObjective... progressObjectives) {
		registerProgressSection(categoryStringID, sectionStringID, title, new GameMessage[]{body}, progressObjectives);
	}

	public static void registerProgressSection(String categoryStringID, String sectionStringID, GameMessage title, GameMessage[] body, GuideJournalProgressObjective... progressObjectives) {
		registerSectionInternal(categoryStringID, sectionStringID, title, body, null, null, null, null, new String[0], validateProgressObjectives(sectionStringID, progressObjectives));
	}

	public static void registerProgressSectionAfterObjective(String categoryStringID, String sectionStringID, GameMessage title, GameMessage body, String revealAfterObjectiveStringID, GuideJournalProgressObjective... progressObjectives) {
		registerProgressSectionAfterObjective(categoryStringID, sectionStringID, title, new GameMessage[]{body}, revealAfterObjectiveStringID, progressObjectives);
	}

	public static void registerProgressSectionAfterObjective(String categoryStringID, String sectionStringID, GameMessage title, GameMessage[] body, String revealAfterObjectiveStringID, GuideJournalProgressObjective... progressObjectives) {
		registerSectionInternal(categoryStringID, sectionStringID, title, body, null, null, null, revealAfterObjectiveStringID, new String[0], validateProgressObjectives(sectionStringID, progressObjectives));
	}

	public static void registerButtonSection(String categoryStringID, String sectionStringID, GameMessage title, GameMessage body, GameMessage buttonText) {
		registerButtonSection(categoryStringID, sectionStringID, title, new GameMessage[]{body}, buttonText, null, null);
	}

	public static void registerButtonSection(String categoryStringID, String sectionStringID, GameMessage title, GameMessage[] body, GameMessage buttonText) {
		registerButtonSection(categoryStringID, sectionStringID, title, body, buttonText, null, null);
	}

	public static void registerButtonSection(String categoryStringID, String sectionStringID, GameMessage title, GameMessage body, GameMessage buttonText, LootTable reward, String storyObjectiveToComplete) {
		registerButtonSection(categoryStringID, sectionStringID, title, new GameMessage[]{body}, buttonText, reward, storyObjectiveToComplete);
	}

	public static void registerButtonSection(String categoryStringID, String sectionStringID, GameMessage title, GameMessage[] body, GameMessage buttonText, LootTable reward, String storyObjectiveToComplete) {
		registerButtonSection(categoryStringID, sectionStringID, title, body, buttonText, reward, storyObjectiveToComplete, new String[0]);
	}

	public static void registerButtonSection(String categoryStringID, String sectionStringID, GameMessage title, GameMessage body, GameMessage buttonText, LootTable reward, String storyObjectiveToComplete, String... requiredItemStringIDs) {
		registerButtonSection(categoryStringID, sectionStringID, title, new GameMessage[]{body}, buttonText, reward, storyObjectiveToComplete, requiredItemStringIDs);
	}

	public static void registerButtonSection(String categoryStringID, String sectionStringID, GameMessage title, GameMessage[] body, GameMessage buttonText, LootTable reward, String storyObjectiveToComplete, String... requiredItemStringIDs) {
		if (buttonText == null) throw new IllegalArgumentException("Journal section button text cannot be null: " + sectionStringID);
		String[] requirements = validateRequiredItems(sectionStringID, requiredItemStringIDs);
		registerSectionInternal(categoryStringID, sectionStringID, title, body, buttonText, reward, storyObjectiveToComplete, requirements);
	}

	public static void registerButtonSectionAfterObjective(String categoryStringID, String sectionStringID, GameMessage title, GameMessage body, GameMessage buttonText, LootTable reward, String storyObjectiveToComplete, String revealAfterObjectiveStringID, String... requiredItemStringIDs) {
		registerButtonSectionAfterObjective(categoryStringID, sectionStringID, title, new GameMessage[]{body}, buttonText, reward, storyObjectiveToComplete, revealAfterObjectiveStringID, requiredItemStringIDs);
	}

	public static void registerButtonSectionAfterObjective(String categoryStringID, String sectionStringID, GameMessage title, GameMessage[] body, GameMessage buttonText, LootTable reward, String storyObjectiveToComplete, String revealAfterObjectiveStringID, String... requiredItemStringIDs) {
		if (buttonText == null) throw new IllegalArgumentException("Journal section button text cannot be null: " + sectionStringID);
		String[] requirements = validateRequiredItems(sectionStringID, requiredItemStringIDs);
		registerSectionInternal(categoryStringID, sectionStringID, title, body, buttonText, reward, storyObjectiveToComplete, revealAfterObjectiveStringID, requirements);
	}

	private static void registerSectionInternal(String categoryStringID, String sectionStringID, GameMessage title, GameMessage[] body, GameMessage buttonText, LootTable reward, String storyObjectiveToComplete) {
		registerSectionInternal(categoryStringID, sectionStringID, title, body, buttonText, reward, storyObjectiveToComplete, null, new String[0]);
	}

	private static void registerSectionInternal(String categoryStringID, String sectionStringID, GameMessage title, GameMessage[] body, GameMessage buttonText, LootTable reward, String storyObjectiveToComplete, String[] requiredItemStringIDs) {
		registerSectionInternal(categoryStringID, sectionStringID, title, body, buttonText, reward, storyObjectiveToComplete, null, requiredItemStringIDs);
	}

	private static void registerSectionInternal(String categoryStringID, String sectionStringID, GameMessage title, GameMessage[] body, GameMessage buttonText, LootTable reward, String storyObjectiveToComplete, String revealAfterObjectiveStringID, String[] requiredItemStringIDs) {
		registerSectionInternal(categoryStringID, sectionStringID, title, body, buttonText, reward, storyObjectiveToComplete, revealAfterObjectiveStringID, requiredItemStringIDs, new GuideJournalProgressObjective[0]);
	}

	private static void registerSectionInternal(String categoryStringID, String sectionStringID, GameMessage title, GameMessage[] body, GameMessage buttonText, LootTable reward, String storyObjectiveToComplete, String revealAfterObjectiveStringID, String[] requiredItemStringIDs, GuideJournalProgressObjective[] progressObjectives) {
		CategoryEntry category = categories.get(categoryStringID);
		if (category == null) throw new IllegalArgumentException("Unknown journal category: " + categoryStringID);
		if (sectionStringID == null || sectionStringID.isEmpty()) throw new IllegalArgumentException("Journal section stringID cannot be empty");
		if (title == null) throw new IllegalArgumentException("Journal section title cannot be null: " + sectionStringID);
		if (body == null || body.length == 0) throw new IllegalArgumentException("Journal section must have at least one body paragraph: " + sectionStringID);
		for (GameMessage paragraph : body) {
			if (paragraph == null) throw new IllegalArgumentException("Journal section paragraph cannot be null: " + sectionStringID);
		}
		if (revealAfterObjectiveStringID != null && !revealAfterObjectiveStringID.isEmpty() && !StoryObjectiveRegistry.objectiveExists(revealAfterObjectiveStringID)) {
			throw new IllegalArgumentException("Unknown journal section reveal objective " + revealAfterObjectiveStringID + " for section " + sectionStringID);
		}

		String challengeStringID = "dsjournal_" + categoryStringID + "_" + sectionStringID;
		if (sections.containsKey(challengeStringID) || JournalChallengeRegistry.doesChallengeExists(challengeStringID)) {
			throw new IllegalArgumentException("Journal section is already registered: " + categoryStringID + "/" + sectionStringID);
		}

		GuideJournalSectionChallenge challenge = null;
		if (buttonText != null) {
			challenge = new GuideJournalSectionChallenge(reward);
			challenge.setCustomName(title);
			JournalChallengeRegistry.registerChallenge(challengeStringID, challenge);
			category.journalEntry.addEntryChallenges(challengeStringID);
		}

		SectionEntry section = new SectionEntry(categoryStringID, sectionStringID, challengeStringID, title, body, buttonText, reward, storyObjectiveToComplete, revealAfterObjectiveStringID, challenge, requiredItemStringIDs, progressObjectives);
		category.sections.add(section);
		sections.put(challengeStringID, section);
	}

	public static boolean isGuideEntry(JournalEntry entry) {
		return entry != null && categories.containsKey(entry.getStringID());
	}

	public static List<SectionEntry> getSections(String categoryStringID) {
		CategoryEntry entry = categories.get(categoryStringID);
		return entry == null ? Collections.emptyList() : Collections.unmodifiableList(entry.sections);
	}

	public static SectionEntry getSectionByChallengeStringID(String challengeStringID) {
		return sections.get(challengeStringID);
	}

	public static int getCompletableSectionCount(String categoryStringID) {
		int count = 0;
		for (SectionEntry section : getSections(categoryStringID)) {
			if (section.challenge != null) count++;
		}
		return count;
	}

	public static int getCompletableSectionCount(String categoryStringID, necesse.engine.network.client.Client client) {
		int count = 0;
		for (SectionEntry section : getSections(categoryStringID)) {
			if (section.challenge != null && section.isVisible(client)) count++;
		}
		return count;
	}

	public static int getCompletedSectionCount(String categoryStringID, necesse.engine.network.client.Client client) {
		int count = 0;
		for (SectionEntry section : getSections(categoryStringID)) {
			if (section.challenge != null && section.isVisible(client) && section.challenge.isCompleted(client)) count++;
		}
		return count;
	}

	private static GuideJournalProgressObjective[] validateProgressObjectives(String sectionStringID, GuideJournalProgressObjective[] progressObjectives) {
		GuideJournalProgressObjective[] objectives = progressObjectives == null ? new GuideJournalProgressObjective[0] : progressObjectives.clone();
		for (GuideJournalProgressObjective objective : objectives) {
			if (objective == null) throw new IllegalArgumentException("Journal progress objective cannot be null: " + sectionStringID);
		}
		return objectives;
	}

	private static String[] validateRequiredItems(String sectionStringID, String[] requiredItemStringIDs) {
		String[] requirements = requiredItemStringIDs == null ? new String[0] : requiredItemStringIDs.clone();
		for (String itemStringID : requirements) {
			if (itemStringID == null || itemStringID.isEmpty()) {
				throw new IllegalArgumentException("Journal section required item stringID cannot be empty: " + sectionStringID);
			}
			if (!ItemRegistry.itemExists(itemStringID)) {
				throw new IllegalArgumentException("Unknown required journal item " + itemStringID + " for section " + sectionStringID);
			}
		}
		return requirements;
	}

	public static class CategoryEntry {
		public final GuideJournalEntry journalEntry;
		public final ArrayList<SectionEntry> sections = new ArrayList<>();

		CategoryEntry(GuideJournalEntry journalEntry) {
			this.journalEntry = journalEntry;
		}
	}

	public static class SectionEntry {
		public final String categoryStringID;
		public final String sectionStringID;
		public final String challengeStringID;
		public final GameMessage title;
		public final GameMessage[] body;
		public final GameMessage buttonText;
		public final LootTable reward;
		public final String storyObjectiveToComplete;
		public final String revealAfterObjectiveStringID;
		public final GuideJournalSectionChallenge challenge;
		public final String[] requiredItemStringIDs;
		public final GuideJournalProgressObjective[] progressObjectives;

		SectionEntry(String categoryStringID, String sectionStringID, String challengeStringID, GameMessage title, GameMessage[] body, GameMessage buttonText, LootTable reward, String storyObjectiveToComplete, String revealAfterObjectiveStringID, GuideJournalSectionChallenge challenge, String[] requiredItemStringIDs, GuideJournalProgressObjective[] progressObjectives) {
			this.categoryStringID = categoryStringID;
			this.sectionStringID = sectionStringID;
			this.challengeStringID = challengeStringID;
			this.title = title;
			this.body = body.clone();
			this.buttonText = buttonText;
			this.reward = reward;
			this.storyObjectiveToComplete = storyObjectiveToComplete;
			this.revealAfterObjectiveStringID = revealAfterObjectiveStringID;
			this.challenge = challenge;
			this.requiredItemStringIDs = requiredItemStringIDs == null ? new String[0] : requiredItemStringIDs.clone();
			this.progressObjectives = progressObjectives == null ? new GuideJournalProgressObjective[0] : progressObjectives.clone();
		}

		public boolean isVisible(necesse.engine.network.client.Client client) {
			return isRevealObjectiveCompleted(client == null ? null : client.storyManager);
		}

		public boolean isVisible(necesse.engine.network.server.ServerClient client) {
			return isRevealObjectiveCompleted(client == null ? null : client.storyManager);
		}

		private boolean isRevealObjectiveCompleted(necesse.engine.storyObjectives.StoryObjectiveManager manager) {
			if (revealAfterObjectiveStringID == null || revealAfterObjectiveStringID.isEmpty()) return true;
			if (manager == null) return false;

			int objectiveID = StoryObjectiveRegistry.getObjectiveID(revealAfterObjectiveStringID);
			if (objectiveID < 0) return false;
			StoryObjective objective = manager.getObjective(objectiveID);
			return objective != null && objective.isCompleted();
		}

		public boolean hasItemRequirements() {
			return requiredItemStringIDs.length > 0;
		}

		public boolean areItemRequirementsMet(necesse.engine.network.client.Client client) {
			for (String itemStringID : requiredItemStringIDs) {
				if (!client.characterStats.items_obtained.isItemObtained(itemStringID)) return false;
			}
			return true;
		}

		public boolean areItemRequirementsMet(necesse.engine.network.server.ServerClient client) {
			for (String itemStringID : requiredItemStringIDs) {
				if (!client.characterStats().items_obtained.isItemObtained(itemStringID)) return false;
			}
			return true;
		}

		public boolean hasCompletionState() {
			return challenge != null || progressObjectives.length > 0;
		}

		public boolean isCompleted(necesse.engine.network.client.Client client) {
			if (challenge != null) return challenge.isCompleted(client);
			if (progressObjectives.length == 0) return false;

			for (GuideJournalProgressObjective objective : progressObjectives) {
				if (!objective.isCompleted(client)) return false;
			}
			return true;
		}
	}
}
