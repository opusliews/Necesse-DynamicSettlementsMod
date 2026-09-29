package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementCaveBedSystem;

@ModMethodPatch(target = Level.class, name = "setTile", arguments = {int.class, int.class, int.class})
public class CaveRoomTileChangePatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This Level level, @Advice.Argument(0) int tileX, @Advice.Argument(1) int tileY) {
		SettlementCaveBedSystem.onCaveRoomLevelChanged(level, tileX, tileY);
	}
}
