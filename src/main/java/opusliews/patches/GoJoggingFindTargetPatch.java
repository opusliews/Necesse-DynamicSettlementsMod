package opusliews.patches;

import java.awt.Point;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.level.maps.levelData.jobs.GoJoggingTileLevelJob;
import necesse.level.maps.levelData.settlementData.ZoneTester;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementJoggingSystem;

@ModMethodPatch(target = GoJoggingTileLevelJob.class, name = "findNextTargetTile", arguments = {Mob.class, ZoneTester.class})
public class GoJoggingFindTargetPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.Argument(0) Mob mob, @Advice.Argument(1) ZoneTester zoneTester, @Advice.Local("dsTarget") Point target) {
		if (mob == null || mob.getLevel() == null || !mob.getLevel().isCave) return false;
		target = SettlementJoggingSystem.findNextTargetTile(mob, zoneTester);
		return true;
	}

	@Advice.OnMethodExit
	public static void onExit(@Advice.Local("dsTarget") Point target, @Advice.Return(readOnly = false) Point result) {
		if (target != null) result = target;
	}
}
