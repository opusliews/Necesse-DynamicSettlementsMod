package opusliews.patches;

import necesse.engine.localization.message.LocalMessage;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.registries.MobRegistry;
import necesse.engine.storyObjectives.objectives.bossObjectives.DefeatBossStoryObjective;
import necesse.engine.storyObjectives.objectives.bossObjectives.DefeatEvilsProtectorStoryObjective;
import necesse.gfx.forms.components.FormFlow;
import necesse.gfx.forms.components.FormStoryObjectiveComponent;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;

@ModMethodPatch(
		target = DefeatBossStoryObjective.class,
		name = "setupFormComponent",
		arguments = {FormStoryObjectiveComponent.class, FormFlow.class}
)
public class DefeatEvilsProtectorStoryObjectiveTextPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.This DefeatBossStoryObjective objective,
			@Advice.Argument(0) FormStoryObjectiveComponent form,
			@Advice.Argument(1) FormFlow flow
	) {
		if (!(objective instanceof DefeatEvilsProtectorStoryObjective)) return false;
		renderEvilsProtectorObjective(objective, form, flow);
		return true;
	}

	public static void renderEvilsProtectorObjective(
			DefeatBossStoryObjective objective,
			FormStoryObjectiveComponent form,
			FormFlow flow
	) {
		if (objective == null) {
			Logging.logMessage("[StoryObjectives] Cannot render Evil's Protector objective text: objective is null");
			return;
		}
		if (form == null) {
			Logging.logMessage("[StoryObjectives] Cannot render Evil's Protector objective text: form is null");
			return;
		}
		if (flow == null) {
			Logging.logMessage("[StoryObjectives] Cannot render Evil's Protector objective text: flow is null");
			return;
		}

		form.addTitle(flow, new LocalMessage("objectives", "majorobjective"));
		flow.next(2);
		form.addObjective(
				flow,
				-1,
				new LocalMessage("storyguide", "defeatevilsprotectorobjective"),
				objective.isCompleted()
		);

		if (!objective.isCompleted()) {
			String hint = MobRegistry.getKillHint(objective.mobStringID);
			if (hint != null) {
				form.addObjectiveNote(flow, new StaticMessage(hint), false);
			}
		}

		flow.next(2);
		form.addClaimText(flow);
		form.addRewardsText(flow);
	}
}
