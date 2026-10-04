package opusliews.journal;

import necesse.engine.journal.JournalChallenge;
import necesse.engine.journal.JournalEntry;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.*;
import necesse.engine.storyObjectives.StoryObjective;
import necesse.inventory.lootTable.LootTable;

import java.util.*;

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
		registerSectionInternal(categoryStringID, sectionStringID, title, body, new LocalMessage("journalguide", "completedbutton"), null, null);
	}

	public static void registerInfoSectionAfterObjective(String categoryStringID, String sectionStringID, GameMessage title, GameMessage body, String revealAfterObjectiveStringID) {
		registerInfoSectionAfterObjective(categoryStringID, sectionStringID, title, new GameMessage[]{body}, revealAfterObjectiveStringID);
	}

	public static void registerInfoSectionAfterObjective(String categoryStringID, String sectionStringID, GameMessage title, GameMessage[] body, String revealAfterObjectiveStringID) {
		registerSectionInternal(categoryStringID, sectionStringID, title, body, new LocalMessage("journalguide", "completedbutton"), null, null, revealAfterObjectiveStringID, new String[0]);
	}

	public static void registerDiscoveryChallenge(String challengeStringID) {
		if (challengeStringID == null || challengeStringID.isEmpty()) throw new IllegalArgumentException("Discovery challenge stringID cannot be empty");
		if (JournalChallengeRegistry.doesChallengeExists(challengeStringID)) return;
		JournalChallengeRegistry.registerChallenge(challengeStringID, new GuideJournalSectionChallenge(null));
	}

	public static void registerInfoSectionAfterChallenge(String categoryStringID, String sectionStringID, GameMessage title, GameMessage body, String revealAfterChallengeStringID) {
		registerInfoSectionAfterChallenge(categoryStringID, sectionStringID, title, new GameMessage[]{body}, revealAfterChallengeStringID);
	}

	public static void registerInfoSectionAfterChallenge(String categoryStringID, String sectionStringID, GameMessage title, GameMessage[] body, String revealAfterChallengeStringID) {
		if (revealAfterChallengeStringID == null || revealAfterChallengeStringID.isEmpty() || !JournalChallengeRegistry.doesChallengeExists(revealAfterChallengeStringID)) {
			throw new IllegalArgumentException("Unknown journal section reveal challenge " + revealAfterChallengeStringID + " for section " + sectionStringID);
		}
		registerSectionInternal(categoryStringID, sectionStringID, title, body, new LocalMessage("journalguide", "completedbutton"), null, null, "challenge:" + revealAfterChallengeStringID, new String[0]);
	}

	public static boolean isDiscoveryChallengeCompleted(ServerClient client, String challengeStringID) {
		if (client == null) return false;
		JournalChallenge challenge = JournalChallengeRegistry.getChallenge(challengeStringID);
		return challenge != null && challenge.isCompleted(client);
	}

	public static void completeDiscoveryChallenge(ServerClient client, String challengeStringID) {
		if (client == null) return;
		JournalChallenge challenge = JournalChallengeRegistry.getChallenge(challengeStringID);
		if (challenge != null && !challenge.isCompleted(client)) challenge.markCompleted(client);
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


	public static void registerButtonSectionAfterChallenge(
			String categoryStringID,
			String sectionStringID,
			GameMessage title,
			GameMessage body,
			GameMessage buttonText,
			String revealAfterChallengeStringID
	) {
		registerButtonSectionAfterChallenge(categoryStringID, sectionStringID, title, new GameMessage[]{body}, buttonText, revealAfterChallengeStringID);
	}

	public static void registerButtonSectionAfterChallenge(
			String categoryStringID,
			String sectionStringID,
			GameMessage title,
			GameMessage[] body,
			GameMessage buttonText,
			String revealAfterChallengeStringID
	) {
		if (buttonText == null) throw new IllegalArgumentException("Journal section button text cannot be null: " + sectionStringID);
		validateRevealChallenge(sectionStringID, revealAfterChallengeStringID);
		registerSectionInternal(categoryStringID, sectionStringID, title, body, buttonText, null, null,
				"challenge:" + revealAfterChallengeStringID, new String[0]);
	}

	public static void registerAutoSectionAfterChallenge(
			String categoryStringID,
			String sectionStringID,
			GameMessage title,
			GameMessage body,
			String revealAfterChallengeStringID
	) {
		registerAutoSectionAfterChallenge(categoryStringID, sectionStringID, title, new GameMessage[]{body}, revealAfterChallengeStringID);
	}

	public static void registerAutoSectionAfterChallenge(
			String categoryStringID,
			String sectionStringID,
			GameMessage title,
			GameMessage[] body,
			String revealAfterChallengeStringID
	) {
		validateRevealChallenge(sectionStringID, revealAfterChallengeStringID);
		registerSectionInternal(categoryStringID, sectionStringID, title, body, null, null, null,
				"challenge:" + revealAfterChallengeStringID, new String[0], new GuideJournalProgressObjective[0], true);
	}

	private static void validateRevealChallenge(String sectionStringID, String revealAfterChallengeStringID) {
		if (revealAfterChallengeStringID == null
				|| revealAfterChallengeStringID.isEmpty()
				|| !JournalChallengeRegistry.doesChallengeExists(revealAfterChallengeStringID)) {
			throw new IllegalArgumentException("Unknown journal section reveal challenge " + revealAfterChallengeStringID + " for section " + sectionStringID);
		}
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

	public static void registerAnyItemProgressSectionAfterChallenge(
			String categoryStringID,
			String sectionStringID,
			GameMessage title,
			GameMessage body,
			GameMessage anyRequiredItemsText,
			String revealAfterChallengeStringID,
			String... requiredItemStringIDs
	) {
		registerAnyItemProgressSectionAfterChallenge(
				categoryStringID,
				sectionStringID,
				title,
				new GameMessage[]{body},
				anyRequiredItemsText,
				revealAfterChallengeStringID,
				requiredItemStringIDs
		);
	}

	public static void registerAnyItemProgressSectionAfterChallenge(
			String categoryStringID,
			String sectionStringID,
			GameMessage title,
			GameMessage[] body,
			GameMessage anyRequiredItemsText,
			String revealAfterChallengeStringID,
			String... requiredItemStringIDs
	) {
		if (revealAfterChallengeStringID == null
				|| revealAfterChallengeStringID.isEmpty()
				|| !JournalChallengeRegistry.doesChallengeExists(revealAfterChallengeStringID)) {
			throw new IllegalArgumentException(
					"Unknown journal section reveal challenge "
							+ revealAfterChallengeStringID
							+ " for section "
							+ sectionStringID
			);
		}

		String[] requirements = validateRequiredItems(sectionStringID, requiredItemStringIDs);

		registerSectionInternal(
				categoryStringID,
				sectionStringID,
				title,
				body,
				null,
				null,
				null,
				"challenge:" + revealAfterChallengeStringID,
				new String[0],
				requirements,
				anyRequiredItemsText,
				new GuideJournalProgressObjective[0],
				false
		);
	}

	public static void registerProgressSectionAfterChallenge(
			String categoryStringID,
			String sectionStringID,
			GameMessage title,
			GameMessage[] body,
			String revealAfterChallengeStringID,
			GuideJournalProgressObjective... progressObjectives
	) {
		if (revealAfterChallengeStringID == null
				|| revealAfterChallengeStringID.isEmpty()
				|| !JournalChallengeRegistry.doesChallengeExists(revealAfterChallengeStringID)) {
			throw new IllegalArgumentException(
					"Unknown journal section reveal challenge "
							+ revealAfterChallengeStringID
							+ " for section "
							+ sectionStringID
			);
		}

		registerSectionInternal(
				categoryStringID,
				sectionStringID,
				title,
				body,
				null,
				null,
				null,
				"challenge:" + revealAfterChallengeStringID,
				new String[0],
				validateProgressObjectives(sectionStringID, progressObjectives)
		);
	}

	private static void registerSectionInternal(
			String categoryStringID,
			String sectionStringID,
			GameMessage title,
			GameMessage[] body,
			GameMessage buttonText,
			LootTable reward,
			String storyObjectiveToComplete,
			String revealAfterObjectiveStringID,
			String[] requiredItemStringIDs,
			GuideJournalProgressObjective[] progressObjectives
	) {
		registerSectionInternal(categoryStringID, sectionStringID, title, body, buttonText, reward,
				storyObjectiveToComplete, revealAfterObjectiveStringID, requiredItemStringIDs,
				new String[0], null, progressObjectives, false);
	}

	private static void registerSectionInternal(
			String categoryStringID,
			String sectionStringID,
			GameMessage title,
			GameMessage[] body,
			GameMessage buttonText,
			LootTable reward,
			String storyObjectiveToComplete,
			String revealAfterObjectiveStringID,
			String[] requiredItemStringIDs,
			GuideJournalProgressObjective[] progressObjectives,
			boolean forceChallenge
	) {
		registerSectionInternal(categoryStringID, sectionStringID, title, body, buttonText, reward,
				storyObjectiveToComplete, revealAfterObjectiveStringID, requiredItemStringIDs,
				new String[0], null, progressObjectives, forceChallenge);
	}

	private static void registerSectionInternal(
			String categoryStringID,
			String sectionStringID,
			GameMessage title,
			GameMessage[] body,
			GameMessage buttonText,
			LootTable reward,
			String storyObjectiveToComplete,
			String revealAfterObjectiveStringID,
			String[] requiredItemStringIDs,
			String[] anyRequiredItemStringIDs,
			GameMessage anyRequiredItemsText,
			GuideJournalProgressObjective[] progressObjectives,
			boolean forceChallenge
	) {
		CategoryEntry category = categories.get(categoryStringID);
		if (category == null) throw new IllegalArgumentException("Unknown journal category: " + categoryStringID);
		if (sectionStringID == null || sectionStringID.isEmpty()) throw new IllegalArgumentException("Journal section stringID cannot be empty");
		if (title == null) throw new IllegalArgumentException("Journal section title cannot be null: " + sectionStringID);
		if (body == null || body.length == 0) throw new IllegalArgumentException("Journal section must have at least one body paragraph: " + sectionStringID);

		for (GameMessage paragraph : body) {
			if (paragraph == null) throw new IllegalArgumentException("Journal section paragraph cannot be null: " + sectionStringID);
		}

		if (revealAfterObjectiveStringID != null && !revealAfterObjectiveStringID.isEmpty()) {
			if (revealAfterObjectiveStringID.startsWith("challenge:")) {
				String challengeStringID = revealAfterObjectiveStringID.substring("challenge:".length());

				if (!JournalChallengeRegistry.doesChallengeExists(challengeStringID)) {
					throw new IllegalArgumentException(
							"Unknown journal section reveal challenge "
									+ challengeStringID
									+ " for section "
									+ sectionStringID
					);
				}
			}
			else if (!StoryObjectiveRegistry.objectiveExists(revealAfterObjectiveStringID)) {
				throw new IllegalArgumentException(
						"Unknown journal section reveal objective "
								+ revealAfterObjectiveStringID
								+ " for section "
								+ sectionStringID
				);
			}
		}

		String challengeStringID = "dsjournal_" + categoryStringID + "_" + sectionStringID;

		if (sections.containsKey(challengeStringID) || JournalChallengeRegistry.doesChallengeExists(challengeStringID)) {
			throw new IllegalArgumentException(
					"Journal section is already registered: "
							+ categoryStringID
							+ "/"
							+ sectionStringID
			);
		}

		GuideJournalSectionChallenge challenge = null;

		if (buttonText != null || forceChallenge) {
			challenge = new GuideJournalSectionChallenge(reward);
			challenge.setCustomName(title);
			JournalChallengeRegistry.registerChallenge(challengeStringID, challenge);
			category.journalEntry.addEntryChallenges(challengeStringID);
		}

		SectionEntry section = new SectionEntry(
				categoryStringID,
				sectionStringID,
				challengeStringID,
				title,
				body,
				buttonText,
				reward,
				storyObjectiveToComplete,
				revealAfterObjectiveStringID,
				challenge,
				requiredItemStringIDs,
				anyRequiredItemStringIDs,
				anyRequiredItemsText,
				progressObjectives
		);

		category.sections.add(section);
		sections.put(challengeStringID, section);
	}

	public static boolean completeSection(ServerClient client, String sectionStringID) {
		if (client == null || sectionStringID == null) return false;
		SectionEntry target = null;
		for (SectionEntry section : sections.values()) {
			if (section.sectionStringID.equals(sectionStringID)) {
				target = section;
				break;
			}
		}
		if (target == null || target.challenge == null || !target.isVisible(client)) return false;
		if (target.challenge.isCompleted(client)) return true;
		target.challenge.markCompleted(client);
		return true;
	}


	public static boolean isSectionCompleted(ServerClient client, String sectionStringID) {
		if (client == null || sectionStringID == null) return false;
		for (SectionEntry section : sections.values()) {
			if (!section.sectionStringID.equals(sectionStringID)) continue;
			return section.challenge != null && section.challenge.isCompleted(client);
		}
		return false;
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
		public final String[] anyRequiredItemStringIDs;
		public final GameMessage anyRequiredItemsText;

		SectionEntry(
				String categoryStringID,
				String sectionStringID,
				String challengeStringID,
				GameMessage title,
				GameMessage[] body,
				GameMessage buttonText,
				LootTable reward,
				String storyObjectiveToComplete,
				String revealAfterObjectiveStringID,
				GuideJournalSectionChallenge challenge,
				String[] requiredItemStringIDs,
				String[] anyRequiredItemStringIDs,
				GameMessage anyRequiredItemsText,
				GuideJournalProgressObjective[] progressObjectives
		) {
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
			this.anyRequiredItemStringIDs = anyRequiredItemStringIDs == null ? new String[0] : anyRequiredItemStringIDs.clone();
			this.anyRequiredItemsText = anyRequiredItemsText;
			this.progressObjectives = progressObjectives == null ? new GuideJournalProgressObjective[0] : progressObjectives.clone();
		}

		public boolean hasAnyItemRequirements() {
			return anyRequiredItemStringIDs.length > 0;
		}

		public boolean areAnyItemRequirementsMet(necesse.engine.network.client.Client client) {
			for (String itemStringID : anyRequiredItemStringIDs) {
				if (client.characterStats.items_obtained.isItemObtained(itemStringID)) return true;
			}
			return false;
		}

		public boolean areAnyItemRequirementsMet(necesse.engine.network.server.ServerClient client) {
			for (String itemStringID : anyRequiredItemStringIDs) {
				if (client.characterStats().items_obtained.isItemObtained(itemStringID)) return true;
			}
			return false;
		}

		public boolean isVisible(necesse.engine.network.client.Client client) {
			if (revealAfterObjectiveStringID != null && revealAfterObjectiveStringID.startsWith("challenge:")) {
				if (client == null) return false;
				JournalChallenge challenge = JournalChallengeRegistry.getChallenge(revealAfterObjectiveStringID.substring("challenge:".length()));
				return challenge != null && challenge.isCompleted(client);
			}
			return isRevealObjectiveCompleted(client == null ? null : client.storyManager);
		}

		public boolean isVisible(necesse.engine.network.server.ServerClient client) {
			if (revealAfterObjectiveStringID != null && revealAfterObjectiveStringID.startsWith("challenge:")) {
				if (client == null) return false;
				JournalChallenge challenge = JournalChallengeRegistry.getChallenge(revealAfterObjectiveStringID.substring("challenge:".length()));
				return challenge != null && challenge.isCompleted(client);
			}
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
			return challenge != null || progressObjectives.length > 0 || anyRequiredItemStringIDs.length > 0;
		}

		public boolean isCompleted(necesse.engine.network.client.Client client) {
			if (challenge != null) return challenge.isCompleted(client);

			if (anyRequiredItemStringIDs.length > 0) {
				return areAnyItemRequirementsMet(client);
			}

			if (progressObjectives.length == 0) return false;

			for (GuideJournalProgressObjective objective : progressObjectives) {
				if (!objective.isCompleted(client)) return false;
			}

			return true;
		}
	}
}
