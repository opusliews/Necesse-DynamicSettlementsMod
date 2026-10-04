package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.LevelSettler;
import necesse.level.maps.levelData.settlementData.settler.Settler;
import net.bytebuddy.asm.Advice;
import opusliews.progression.GuideProgressionSystem;

@ModMethodPatch(target = Settler.class, name = "onMoveIn", arguments = {LevelSettler.class})
public class SettlerMoveInGuideProgressionPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This Settler settler, @Advice.Argument(0) LevelSettler levelSettler) {
		if (settler == null || levelSettler == null) return;
		GuideProgressionSystem.onSettlerMovedIn(levelSettler.data, settler.getStringID());
	}
}
