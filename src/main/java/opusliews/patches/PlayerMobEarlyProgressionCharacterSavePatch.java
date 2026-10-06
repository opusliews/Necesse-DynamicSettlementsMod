package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.save.SaveData;
import necesse.entity.mobs.PlayerMob;
import net.bytebuddy.asm.Advice;
import opusliews.progression.EarlyHealthProgressionSystem;
import opusliews.progression.GuideProgressionSystem;
import opusliews.worldgengating.WorldgenStationProgressionSystem;

@ModMethodPatch(target = PlayerMob.class, name = "addLoadedCharacterSaveData", arguments = {SaveData.class})
public class PlayerMobEarlyProgressionCharacterSavePatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This PlayerMob player, @Advice.Argument(0) SaveData save) {
		EarlyHealthProgressionSystem.addCharacterSaveData(player, save);
		WorldgenStationProgressionSystem.addSaveData(player, save);
		GuideProgressionSystem.addSaveData(player, save);
		opusliews.logging.InventoryPersistenceDebug.logPlayer("CHARACTER_SAVE", player);
	}
}
