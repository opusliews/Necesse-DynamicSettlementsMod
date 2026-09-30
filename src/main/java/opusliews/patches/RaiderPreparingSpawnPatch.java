package opusliews.patches;

import java.awt.Point;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.levelEvent.settlementRaidEvent.SettlementRaidLevelEvent;
import net.bytebuddy.asm.Advice;
import opusliews.raids.MultiLevelRaidSystem;

@ModMethodPatch(target = SettlementRaidLevelEvent.class, name = "spawnRaider", arguments = {Point.class, int.class, int.class})
public class RaiderPreparingSpawnPatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This SettlementRaidLevelEvent event, @Advice.Argument(value = 0, readOnly = false) Point tile) {
		tile = MultiLevelRaidSystem.adjustPreparingSpawn(event, tile);
	}
}
