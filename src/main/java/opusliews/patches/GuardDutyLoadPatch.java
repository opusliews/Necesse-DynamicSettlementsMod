package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.save.LoadData;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.guard.GuardDuty;
import opusliews.guard.GuardDutySystem;
import opusliews.guard.GuardFatigueSystem;
import opusliews.guard.GuardLevelAssignment;
import opusliews.guard.GuardLevelAssignmentSystem;
import opusliews.multilevelsettlement.SettlementLevelPreference;
import opusliews.multilevelsettlement.SettlementLevelPreferenceSystem;

@ModMethodPatch(target = HumanMob.class, name = "applyLoadData", arguments = {LoadData.class})
public class GuardDutyLoadPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This HumanMob mob, @Advice.Argument(0) LoadData save) {
		SettlementLevelPreference loadedPreference = save.hasLoadDataByName("dynamicSettlementsLevelPreference")
				? SettlementLevelPreference.fromOrdinal(save.getInt("dynamicSettlementsLevelPreference", 0, false))
				: SettlementLevelPreferenceSystem.getGeneratedPreference(mob.settlerSeed);
		SettlementLevelPreferenceSystem.setPreference(mob, loadedPreference);
		if (mob instanceof GuardHumanMob) {
			GuardHumanMob guard = (GuardHumanMob)mob;
			boolean nightDuty = save.getBoolean("dynamicSettlementsNightGuardDuty", false, false);
			GuardDutySystem.setDuty(guard, nightDuty ? GuardDuty.NIGHT : GuardDuty.DAY);
			GuardLevelAssignmentSystem.setAssignment(guard, GuardLevelAssignment.fromOrdinal(save.getInt("dynamicSettlementsGuardLevel", 0, false)));
			GuardFatigueSystem.applyLoadedState(
					guard,
					save.getInt("dynamicSettlementsGuardFatigue", 0, false),
					save.getBoolean("dynamicSettlementsGuardRestActive", false, false),
					save.getBoolean("dynamicSettlementsGuardRestInterrupted", false, false)
			);
		}
	}
}
