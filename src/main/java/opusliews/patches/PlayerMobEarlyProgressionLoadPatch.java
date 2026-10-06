package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.save.LoadData;
import necesse.entity.mobs.PlayerMob;
import net.bytebuddy.asm.Advice;
import opusliews.progression.EarlyHealthProgressionSystem;
import opusliews.progression.GuideProgressionSystem;
import opusliews.worldgengating.WorldgenStationProgressionSystem;

@ModMethodPatch(target = PlayerMob.class, name = "applyLoadData", arguments = {LoadData.class})
public class PlayerMobEarlyProgressionLoadPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This PlayerMob player, @Advice.Argument(0) LoadData save) {
		EarlyHealthProgressionSystem.applyWorldFallbackLoadData(player, save);
		WorldgenStationProgressionSystem.applyLoadData(player, save);
		GuideProgressionSystem.applyLoadData(player, save);
		opusliews.logging.InventoryPersistenceDebug.logPlayer("WORLD_LOAD", player);
	}
}
