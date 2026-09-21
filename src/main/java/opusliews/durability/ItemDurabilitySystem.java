package opusliews.durability;

import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.ObjectLayerRegistry;
import necesse.entity.mobs.itemAttacker.ItemAttackSlot;
import necesse.gfx.Renderer;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.toolItem.ToolDamageItem;
import necesse.inventory.item.toolItem.ToolType;
import necesse.level.gameObject.GameObject;
import necesse.level.gameTile.GameTile;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import opusliews.logging.Logging;

import java.util.List;

public final class ItemDurabilitySystem {
	public static final String durabilityGndKey = "dynamicsettlementsDurability";

	private ItemDurabilitySystem() {
	}

	public static final class ToolDamageState {
		public final DurabilityAction damageAction;
		public final LevelObject targetObject;
		public final int objectLayerID;
		public final int originalObjectID;
		public final int tileX;
		public final int tileY;
		public final int originalTileID;

		public ToolDamageState(DurabilityAction damageAction, LevelObject targetObject, int objectLayerID, int originalObjectID, int tileX, int tileY, int originalTileID) {
			this.damageAction = damageAction;
			this.targetObject = targetObject;
			this.objectLayerID = objectLayerID;
			this.originalObjectID = originalObjectID;
			this.tileX = tileX;
			this.tileY = tileY;
			this.originalTileID = originalTileID;
		}
	}

	public static boolean isBreakable(InventoryItem item) {
		return item != null && ItemDurabilityRegistry.isBreakable(item.item);
	}

	public static int getMaxDurability(InventoryItem item) {
		return item == null ? 0 : ItemDurabilityRegistry.getMaxDurability(item.item);
	}

	public static int getDurability(InventoryItem item) {
		int maxDurability = getMaxDurability(item);
		if (maxDurability <= 0) return 0;

		GNDItemMap gndData = item.getGndData();
		if (!gndData.hasKey(durabilityGndKey)) return maxDurability;
		return Math.max(0, Math.min(maxDurability, gndData.getInt(durabilityGndKey, maxDurability)));
	}

	public static float getDurabilityRatio(InventoryItem item) {
		int maxDurability = getMaxDurability(item);
		return maxDurability <= 0 ? 1.0F : getDurability(item) / (float)maxDurability;
	}

	public static void setDurability(InventoryItem item, int durability) {
		if (!isBreakable(item)) return;

		int maxDurability = getMaxDurability(item);
		int clamped = Math.max(0, Math.min(maxDurability, durability));
		if (clamped >= maxDurability) {
			item.getGndData().clearItem(durabilityGndKey);
		} else {
			item.getGndData().setInt(durabilityGndKey, clamped);
		}
	}

	public static LevelObject getToolDamageTargetObject(ToolDamageItem toolItem, Level level, int priorityObjectLayerID, int tileX, int tileY, InventoryItem item) {
		if (toolItem == null || level == null || item == null) return null;

		ToolType toolType = toolItem.getToolType(item);
		if (toolType != ToolType.AXE && toolType != ToolType.PICKAXE && toolType != ToolType.ALL) return null;

		if (priorityObjectLayerID != -1) {
			int objectID = level.getObjectID(priorityObjectLayerID, tileX, tileY);
			if (objectID != 0) {
				GameObject object = level.getObject(priorityObjectLayerID, tileX, tileY);
				if (object.toolType != ToolType.UNBREAKABLE) return new LevelObject(level, priorityObjectLayerID, tileX, tileY);
			}
		}

		for (int layerID = ObjectLayerRegistry.getTotalLayers() - 1; layerID >= 0; layerID--) {
			int objectID = level.getObjectID(layerID, tileX, tileY);
			if (objectID == 0) continue;

			GameObject object = level.getObject(layerID, tileX, tileY);
			if (object.toolType != ToolType.UNBREAKABLE) return new LevelObject(level, layerID, tileX, tileY);
		}

		return null;
	}

	public static DurabilityAction getToolDamageAction(ToolDamageItem toolItem, Level level, int priorityObjectLayerID, int tileX, int tileY, InventoryItem item) {
		if (toolItem == null || level == null || item == null) return null;

		LevelObject targetObject = getToolDamageTargetObject(toolItem, level, priorityObjectLayerID, tileX, tileY, item);
		if (targetObject != null) {
			if (targetObject.object.attackThrough) {
				Logging.logMessage("[Durability] Ignoring attackThrough object=" + targetObject.object.getStringID()
						+ " tile=" + tileX + "," + tileY);
				return null;
			}
			return DurabilityAction.OBJECT_DAMAGE;
		}

		ToolType toolType = toolItem.getToolType(item);
		if (toolType == ToolType.SHOVEL || toolType == ToolType.ALL) {
			GameTile tile = level.getTile(tileX, tileY);
			if (tile.getID() != 0 && tile.canBeMined) return DurabilityAction.TILE_DAMAGE;
		}

		return null;
	}

	public static ToolDamageState captureToolDamageState(ToolDamageItem toolItem, Level level, int priorityObjectLayerID, int tileX, int tileY, InventoryItem item) {
		DurabilityAction action = getToolDamageAction(toolItem, level, priorityObjectLayerID, tileX, tileY, item);
		if (action == null) return null;

		if (action == DurabilityAction.OBJECT_DAMAGE) {
			LevelObject target = getToolDamageTargetObject(toolItem, level, priorityObjectLayerID, tileX, tileY, item);
			if (target == null || target.object.attackThrough) return null;
			return new ToolDamageState(action, target, target.layerID, target.object.getID(), tileX, tileY, -1);
		}

		return new ToolDamageState(action, null, -1, -1, tileX, tileY, level.getTileID(tileX, tileY));
	}

