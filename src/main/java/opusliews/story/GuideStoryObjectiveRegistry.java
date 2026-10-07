package opusliews.story;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.StoryObjectiveRegistry;
import necesse.engine.storyObjectives.StoryObjective;
import necesse.inventory.lootTable.LootTable;
import opusliews.logging.Logging;

public class GuideStoryObjectiveRegistry {
	public static final String firstVanillaObjectiveStringID = "gettingstarted";
	private static final Map<String, Entry> entries = new LinkedHashMap<>();
	private static final HashSet<String> removedObjectiveStringIDs = new HashSet<>();

	public static void registerBeforeFirstVanilla(String stringID, GameMessage title, GameMessage objective, CompletionCondition completionCondition) {
		registerBefore(stringID, firstVanillaObjectiveStringID, title, new GameMessage[]{objective}, completionCondition, null, true);
	}

	public static void registerBeforeFirstVanilla(String stringID, GameMessage title, GameMessage[] objectives, CompletionCondition completionCondition) {
		registerBefore(stringID, firstVanillaObjectiveStringID, title, objectives, completionCondition, null, true);
	}

	public static void registerBeforeFirstVanilla(String stringID, GameMessage title, GameMessage objective, CompletionCondition completionCondition, LootTable rewards) {
		registerBefore(stringID, firstVanillaObjectiveStringID, title, new GameMessage[]{objective}, completionCondition, rewards, true);
	}

	public static void registerBeforeFirstVanilla(String stringID, GameMessage title, GameMessage[] objectives, CompletionCondition completionCondition, LootTable rewards) {
		registerBefore(stringID, firstVanillaObjectiveStringID, title, objectives, completionCondition, rewards, true);
	}

	public static void registerButtonBeforeFirstVanilla(String stringID, GameMessage title, GameMessage objective, GameMessage buttonText) {
		registerButtonBefore(stringID, firstVanillaObjectiveStringID, title, new GameMessage[]{objective}, buttonText);
	}

	public static void registerButtonBeforeFirstVanilla(String stringID, GameMessage title, GameMessage[] objectives, GameMessage buttonText) {
		registerButtonBefore(stringID, firstVanillaObjectiveStringID, title, objectives, buttonText);
	}

	public static void registerBefore(String stringID, String beforeObjectiveStringID, GameMessage title, GameMessage objective, CompletionCondition completionCondition) {
		registerBefore(stringID, beforeObjectiveStringID, title, new GameMessage[]{objective}, completionCondition, null, true);
	}

	public static void registerBefore(String stringID, String beforeObjectiveStringID, GameMessage title, GameMessage objective, CompletionCondition completionCondition, LootTable rewards) {
		registerBefore(stringID, beforeObjectiveStringID, title, new GameMessage[]{objective}, completionCondition, rewards, true);
	}

	public static void registerBefore(String stringID, String beforeObjectiveStringID, GameMessage title, GameMessage[] objectives, CompletionCondition completionCondition, LootTable rewards, boolean autoClaimWithoutRewards) {
		validateRegistration(stringID, beforeObjectiveStringID, title, objectives);
		Entry entry = new Entry(title, objectives, completionCondition, rewards, autoClaimWithoutRewards, beforeObjectiveStringID, true, null);
		entries.put(stringID, entry);
		StoryObjectiveRegistry.registerObjective(stringID, GuideStoryObjective.class, false).showBeforeObjective(beforeObjectiveStringID);
	}

	public static void registerButtonBefore(String stringID, String beforeObjectiveStringID, GameMessage title, GameMessage objective, GameMessage buttonText) {
		registerButtonBefore(stringID, beforeObjectiveStringID, title, new GameMessage[]{objective}, buttonText);
	}

	public static void registerButtonBefore(String stringID, String beforeObjectiveStringID, GameMessage title, GameMessage[] objectives, GameMessage buttonText) {
		validateRegistration(stringID, beforeObjectiveStringID, title, objectives);
		validateButtonText(stringID, buttonText);
		Entry entry = new Entry(title, objectives, null, null, true, beforeObjectiveStringID, true, buttonText);
		entries.put(stringID, entry);
		StoryObjectiveRegistry.registerObjective(stringID, GuideStoryObjective.class, false).showBeforeObjective(beforeObjectiveStringID);
	}

