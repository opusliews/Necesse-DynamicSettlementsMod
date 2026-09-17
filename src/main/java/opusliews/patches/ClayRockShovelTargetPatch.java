package opusliews.patches;

import java.awt.geom.Line2D;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.registries.ObjectLayerRegistry;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.util.GameMath;
import necesse.entity.DamagedObjectEntity;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.TileDamageOption;
import necesse.inventory.item.toolItem.ToolDamageItem;
import necesse.inventory.item.toolItem.ToolType;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(
		target = ToolDamageItem.class,
		name = "getTileDamageOption",
		arguments = {Level.class, int.class, int.class, PlayerMob.class, Line2D.class, InventoryItem.class, boolean.class}
)
public class ClayRockShovelTargetPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This ToolDamageItem tool,
			@Advice.Argument(0) Level level,
			@Advice.Argument(1) int levelX,
			@Advice.Argument(2) int levelY,
			@Advice.Argument(3) PlayerMob player,
			@Advice.Argument(4) Line2D playerPositionLine,
			@Advice.Argument(5) InventoryItem item,
			@Advice.Argument(6) boolean ignoreTileDamage,
			@Advice.Return(readOnly = false) TileDamageOption result
	) {
		if (tool.getToolType(item) != ToolType.SHOVEL) {
			return;
		}

		int tileX = GameMath.getTileCoordinate(levelX);
		int tileY = GameMath.getTileCoordinate(levelY);
		if (!tool.isTileInRange(level, tileX, tileY, player, playerPositionLine, item)) {
			return;
		}

		if (!tool.canDamageTile(level, 0, tileX, tileY, player, item)) {
			return;
		}

		int clayRockID = ObjectRegistry.getObjectID("clayrock");
		for (int layerID = ObjectLayerRegistry.getTotalLayers() - 1; layerID >= 0; layerID--) {
			if (level.getObjectID(layerID, tileX, tileY) != clayRockID) {
				continue;
			}

			if (!ignoreTileDamage) {
				DamagedObjectEntity damaged = level.entityManager.getDamagedObjectEntity(tileX, tileY);
				if (damaged != null && damaged.objectDamage[layerID] >= ObjectRegistry.getObject(clayRockID).objectHealth) {
					return;
				}
			}

			result = new TileDamageOption(layerID, tileX, tileY);
			return;
		}
	}
}
