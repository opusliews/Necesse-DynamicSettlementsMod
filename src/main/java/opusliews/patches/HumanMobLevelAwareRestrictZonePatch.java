package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.level.maps.levelData.settlementData.ZoneTester;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementLevelZoneSystem;

@ModMethodPatch(target = HumanMob.class, name = "getJobRestrictZone", arguments = {})
public class HumanMobLevelAwareRestrictZonePatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This HumanMob human, @Advice.Return(readOnly = false) ZoneTester result) {
		ZoneTester levelAware = SettlementLevelZoneSystem.getCurrentLevelJobRestriction(human);
		if (levelAware != null) result = levelAware;
	}
}