	public static void registerAfter(String stringID, String afterObjectiveStringID, GameMessage title, GameMessage objective, CompletionCondition completionCondition) {
		registerAfter(stringID, afterObjectiveStringID, title, new GameMessage[]{objective}, completionCondition, null, true);
	}

	public static void registerAfter(String stringID, String afterObjectiveStringID, GameMessage title, GameMessage[] objectives, CompletionCondition completionCondition) {
		registerAfter(stringID, afterObjectiveStringID, title, objectives, completionCondition, null, true);
	}

	public static void registerAfter(String stringID, String afterObjectiveStringID, GameMessage title, GameMessage objective, CompletionCondition completionCondition, LootTable rewards) {
		registerAfter(stringID, afterObjectiveStringID, title, new GameMessage[]{objective}, completionCondition, rewards, true);
	}

	public static void registerAfter(String stringID, String afterObjectiveStringID, GameMessage title, GameMessage[] objectives, CompletionCondition completionCondition, LootTable rewards) {
		registerAfter(stringID, afterObjectiveStringID, title, objectives, completionCondition, rewards, true);
	}

	public static void registerAfter(String stringID, String afterObjectiveStringID, GameMessage title, GameMessage[] objectives, CompletionCondition completionCondition, LootTable rewards, boolean autoClaimWithoutRewards) {
		validateRegistration(stringID, afterObjectiveStringID, title, objectives);
		Entry entry = new Entry(title, objectives, completionCondition, rewards, autoClaimWithoutRewards, afterObjectiveStringID, false, null);
		entries.put(stringID, entry);
		StoryObjectiveRegistry.registerObjective(stringID, GuideStoryObjective.class, false).showAfterObjective(afterObjectiveStringID);
	}

	public static void registerButtonAfter(String stringID, String afterObjectiveStringID, GameMessage title, GameMessage objective, GameMessage buttonText) {
		registerButtonAfter(stringID, afterObjectiveStringID, title, new GameMessage[]{objective}, buttonText);
	}

	public static void registerButtonAfter(String stringID, String afterObjectiveStringID, GameMessage title, GameMessage[] objectives, GameMessage buttonText) {
		validateRegistration(stringID, afterObjectiveStringID, title, objectives);
		validateButtonText(stringID, buttonText);
		Entry entry = new Entry(title, objectives, null, null, true, afterObjectiveStringID, false, buttonText);
		entries.put(stringID, entry);
		StoryObjectiveRegistry.registerObjective(stringID, GuideStoryObjective.class, false).showAfterObjective(afterObjectiveStringID);
	}


	@SuppressWarnings("unchecked")
	public static void applyOrdering() {
		if (entries.isEmpty()) return;

		try {
			Field sortedElementsField = StoryObjectiveRegistry.class.getDeclaredField("sortedElements");
			sortedElementsField.setAccessible(true);
			ArrayList elements = (ArrayList)sortedElementsField.get(null);
			if (elements == null) return;

			Map<String, Object> guideElements = new HashMap<>();
			for (Object object : new ArrayList(elements)) {
				StoryObjectiveRegistry.StoryObjectiveRegistryElement element = (StoryObjectiveRegistry.StoryObjectiveRegistryElement)object;
				if (entries.containsKey(element.getStringID())) {
					guideElements.put(element.getStringID(), element);
					elements.remove(element);
				}
			}

			Map<String, String> lastAfterByAnchor = new HashMap<>();
			for (Map.Entry<String, Entry> registered : entries.entrySet()) {
				String stringID = registered.getKey();
				Entry entry = registered.getValue();
				Object element = guideElements.get(stringID);
				if (element == null) continue;

				String insertionAnchor = entry.anchorObjectiveStringID;
				if (!entry.showBefore) {
					insertionAnchor = lastAfterByAnchor.getOrDefault(entry.anchorObjectiveStringID, entry.anchorObjectiveStringID);
				}

				int anchorIndex = findElementIndex(elements, insertionAnchor);
				if (anchorIndex < 0) {
					throw new IllegalStateException("Could not find story objective anchor while sorting: " + insertionAnchor);
				}

				elements.add(entry.showBefore ? anchorIndex : anchorIndex + 1, element);
				if (!entry.showBefore) {
					lastAfterByAnchor.put(entry.anchorObjectiveStringID, stringID);
				}
			}

			Field sortedIndexField = StoryObjectiveRegistry.StoryObjectiveRegistryElement.class.getDeclaredField("sortedIndex");
			sortedIndexField.setAccessible(true);
			for (int i = 0; i < elements.size(); i++) {
				sortedIndexField.setInt(elements.get(i), i);
			}
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("Failed to apply guide story objective ordering", e);
		}
	}

