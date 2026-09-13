package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.save.SaveData;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opus.guard.GuardDutySystem;
import opus.guard.GuardFatigueSystem;

@ModMethodPatch(target = HumanMob.class, name = "addSaveData", arguments = {SaveData.class})
public class GuardDutySavePatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This HumanMob mob, @Advice.Argument(0) SaveData save) {
		if (mob instanceof GuardHumanMob) {
			GuardHumanMob guard = (GuardHumanMob)mob;
			save.addBoolean("settlementBuildersNightGuardDuty", GuardDutySystem.isNightDuty(guard));
			save.addInt("settlementBuildersGuardFatigue", GuardFatigueSystem.getFatigue(guard));
			save.addBoolean("settlementBuildersGuardRestActive", GuardFatigueSystem.isRestPeriodActive(guard));
			save.addBoolean("settlementBuildersGuardRestInterrupted", GuardFatigueSystem.wasRestInterrupted(guard));
		}
	}
}
