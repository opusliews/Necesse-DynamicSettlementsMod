package opusliews.story;

import necesse.engine.localization.message.GameMessage;
import necesse.engine.storyObjectives.StoryObjective;
import necesse.engine.storyObjectives.StoryObjectiveManager;
import necesse.gfx.forms.components.FormFlow;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormStoryObjectiveComponent;
import necesse.gfx.forms.components.localComponents.FormLocalTextButton;
import necesse.gfx.ui.ButtonColor;
import necesse.inventory.lootTable.LootTable;
import opusliews.network.PacketCompleteGuideObjective;

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

		if (entry.completionButton != null && !isCompleted() && manager.isClient()) {
			flow.next(8);
			FormLocalTextButton button = form.addComponent(new FormLocalTextButton(
					entry.completionButton,
					10,
					0,
					form.getWidth() - 20,
					FormInputSize.SIZE_32,
					ButtonColor.BASE
			));
			button.onClicked(event -> manager.getClient().network.sendPacket(new PacketCompleteGuideObjective(getStringID())));
			flow.nextY(button);
		}

		if (entry.rewards != null) {
			form.addClaimText(flow);
			form.addRewardsText(flow);
		}
	}
}