	public static void moveObjectiveAfter(String stringID, String afterObjectiveStringID) {
		if (stringID == null || stringID.isEmpty()) {
			Logging.logMessage("[StoryObjectives] Cannot move objective: stringID is null or empty");
			return;
		}
		if (afterObjectiveStringID == null || afterObjectiveStringID.isEmpty()) {
			Logging.logMessage("[StoryObjectives] Cannot move objective " + stringID + ": anchor is null or empty");
			return;
		}

		try {
			Field sortedElementsField = StoryObjectiveRegistry.class.getDeclaredField("sortedElements");
			sortedElementsField.setAccessible(true);
			ArrayList elements = (ArrayList)sortedElementsField.get(null);
			if (elements == null) {
				Logging.logMessage("[StoryObjectives] Cannot move " + stringID + " after " + afterObjectiveStringID + ": sortedElements is null");
				return;
			}

			int objectiveIndex = findElementIndex(elements, stringID);
			if (objectiveIndex < 0) {
				Logging.logMessage("[StoryObjectives] Cannot move " + stringID + " after " + afterObjectiveStringID + ": objective not found");
				return;
			}

			Object objectiveElement = elements.remove(objectiveIndex);
			int anchorIndex = findElementIndex(elements, afterObjectiveStringID);
			if (anchorIndex < 0) {
				elements.add(objectiveIndex, objectiveElement);
				Logging.logMessage("[StoryObjectives] Cannot move " + stringID + " after " + afterObjectiveStringID + ": anchor not found");
				return;
			}

			elements.add(anchorIndex + 1, objectiveElement);
			Field sortedIndexField = StoryObjectiveRegistry.StoryObjectiveRegistryElement.class.getDeclaredField("sortedIndex");
			sortedIndexField.setAccessible(true);
			for (int i = 0; i < elements.size(); i++) {
				sortedIndexField.setInt(elements.get(i), i);
			}
			if (Logging.logEnabled) Logging.logMessage("[StoryObjectives] Moved " + stringID + " after " + afterObjectiveStringID);
		}
		catch (ReflectiveOperationException e) {
			Logging.logMessage("[StoryObjectives] Failed moving " + stringID + " after " + afterObjectiveStringID + ": " + e.getMessage());
			throw new RuntimeException("Failed to move story objective in ordering", e);
		}
	}

	@SuppressWarnings("unchecked")
	public static void removeObjectivesFromOrdering(String... stringIDs) {
		if (stringIDs == null || stringIDs.length == 0) return;
		try {
			Field sortedElementsField = StoryObjectiveRegistry.class.getDeclaredField("sortedElements");
			sortedElementsField.setAccessible(true);
			ArrayList elements = (ArrayList)sortedElementsField.get(null);
			if (elements == null) {
				Logging.logMessage("[StoryObjectives] Cannot remove vanilla objectives because sortedElements is null");
				return;
			}
			HashSet<String> remove = new HashSet<>();
			Collections.addAll(remove, stringIDs);
			removedObjectiveStringIDs.addAll(remove);
			int before = elements.size();
			elements.removeIf(object -> remove.contains(((StoryObjectiveRegistry.StoryObjectiveRegistryElement)object).getStringID()));
			Field sortedIndexField = StoryObjectiveRegistry.StoryObjectiveRegistryElement.class.getDeclaredField("sortedIndex");
			sortedIndexField.setAccessible(true);
			for (int i = 0; i < elements.size(); i++) sortedIndexField.setInt(elements.get(i), i);
			if (Logging.logEnabled) Logging.logMessage("[StoryObjectives] Removed " + (before - elements.size()) + " vanilla objectives from progression ordering");
		}
		catch (ReflectiveOperationException e) {
			Logging.logMessage("[StoryObjectives] Failed to remove vanilla objectives from progression ordering: " + e.getMessage());
			throw new RuntimeException("Failed to remove vanilla story objectives from ordering", e);
		}
	}


