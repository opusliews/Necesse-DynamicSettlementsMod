package opusliews.story;

import necesse.engine.localization.message.GameMessage;
import necesse.engine.storyObjectives.StoryObjective;
import necesse.engine.storyObjectives.StoryObjectiveManager;
import necesse.gfx.forms.components.FormFlow;
import necesse.gfx.forms.components.FormStoryObjectiveComponent;
import necesse.inventory.lootTable.LootTable;

public class GuideStoryObjective extends StoryObjective {
	public GuideStoryObjective(StoryObjectiveManager manager) {
		super(manager);
	}

	@Override
	public void serverTickCurrentObjective() {
		super.serverTickCurrentObjective();
		if (isCompleted()) return;

		GuideStoryObjectiveRegistry.Entry entry = GuideStoryObjectiveRegistry.getEntry(getStringID());
		if (entry != null && entry.completionCondition != null && entry.completionCondition.isCompleted(this)) {
			completeGuide();
		}
	}

	public void completeGuide() {
		if (isCompleted()) return;

		GuideStoryObjectiveRegistry.Entry entry = GuideStoryObjectiveRegistry.getEntry(getStringID());
		markCompleted();
		if (entry != null && entry.rewards == null && entry.autoClaimWithoutRewards && !isClaimed()) {
			claimRewards(null);
		}
	}

	@Override
	public LootTable getRewards() {
		GuideStoryObjectiveRegistry.Entry entry = GuideStoryObjectiveRegistry.getEntry(getStringID());
		return entry == null ? null : entry.rewards;
	}

	@Override
	public void setupFormComponent(FormStoryObjectiveComponent form, FormFlow flow) {
		GuideStoryObjectiveRegistry.Entry entry = GuideStoryObjectiveRegistry.getEntry(getStringID());
		if (entry == null) return;

		form.addTitle(flow, entry.title);
		for (int i = 0; i < entry.objectives.length; i++) {
			GameMessage objective = entry.objectives[i];
			form.addObjective(flow, i + 1, objective, isCompleted());
		}

		if (entry.rewards != null) {
			form.addClaimText(flow);
			form.addRewardsText(flow);
		}
	}
}
