package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.storyObjectives.StoryObjectiveManager;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;
import opusliews.story.GuideStoryObjectiveRegistry;

@ModMethodPatch(
		target = StoryObjectiveManager.class,
		name = "hasCompletedObjective",
		arguments = {String.class}
)
public class RemovedStoryObjectiveCompletionPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.Argument(0) String objectiveStringID,
			@Advice.Local("removedStoryObjective") boolean removedStoryObjective
	) {
		removedStoryObjective = GuideStoryObjectiveRegistry.isRemovedFromOrdering(objectiveStringID);
		if (removedStoryObjective && Logging.logEnabled) {
			Logging.logMessage(
					"[StoryObjectives] Compatibility completion for removed vanilla objective="
							+ objectiveStringID
			);
		}
		return removedStoryObjective;
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Local("removedStoryObjective") boolean removedStoryObjective,
			@Advice.Return(readOnly = false) boolean completed
	) {
		if (removedStoryObjective) completed = true;
	}
}
