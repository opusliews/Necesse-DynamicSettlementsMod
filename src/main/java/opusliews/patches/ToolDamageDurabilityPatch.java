package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.ToolDamageItem;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.durability.DurabilityAction;
import opusliews.durability.DurabilityContext;
import opusliews.durability.ItemDurabilitySystem;
import opusliews.logging.Logging;

import java.awt.geom.Line2D;

@ModMethodPatch(
		target = ToolDamageItem.class,
		name = "runLevelDamage",
		arguments = {Level.class, int.class, int.class, int.class, int.class, int.class, PlayerMob.class, Line2D.class, InventoryItem.class, int.class, GNDItemMap.class}
)
public class ToolDamageDurabilityPatch {
	@Advice.OnMethodEnter
	public static ItemDurabilitySystem.ToolDamageState onEnter(
			@Advice.This ToolDamageItem toolItem,
			@Advice.Argument(0) Level level,
			@Advice.Argument(3) int objectLayerID,
			@Advice.Argument(4) int tileX,
			@Advice.Argument(5) int tileY,
			@Advice.Argument(6) PlayerMob player,
			@Advice.Argument(7) Line2D playerPositionLine,
			@Advice.Argument(8) InventoryItem item
	) {
		if (player == null || !player.isServer()) return null;
		if (!ItemDurabilitySystem.isBreakable(item)) return null;
		if (!toolItem.isTileInRange(level, tileX, tileY, player, playerPositionLine, item)) return null;
		if (!toolItem.canDamageTile(level, objectLayerID, tileX, tileY, player, item)) return null;
		return ItemDurabilitySystem.captureToolDamageState(toolItem, level, objectLayerID, tileX, tileY, item);
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Argument(0) Level level,
			@Advice.Argument(6) PlayerMob player,
			@Advice.Enter ItemDurabilitySystem.ToolDamageState state,
			@Advice.Return(readOnly = false) InventoryItem returnedItem
	) {
		if (state == null || player == null || !player.isServer() || returnedItem == null) return;

		DurabilityContext context = state.targetObject != null
				? DurabilityContext.object(level, player, state.targetObject)
				: DurabilityContext.player(level, player);

		Logging.logMessage("[Durability] Tool damage result item=" + returnedItem.item.getStringID()
				+ " tile=" + state.tileX + "," + state.tileY
				+ " action=" + state.damageAction);

		InventoryItem originalReturnedItem = returnedItem;
		returnedItem = ItemDurabilitySystem.applyActionAndGetItem(returnedItem, state.damageAction, context);
		if (returnedItem == null) {
			Logging.logMessage("[Durability] Tool broke during " + state.damageAction + "; returning depleted item for attack-handler cleanup");
			returnedItem = originalReturnedItem;
		}

		if (returnedItem.getAmount() > 0 && ItemDurabilitySystem.wasToolTargetDestroyed(level, state)) {
			DurabilityAction destroyedAction = state.damageAction == DurabilityAction.OBJECT_DAMAGE
					? DurabilityAction.OBJECT_DESTROYED
					: DurabilityAction.TILE_DESTROYED;
			Logging.logMessage("[Durability] Target destroyed tile=" + state.tileX + "," + state.tileY + " action=" + destroyedAction);
			InventoryItem beforeDestroyedAction = returnedItem;
			returnedItem = ItemDurabilitySystem.applyActionAndGetItem(returnedItem, destroyedAction, context);
			if (returnedItem == null) {
				Logging.logMessage("[Durability] Tool broke during " + destroyedAction + "; returning depleted item for attack-handler cleanup");
				returnedItem = beforeDestroyedAction;
			}
		}
	}
}
