package opusliews.patches;

import necesse.engine.localization.message.LocalMessage;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.gfx.forms.presets.containerComponent.mob.ShopContainerForm;
import necesse.inventory.container.mob.ShopContainer;
import net.bytebuddy.asm.Advice;
import opusliews.guard.GuardDutyDialogueRefresh;
import opusliews.guard.GuardDutySystem;
import opusliews.guard.GuardLevelAssignment;
import opusliews.guard.GuardLevelAssignmentSystem;
import opusliews.mobs.BuilderHumanMob;
import opusliews.multilevelsettlement.SettlementLevelPreference;
import opusliews.multilevelsettlement.SettlementLevelPreferenceSystem;
import opusliews.network.PacketBuilderRoadRepairToggle;
import opusliews.network.PacketGuardDutyToggle;
import opusliews.network.PacketGuardLevelAssignment;
import opusliews.network.PacketSettlerLevelPreference;

@ModMethodPatch(
		target = ShopContainerForm.class,
		name = "addAdventurePartyDialogueOptions",
		arguments = {}
)
public class ShopContainerFormAdventurePartyPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	static boolean onEnter(@Advice.This ShopContainerForm form) {
		addLevelPreferenceOption(form);
		addGuardDutyOption(form);
		addGuardLevelOption(form);
		return handleBuilderAdventurePartyOptions(form);
	}

	public static void addLevelPreferenceOption(ShopContainerForm form) {
		ShopContainer container = (ShopContainer)form.getContainer();
		if (!container.hasSettlerAccess || container.isInYourAdventureParty || container.isSettlerOutsideSettlement) return;
		if (!(container.humanShop instanceof necesse.entity.mobs.friendly.human.HumanMob)) return;
		if (container.humanShop instanceof GuardHumanMob) return;

		necesse.entity.mobs.friendly.human.HumanMob human = container.humanShop;
		SettlementLevelPreference preference = SettlementLevelPreferenceSystem.getPreference(human);
		String key = preference == SettlementLevelPreference.SURFACE ? "levelpreferencesurface"
				: preference == SettlementLevelPreference.CAVE ? "levelpreferencecave"
				: "levelpreferenceauto";
		form.dialogueForm.addDialogueOption(new LocalMessage("ui", key), () -> {
			SettlementLevelPreference next = SettlementLevelPreferenceSystem.getPreference(human).next();
			SettlementLevelPreferenceSystem.setPreference(human, next);
			form.getClient().network.sendPacket(new PacketSettlerLevelPreference(human.getUniqueID(), next));
			form.updateDialogue();
		});
	}

	public static void addGuardDutyOption(ShopContainerForm form) {
		ShopContainer container = (ShopContainer)form.getContainer();
		if (!(container.humanShop instanceof GuardHumanMob)) {
			return;
		}

		GuardHumanMob guard = (GuardHumanMob)container.humanShop;
		if (!container.hasSettlerAccess
				|| container.isInYourAdventureParty
				|| container.isSettlerOutsideSettlement
				|| !guard.isSettlerOnCurrentLevel()) {
			return;
		}

		GuardDutyDialogueRefresh.track(guard, form);
		form.dialogueForm.addDialogueOption(
				new LocalMessage(
						"ui",
						GuardDutySystem.isNightDuty(guard)
								? "guardswitchdayduty"
								: "guardswitchnightduty"
				),
				() -> {
					boolean nightDuty = !GuardDutySystem.isNightDuty(guard);
					form.getClient().network.sendPacket(new PacketGuardDutyToggle(guard.getUniqueID(), nightDuty));
				}
		);
	}

	public static void addGuardLevelOption(ShopContainerForm form) {
		ShopContainer container = (ShopContainer)form.getContainer();
		if (!(container.humanShop instanceof GuardHumanMob)) return;

		GuardHumanMob guard = (GuardHumanMob)container.humanShop;
		if (!container.hasSettlerAccess
				|| container.isInYourAdventureParty
				|| container.isSettlerOutsideSettlement
				|| !guard.isSettlerOnCurrentLevel()) return;

		GuardDutyDialogueRefresh.track(guard, form);
		GuardLevelAssignment assignment = GuardLevelAssignmentSystem.getAssignment(guard);
		form.dialogueForm.addDialogueOption(
				new LocalMessage("ui", assignment == GuardLevelAssignment.CAVE ? "guardlevelcave" : "guardlevelsurface"),
				() -> form.getClient().network.sendPacket(new PacketGuardLevelAssignment(guard.getUniqueID(), assignment.next()))
		);
	}

	public static boolean handleBuilderAdventurePartyOptions(ShopContainerForm form) {
		ShopContainer container = (ShopContainer)form.getContainer();

		if (!(container.humanShop instanceof BuilderHumanMob)) {
			return false;
		}

		BuilderHumanMob builder = (BuilderHumanMob)container.humanShop;

		if (container.hasSettlerAccess
				&& container.canJoinAdventureParties
				&& !container.isInYourAdventureParty) {
			if (form.isCurrent(form.partyConfigForm)) {
				container.setIsInPartyConfig.runAndSend(false);
				form.makeCurrent(form.dialogueForm);
			}

			form.dialogueForm.addDialogueOption(
					new LocalMessage("ui", "settlerjoinparty"),
					() -> {
						container.joinAdventurePartyAction.runAndSend();
						form.waitingForPartyConfirm = true;
					}
			);

			if (container.isSettlerOutsideSettlement) {
				form.dialogueForm.addDialogueOption(
						new LocalMessage("ui", "settlerreturntosettlement"),
						container.returnToSettlementAction::runAndSend
				);
			}

			return true;
		}

		if (container.isInYourAdventureParty) {
			form.dialogueForm.addDialogueOption(
					new LocalMessage("ui", "confiureadventureparty"),
					() -> {
						container.setIsInPartyConfig.runAndSend(true);
						form.makeCurrent(form.partyConfigForm);
					}
			);

			form.dialogueForm.addDialogueOption(
					new LocalMessage(
							"ui",
							builder.isRepairOnRoad()
									? "builderstoproadrepairs"
									: "builderstartroadrepairs"
					),
					() -> {
						boolean enabled = !builder.isRepairOnRoad();

						builder.setRepairOnRoad(enabled);

						form.getClient().network.sendPacket(
								new PacketBuilderRoadRepairToggle(
										builder.getUniqueID(),
										enabled
								)
						);

						form.updateDialogue();
					}
			);

			form.dialogueForm.addDialogueOption(
					new LocalMessage("ui", "settlerleaveparty"),
					container.leaveAdventurePartyAction::runAndSend
			);
		}

		return true;
	}
}
