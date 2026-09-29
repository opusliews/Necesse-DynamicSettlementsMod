package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.settler.personalities.ClumsySettlerPersonality;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementPersonalityLevelSystem;

@ModMethodPatch(target = ClumsySettlerPersonality.class, name = "settlementTick", arguments = {ServerSettlementData.class})
public class ClumsyPersonalityBedLevelContextPatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This ClumsySettlerPersonality personality, @Advice.Argument(0) ServerSettlementData settlement) {
		HumanMob mob = personality.mob;
		SettlementPersonalityLevelSystem.beginBedLevelContext(mob, settlement);
	}

	@Advice.OnMethodExit(onThrowable = Throwable.class)
	public static void onExit() {
		SettlementPersonalityLevelSystem.endBedLevelContext();
	}
}
