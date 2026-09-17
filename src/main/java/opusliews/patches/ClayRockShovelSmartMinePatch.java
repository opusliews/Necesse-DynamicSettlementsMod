package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.registries.ObjectLayerRegistry;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.util.GameMath;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.ToolDamageItem;
import necesse.inventory.item.toolItem.ToolType;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(
		target = ToolDamageItem.class,
		name = "getFirstSmartHitTile",
		arguments = {Level.class, PlayerMob.class, InventoryItem.class, int.class, int.class}
)
public class ClayRockShovelSmartMinePatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This ToolDamageItem tool,
			@Advice.Argument(0) Level level,
			@Advice.Argument(1) PlayerMob player,
			@Advice.Argument(2) InventoryItem item,
			@Advice.Argument(3) int mouseX,
			@Advice.Argument(4) int mouseY,
			@Advice.Return(readOnly = false) ToolDamageItem.SmartMineTarget result
	) {
		if (tool.getToolType(item) != ToolType.SHOVEL) {
			return;
		}

		int tileX = GameMath.getTileCoordinate(mouseX);
		int tileY = GameMath.getTileCoordinate(mouseY);
		if (!tool.isTileInRange(level, tileX, tileY, player, null, item)) {
			return;
		}

		int clayRockID = ObjectRegistry.getObjectID("clayrock");
		for (int layerID = ObjectLayerRegistry.getTotalLayers() - 1; layerID >= 0; layerID--) {
			if (level.getObjectID(layerID, tileX, tileY) == clayRockID
					&& tool.canDamageTile(level, layerID, tileX, tileY, player, item)) {
				result = new ToolDamageItem.SmartMineTarget(level, tileX, tileY, true, layerID);
				return;
			}
		}
	}
}