	public static boolean isRemovedFromOrdering(String stringID) {
		return stringID != null && removedObjectiveStringIDs.contains(stringID);
	}

	private static int findElementIndex(ArrayList elements, String stringID) {
		for (int i = 0; i < elements.size(); i++) {
			StoryObjectiveRegistry.StoryObjectiveRegistryElement element = (StoryObjectiveRegistry.StoryObjectiveRegistryElement)elements.get(i);
			if (element.getStringID().equals(stringID)) return i;
		}
		return -1;
	}

	public static void complete(ServerClient client, String stringID) {
		if (client == null) {
			Logging.logMessage("complete(" + stringID + "): client is null");
			return;
		}

		int objectiveID = StoryObjectiveRegistry.getObjectiveID(stringID);
		Logging.logMessage("complete(" + stringID + "): objectiveID=" + objectiveID);

		if (objectiveID < 0) {
			Logging.logMessage("Objective is not registered");
			return;
		}

		StoryObjective objective = client.storyManager.getObjective(objectiveID);

		Logging.logMessage(
				"Objective=" + objective
						+ ", class=" + (objective == null ? "null" : objective.getClass().getName())
						+ ", completed=" + (objective != null && objective.isCompleted())
						+ ", claimed=" + (objective != null && objective.isClaimed())
		);

		if (objective instanceof GuideStoryObjective) {
			Logging.logMessage("Calling completeGuide()");
			((GuideStoryObjective)objective).completeGuide();

			Logging.logMessage(
					"After completeGuide: completed=" + objective.isCompleted()
							+ ", claimed=" + objective.isClaimed()
			);
		} else {
			Logging.logMessage("NOT a GuideStoryObjective");
		}
	}

	public static boolean isButtonObjective(String stringID) {
		Entry entry = entries.get(stringID);
		return entry != null && entry.completionButton != null;
	}

	static Entry getEntry(String stringID) {
		return entries.get(stringID);
	}

	private static void validateRegistration(String stringID, String anchorObjectiveStringID, GameMessage title, GameMessage[] objectives) {
		if (stringID == null || stringID.isEmpty()) {
			throw new IllegalArgumentException("Guide story objective stringID cannot be empty");
		}
		if (entries.containsKey(stringID) || StoryObjectiveRegistry.objectiveExists(stringID)) {
			throw new IllegalArgumentException("Story objective is already registered: " + stringID);
		}
		if (!StoryObjectiveRegistry.objectiveExists(anchorObjectiveStringID)) {
			throw new IllegalArgumentException("Story objective anchor does not exist: " + anchorObjectiveStringID);
		}
		if (title == null) {
			throw new IllegalArgumentException("Guide story objective title cannot be null: " + stringID);
		}
		if (objectives == null || objectives.length == 0) {
			throw new IllegalArgumentException("Guide story objective must have at least one objective line: " + stringID);
		}
		for (GameMessage objective : objectives) {
			if (objective == null) {
				throw new IllegalArgumentException("Guide story objective line cannot be null: " + stringID);
			}
		}
	}

	private static void validateButtonText(String stringID, GameMessage buttonText) {
		if (buttonText == null) {
			throw new IllegalArgumentException("Guide story objective completion button cannot be null: " + stringID);
		}
	}

	@FunctionalInterface
	public interface CompletionCondition {
		boolean isCompleted(GuideStoryObjective objective);
	}

	static class Entry {
		final GameMessage title;
		final GameMessage[] objectives;
		final CompletionCondition completionCondition;
		final LootTable rewards;
		final boolean autoClaimWithoutRewards;
		final String anchorObjectiveStringID;
		final boolean showBefore;
		final GameMessage completionButton;

		Entry(GameMessage title, GameMessage[] objectives, CompletionCondition completionCondition, LootTable rewards, boolean autoClaimWithoutRewards, String anchorObjectiveStringID, boolean showBefore, GameMessage completionButton) {
			this.title = title;
			this.objectives = objectives.clone();
			this.completionCondition = completionCondition;
			this.rewards = rewards;
			this.autoClaimWithoutRewards = autoClaimWithoutRewards;
			this.anchorObjectiveStringID = anchorObjectiveStringID;
			this.showBefore = showBefore;
			this.completionButton = completionButton;
		}
	}
}
