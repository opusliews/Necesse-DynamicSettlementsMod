package opusliews.commands;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import necesse.engine.commands.CommandLog;
import necesse.engine.commands.ModularChatCommand;
import necesse.engine.commands.PermissionLevel;
import necesse.engine.network.client.Client;
import necesse.engine.network.packet.PacketPlayerStats;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.playerStats.stats.ChallengesClaimedStat;
import necesse.engine.playerStats.stats.ChallengesCompletedStat;
import necesse.engine.registries.StoryObjectiveRegistry;
import necesse.engine.storyObjectives.StoryObjective;
import necesse.engine.storyObjectives.StoryObjectiveManager;
import opusliews.journal.GuideJournalRegistry;
import opusliews.story.GuideStoryObjective;

/**
 * Support-only, non-cheat rollback to the checkpoint immediately after guide21.
 * Only the executing character is affected. Not listed in help or autocomplete.
 */
public final class RepairCookingGuideCommand extends ModularChatCommand {
	public static final String commandName = "dsrepaircooking";

	private static final Field completedField;
	private static final Field claimedField;
	private static final Field completedChallengesField;
	private static final Field claimedChallengesField;

	static {
		try {
			completedField = StoryObjective.class.getDeclaredField("isCompleted");
			claimedField = StoryObjective.class.getDeclaredField("isClaimed");
			completedChallengesField = ChallengesCompletedStat.class.getDeclaredField("completedChallenges");
			claimedChallengesField = ChallengesClaimedStat.class.getDeclaredField("claimedChallenges");
			completedField.setAccessible(true);
			claimedField.setAccessible(true);
			completedChallengesField.setAccessible(true);
			claimedChallengesField.setAccessible(true);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Cannot initialize the cooking-guide recovery command", e);
		}
	}

	public RepairCookingGuideCommand() {
		super(commandName, "Restore the Dynamic Settlements cooking-guide checkpoint", PermissionLevel.USER, false);
	}

	@Override
	public boolean shouldBeListed() {
		return false;
	}

	@Override
	public void runModular(Client client, Server server, ServerClient serverClient, Object[] args, String[] errors, CommandLog logs) {
		if (serverClient == null || serverClient.storyManager == null || serverClient.characterStats() == null) {
			logs.add("This repair can only be run by a player with a loaded character.");
			return;
		}

		StoryObjectiveManager manager = serverClient.storyManager;
		int checkpointID = StoryObjectiveRegistry.getObjectiveID("guide21");
		if (checkpointID < 0) {
			logs.add("The cooking guide is not registered; no changes made.");
			return;
		}
		StoryObjective checkpoint = manager.getObjective(checkpointID);
		if (!(checkpoint instanceof GuideStoryObjective)) {
			logs.add("The cooking guide is unavailable; no changes made.");
			return;
		}
		int checkpointIndex = checkpoint.getSortedIndex();
		StoryObjective current = manager.getCurrentObjective();
		if (current != null && current.getSortedIndex() < checkpointIndex) {
			logs.add("This character has not reached the cooking guide yet; no earlier progress was skipped.");
			return;
		}

		// Prepare the complete rollback before changing any objective or journal state.
		List<StoryObjective> laterObjectives = new ArrayList<>();
		HashSet<String> laterObjectiveIDs = new HashSet<>();
		for (Object idValue : StoryObjectiveRegistry.getSortedStringIDs()) {
			String stringID = (String)idValue;
			int id = StoryObjectiveRegistry.getObjectiveID(stringID);
			if (id < 0) continue;
			StoryObjective objective = manager.getObjective(id);
			if (objective != null && objective.getSortedIndex() > checkpointIndex) {
				laterObjectives.add(objective);
				laterObjectiveIDs.add(stringID);
			}
		}

		// Sections revealed only after later objectives must also lose their
		// completed/claimed state. The guide22 journal remains revealed by guide21,
		// but must be unfinished so the player can resume the brick-firing guide.
		HashSet<String> journalChallengesToReset = new HashSet<>();
		for (GuideJournalRegistry.SectionEntry section : GuideJournalRegistry.getSections("dynamicsettlements")) {
			if (section.challenge == null) continue;
			if ("journalguide22".equals(section.sectionStringID)
					|| laterObjectiveIDs.contains(section.revealAfterObjectiveStringID)) {
				journalChallengesToReset.add(section.challengeStringID);
			}
		}

		try {
			@SuppressWarnings("unchecked")
			HashSet<String> completedChallenges = (HashSet<String>)completedChallengesField.get(serverClient.characterStats().challenges_completed);
			@SuppressWarnings("unchecked")
			HashSet<String> claimedChallenges = (HashSet<String>)claimedChallengesField.get(serverClient.characterStats().challenges_claimed);
			if (completedChallenges == null || claimedChallenges == null) {
				logs.add("Cannot access saved journal progress; no changes made.");
				return;
			}

			// Flush any queued stat completions before removing future journal state.
			serverClient.forceCombineNewStats();

			int resetObjectives = 0;
			for (StoryObjective objective : laterObjectives) {
				if (objective.isCompleted() || objective.isClaimed()) {
					completedField.setBoolean(objective, false);
					claimedField.setBoolean(objective, false);
					objective.markDirty();
					resetObjectives++;
				}
			}
			int resetJournal = 0;
			for (String challengeID : journalChallengesToReset) {
				boolean changed = completedChallenges.remove(challengeID);
				changed |= claimedChallenges.remove(challengeID);
				if (changed) resetJournal++;
			}

			// Complete this objective normally, including its automatic claim; that
			// unlocks journalguide22 without completing guide22 itself.
			((GuideStoryObjective)checkpoint).completeGuide();
			// Old/corrupt saves can hold a completed but unclaimed guide21;
			// because this guide has no rewards, claiming it advances safely.
			if (checkpoint.isCompleted() && !checkpoint.isClaimed()) {
				checkpoint.claimRewards(null);
			}
			manager.updateCurrentAndClaimableObjectives();
			manager.updateObjectiveListeners();

			// Dirty stat packets only add journal challenges, so an authoritative full
			// update is required for removals (and immediately refreshes story state).
			serverClient.sendPacket(new PacketPlayerStats(serverClient.characterStats(), manager, true));
			logs.add("Cooking guide restored: Guide 21 completed, Guide 22 active, brick-firing journal revealed. Reset "
					+ resetObjectives + " later objectives and " + resetJournal + " later journal completion states. Reopen the Journal and save normally.");
		} catch (ReflectiveOperationException | RuntimeException e) {
			logs.add("Cooking-guide repair failed: " + e.getMessage() + ". Back up the character before retrying.");
		}
	}
}
