package opusliews.patches;

import necesse.engine.input.InputID;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.engine.window.WindowManager;
import necesse.inventory.item.toolItem.TileDamageOption;
import necesse.inventory.item.toolItem.ToolDamageItem;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;
import opusliews.settlement.SettlementChestProtectionSystem;

@ModMethodPatch(
		target = ToolDamageItem.class,
		name = "setupAttackMapContentHitTile",
		arguments = {GNDItemMap.class, Level.class, TileDamageOption.class}
)
public class SettlementChestProtectionToolMapPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Argument(0) GNDItemMap map,
			@Advice.Argument(1) Level level
	) {
		if (map == null || level == null || !level.isClient()) return;

		try {
			boolean ctrlDown = WindowManager.getWindow().getInput().isKeyDown(InputID.KEY_LEFT_CONTROL)
					|| WindowManager.getWindow().getInput().isKeyDown(InputID.KEY_RIGHT_CONTROL);
			map.setBoolean(SettlementChestProtectionSystem.ctrlMapKey, ctrlDown);
			if (Logging.logEnabled && ctrlDown) {
				Logging.logMessage("[ChestProtection] Captured Ctrl=true for tile damage attack map level=" + level.getIdentifier());
			}
		} catch (RuntimeException e) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Failed to capture Ctrl state for tile damage attack: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}
}
