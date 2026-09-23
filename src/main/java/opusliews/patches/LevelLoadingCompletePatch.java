package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.damage.DamageRepairLevelData;
import opusliews.damage.WeatheringLevelData;
import opusliews.settler.BuilderRequestLevelData;
import opusliews.tile.CharcoalPitLevelData;

@ModMethodPatch(target = Level.class, name = "onLoadingComplete", arguments = {})
public class LevelLoadingCompletePatch {
	@Advice.OnMethodEnter
	static void onEnter(@Advice.This Level level) {
		if (!level.isServer()) {
			return;
		}

		BuilderRequestLevelData.get(level, true);
		CharcoalPitLevelData.get(level, true);

		DamageRepairLevelData.get(level, true);
		WeatheringLevelData.get(level, true);
	}
}
