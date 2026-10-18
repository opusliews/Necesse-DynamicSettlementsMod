package opusliews.patches;

import necesse.engine.input.Input;
import necesse.engine.input.controller.ControllerInput;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.registries.ObjectLayerRegistry;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.util.GameMath;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.PlayerInventorySlot;
import necesse.inventory.item.toolItem.ToolDamageItem.SmartMineTarget;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.item.CrudeAxeItem;
import opusliews.object.PlacedLogRegistry;
import opusliews.object.PlacedPlankObject;
import opusliews.object.PlacedPlankRegistry;

import java.awt.*;
import java.awt.geom.Point2D;

/** Bridges custom right-click wood actions to directional controller attack only. */
@ModMethodPatch(target = PlayerMob.class, name = "runClientControllerAttack", arguments = {PlayerInventorySlot.class})
public class ControllerWoodActionsPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean enter(@Advice.This PlayerMob player, @Advice.Argument(0) PlayerInventorySlot slot) {
		return ControllerWoodActions.tryHandle(player, slot);
	}

	@Advice.OnMethodExit
	public static void exit(@Advice.Enter boolean handled, @Advice.Return(readOnly = false) boolean result) {
		if (handled) result = true;
	}

	/** Public helper: inlined ByteBuddy advice must not reference private synthetic members. */
	public static class ControllerWoodActions {
		public static boolean tryHandle(PlayerMob player, PlayerInventorySlot slot) {
			if (!Input.lastInputIsController || ControllerInput.isCursorVisible()) return false;
			Level level = player.getLevel();
			if (level == null || !level.isClient() || slot == null) return false;
			// Do not bypass vanilla attack cooldowns or running attack handlers.
			if (player.isAttacking || player.getAttackHandler() != null) return false;
			InventoryItem item = player.getInv().getItem(slot);
			if (item == null) return false;
			Point2D.Float aim = player.getControllerAimDir();
			float length = (float)Math.sqrt(aim.x * aim.x + aim.y * aim.y);
			if (length < 0.001F) return false;
			float dirX = aim.x / length;
			float dirY = aim.y / length;

			if (item.item.isGlobalIngredient("anylog") || item.item.isGlobalIngredient("dsanyplank")) {
				boolean log = item.item.isGlobalIngredient("anylog");
				int objectID = log ? PlacedLogRegistry.getObjectID(item.item.getStringID())
						: PlacedPlankRegistry.getObjectID(item.item.getStringID());
				if (objectID < 0) return false;
				// RT must not invoke or fall through to hole placement: LT owns this interaction.
				if (ControllerHoleActions.Handler.findHole(level, player, dirX, dirY) != null) return true;
				GameObject object = ObjectRegistry.getObject(objectID);
				int layer = log ? 0 : ObjectLayerRegistry.TILE_LAYER;
				Point target = findPlacement(level, player, dirX, dirY, object, layer);
				if (target != null) player.runClientInteract(target.x, target.y, false);
				// Holding a log/plank must not also attack with it when no placement is possible.
				return true;
			}

			// Dirt piles and fireable items are also LT-only when aimed at a pit.
			if (ControllerHoleActions.Handler.isPitMaterial(item.item)
					&& ControllerHoleActions.Handler.findHole(level, player, dirX, dirY) != null) return true;

			if (item.item instanceof CrudeAxeItem) {
				CrudeAxeItem axe = (CrudeAxeItem)item.item;
				int range = axe.getMiningRange(item, player);
				SmartMineTarget hit = axe.getFirstSmartHitTile(level, player, item,
						(int)(player.x + dirX * range), (int)(player.y + dirY * range));
				if (hit != null && level.getObject(hit.priorityObjectLayerID, hit.x, hit.y) instanceof PlacedPlankObject) {
					player.runClientInteract(hit.x * 32 + 16, hit.y * 32 + 16, false);
					return true;
				}
			}
			return false;
		}

		public static Point findPlacement(Level level, PlayerMob player, float dx, float dy,
				GameObject object, int layer) {
			int lastX = Integer.MIN_VALUE;
			int lastY = Integer.MIN_VALUE;
			// Aim for the furthest valid tile under the stick, avoiding placements at player's feet.
			Point best = null;
			for (int distance = 24; distance <= 96; distance += 4) {
				int x = GameMath.getTileCoordinate((int)(player.x + dx * distance));
				int y = GameMath.getTileCoordinate((int)(player.y + dy * distance));
				if (x == lastX && y == lastY) continue;
				lastX = x;
				lastY = y;
				if (!level.isTileWithinBounds(x, y) || level.isProtected(x, y)) continue;
				if (object.canPlace(level, layer, x, y, 0, true, false) == null
						&& player.getPositionPoint().distance(x * 32.0 + 16.0, y * 32.0 + 16.0) <= 100.0) {
					best = new Point(x * 32 + 16, y * 32 + 16);
				}
			}
			return best;
		}
	}
}