	public static boolean wasToolTargetDestroyed(Level level, ToolDamageState state) {
		if (level == null || state == null) return false;
		if (state.damageAction == DurabilityAction.OBJECT_DAMAGE) {
			return level.getObjectID(state.objectLayerID, state.tileX, state.tileY) != state.originalObjectID;
		}
		if (state.damageAction == DurabilityAction.TILE_DAMAGE) {
			return level.getTileID(state.tileX, state.tileY) != state.originalTileID;
		}
		return false;
	}

	public static InventoryItem applyActionAndGetItem(InventoryItem item, DurabilityAction action, DurabilityContext context) {
		if (!isBreakable(item)) return item;
		if (context != null && context.level != null && context.level.isClient()) return item;

		ItemDurabilityDefinition definition = ItemDurabilityRegistry.get(item.item);
		List<DurabilityLossRule> rules = definition.getRules(action);
		if (rules == null || rules.isEmpty()) {
			Logging.logMessage("[Durability] No rules for item=" + item.item.getStringID() + " action=" + action);
			return item;
		}

		int totalLoss = 0;
		for (DurabilityLossRule rule : rules) {
			if (rule.matches(context)) totalLoss += rule.rollLoss();
		}

		Logging.logMessage("[Durability] Applying action item=" + item.item.getStringID()
				+ " action=" + action
				+ " loss=" + totalLoss
				+ " durability=" + getDurability(item) + "/" + getMaxDurability(item));

		if (totalLoss <= 0) return item;
		return applyLossAndGetItem(item, totalLoss);
	}

	public static int applyAction(InventoryItem item, ItemAttackSlot slot, DurabilityAction action, DurabilityContext context) {
		if (!isBreakable(item)) return 0;
		int before = getDurability(item);
		InventoryItem result = applyActionAndGetItem(item, action, context);
		int after = result == null || result.item != item.item ? 0 : getDurability(result);
		if (slot != null && result != item) slot.setItem(result);
		if (slot != null) slot.markDirty();
		return Math.max(0, before - after);
	}

	public static int applyCustomAction(InventoryItem item, ItemAttackSlot slot, String customAction, DurabilityContext context) {
		DurabilityContext customContext = context == null
				? DurabilityContext.custom(null, null, customAction)
				: new DurabilityContext(context.level, context.player, context.targetMob, context.targetObject, context.recipe, customAction);
		return applyAction(item, slot, DurabilityAction.CUSTOM, customContext);
	}

	public static void applyLoss(InventoryItem item, ItemAttackSlot slot, int loss) {
		InventoryItem result = applyLossAndGetItem(item, loss);
		if (slot != null && result != item) slot.setItem(result);
		if (slot != null) slot.markDirty();
	}

	public static InventoryItem applyLossAndGetItem(InventoryItem item, int loss) {
		if (!isBreakable(item) || loss <= 0) return item;

		int before = getDurability(item);
		int remaining = before - loss;
		if (remaining > 0) {
			setDurability(item, remaining);
			Logging.logMessage("[Durability] Damaged item=" + item.item.getStringID() + " " + before + " -> " + remaining);
			return item;
		}

		ItemDurabilityDefinition definition = ItemDurabilityRegistry.get(item.item);
		Logging.logMessage("[Durability] Breaking item=" + item.item.getStringID() + " behavior=" + definition.getBreakBehavior());

		if (definition.getBreakBehavior() == ItemDurabilityDefinition.BreakBehavior.REPLACE) {
			Item brokenItem = ItemRegistry.getItem(definition.getBrokenItemStringID());
			if (brokenItem != null) {
				InventoryItem replacement = new InventoryItem(brokenItem, 1, item.isLocked());
				replacement.setNew(item.isNew());
				replacement.setGndData(item.getGndData());
				replacement.getGndData().clearItem(durabilityGndKey);
				Logging.logMessage("[Durability] Replaced broken item=" + item.item.getStringID() + " with=" + brokenItem.getStringID());
				return replacement;
			}
			Logging.logMessage("[Durability] Invalid broken replacement item=" + definition.getBrokenItemStringID() + "; destroying instead");
		}

		item.setAmount(0);
		item.getGndData().clearItem(durabilityGndKey);
		Logging.logMessage("[Durability] Destroyed broken item=" + item.item.getStringID());
		return null;
	}

	public static void drawDurabilityBar(InventoryItem item, int x, int y) {
		if (!isBreakable(item)) return;

		float ratio = getDurabilityRatio(item);
		if (ratio >= 1.0F) return;

		int outerX = x + 1;
		int outerY = y + 27;
		int outerWidth = 30;
		int outerHeight = 5;
		int barX = outerX + 1;
		int barY = outerY + 1;
		int barWidth = outerWidth - 2;
		int barHeight = outerHeight - 2;
		int filledWidth = Math.max(0, Math.min(barWidth, Math.round(barWidth * ratio)));
		int emptyWidth = barWidth - filledWidth;

		drawQuad(outerX, outerY, outerWidth, outerHeight, 0, 0, 0);
		if (filledWidth > 0) {
			int red = Math.round(255.0F * (1.0F - ratio));
			int green = Math.round(255.0F * ratio);
			drawQuad(barX, barY, filledWidth, barHeight, red, green, 0);
		}
		if (emptyWidth > 0) {
			drawQuad(barX + filledWidth, barY, emptyWidth, barHeight, 180, 180, 180);
		}
	}

	private static void drawQuad(int x, int y, int width, int height, int red, int green, int blue) {
		Renderer.initQuadDraw(width, height)
				.color(red / 255.0F, green / 255.0F, blue / 255.0F, 1.0F)
				.draw(x, y);
	}
}
