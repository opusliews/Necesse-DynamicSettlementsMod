package opusliews.tile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import necesse.engine.registries.TileRegistry;
import necesse.engine.util.GameMath;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.Inventory;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.toolItem.shovelToolItem.ShovelToolItem;
import necesse.level.maps.Level;
import opusliews.item.DirtPileItem;
import opusliews.network.PacketCharcoalPitInteract;
import opusliews.tile.CharcoalPitLevelData.StoredLog;

public final class CharcoalPitSystem {
	private static final int requiredLogs = 32;
	private static final int interactRange = 100;

	private CharcoalPitSystem() {
	}

	public static boolean tryClientInteract(PlayerMob player, int levelX, int levelY) {
		Level level = player.getLevel();
		if (level == null || !level.isClient()) {
			return false;
		}

		int tileX = GameMath.getTileCoordinate(levelX);
		int tileY = GameMath.getTileCoordinate(levelY);
		if (!level.isTileWithinBounds(tileX, tileY) || !isPitTile(level, tileX, tileY)) {
			return false;
		}

		InventoryItem selected = player.getSelectedItem();
		int tileID = level.getTileID(tileX, tileY);
		boolean allowedAction = false;

		if (tileID == TileRegistry.getTileID(ShallowHoleTile.stringID)) {
			allowedAction = isDirtPile(selected) || isLog(selected);
		} else if (tileID == TileRegistry.getTileID(CharcoalPitTile.stringID)) {
			allowedAction = isDirtPile(selected) || isShovel(selected);
		} else if (tileID == TileRegistry.getTileID(CoveredCharcoalPitTile.stringID)) {
			allowedAction = isShovel(selected);
		}

		if (allowedAction) {
			level.getClient().network.sendPacket(new PacketCharcoalPitInteract(tileX, tileY));
		}

		// Always consume right click on pit tiles, even when the selected item is not allowed.
		return true;
	}

	public static void tryServerInteract(PlayerMob player, int tileX, int tileY) {
		Level level = player.getLevel();
		if (level == null || !level.isServer() || !level.isTileWithinBounds(tileX, tileY) || level.isProtected(tileX, tileY)) {
			return;
		}

		InventoryItem selected = player.getSelectedItem();
		if (selected == null || !isWithinRange(player, tileX, tileY)) {
			return;
		}

		int tileID = level.getTileID(tileX, tileY);
		if (tileID == TileRegistry.getTileID(ShallowHoleTile.stringID)) {
			if (isDirtPile(selected)) {
				fillHoleWithDirt(level, player, selected, tileX, tileY);
			} else if (isLog(selected)) {
				fillHoleWithLogs(level, player, selected, tileX, tileY);
			}
			return;
		}

		if (tileID == TileRegistry.getTileID(CharcoalPitTile.stringID)) {
			if (isShovel(selected) && isShovelInRange(level, player, selected, tileX, tileY)) {
				extractPit(level, tileX, tileY, false);
			} else if (isDirtPile(selected)) {
				coverPit(level, selected, tileX, tileY);
			}
			return;
		}

		if (tileID == TileRegistry.getTileID(CoveredCharcoalPitTile.stringID)
				&& isShovel(selected)
				&& isShovelInRange(level, player, selected, tileX, tileY)) {
			extractPit(level, tileX, tileY, true);
		}
	}

	public static boolean isPitTile(Level level, int tileX, int tileY) {
		if (level == null || !level.isTileWithinBounds(tileX, tileY)) {
			return false;
		}

		int tileID = level.getTileID(tileX, tileY);
		return tileID == TileRegistry.getTileID(ShallowHoleTile.stringID)
				|| tileID == TileRegistry.getTileID(CharcoalPitTile.stringID)
				|| tileID == TileRegistry.getTileID(CoveredCharcoalPitTile.stringID);
	}

	private static void fillHoleWithDirt(Level level, PlayerMob player, InventoryItem selected, int tileX, int tileY) {
		selected.setAmount(selected.getAmount() - 1);
		setTile(level, tileX, tileY, TileRegistry.dirtID);
		CharcoalPitLevelData data = CharcoalPitLevelData.get(level, false);
		if (data != null) {
			data.removeLogs(tileX, tileY);
		}
	}

	private static void fillHoleWithLogs(Level level, PlayerMob player, InventoryItem selected, int tileX, int tileY) {
		List<StoredLog> removedLogs = removeLogs(player, selected.item.getStringID(), requiredLogs);
		if (removedLogs == null) {
			return;
		}

		CharcoalPitLevelData.get(level, true).setLogs(tileX, tileY, removedLogs);
		setTile(level, tileX, tileY, TileRegistry.getTileID(CharcoalPitTile.stringID));
	}

	private static void coverPit(Level level, InventoryItem selected, int tileX, int tileY) {
		CharcoalPitLevelData data = CharcoalPitLevelData.get(level, false);
		if (data == null || data.getLogs(tileX, tileY).isEmpty()) {
			return;
		}

		selected.setAmount(selected.getAmount() - 1);
		setTile(level, tileX, tileY, TileRegistry.getTileID(CoveredCharcoalPitTile.stringID));
	}

	private static void extractPit(Level level, int tileX, int tileY, boolean returnDirt) {
		CharcoalPitLevelData data = CharcoalPitLevelData.get(level, false);
		List<StoredLog> logs = data == null ? new ArrayList<>() : data.removeLogs(tileX, tileY);

		setTile(level, tileX, tileY, TileRegistry.getTileID(ShallowHoleTile.stringID));
		for (StoredLog log : logs) {
			dropItem(level, tileX, tileY, new InventoryItem(log.itemStringID, log.amount));
		}

		if (returnDirt) {
			dropItem(level, tileX, tileY, new InventoryItem(DirtPileItem.stringID, 1));
		}
	}

	private static List<StoredLog> removeLogs(PlayerMob player, String preferredItemStringID, int amount) {
		Inventory inventory = player.getInv().main;
		if (countLogs(inventory) < amount) {
			return null;
		}

		Set<String> removalOrder = new LinkedHashSet<>();
		removalOrder.add(preferredItemStringID);
		for (int slot = 0; slot < inventory.getSize(); slot++) {
			InventoryItem inventoryItem = inventory.getItem(slot);
			if (isLog(inventoryItem)) {
				removalOrder.add(inventoryItem.item.getStringID());
			}
		}

		Map<String, Integer> removedByType = new LinkedHashMap<>();
		int remaining = amount;
		for (String itemStringID : removalOrder) {
			remaining = removeLogTypeFromSlots(inventory, itemStringID, remaining, removedByType);
			if (remaining == 0) {
				break;
			}
		}

		List<StoredLog> removedLogs = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : removedByType.entrySet()) {
			removedLogs.add(new StoredLog(entry.getKey(), entry.getValue()));
		}
		return removedLogs;
	}

	private static int removeLogTypeFromSlots(
			Inventory inventory,
			String itemStringID,
			int amount,
			Map<String, Integer> removedByType
	) {
		int remaining = amount;
		for (int slot = 0; slot < inventory.getSize() && remaining > 0; slot++) {
			InventoryItem inventoryItem = inventory.getItem(slot);
			if (inventoryItem == null || !itemStringID.equals(inventoryItem.item.getStringID())) {
				continue;
			}

			int take = Math.min(remaining, inventoryItem.getAmount());
			inventory.setAmount(slot, inventoryItem.getAmount() - take);
			removedByType.put(itemStringID, removedByType.getOrDefault(itemStringID, 0) + take);
			remaining -= take;
		}
		return remaining;
	}

	private static int countLogs(Inventory inventory) {
		int amount = 0;
		for (int slot = 0; slot < inventory.getSize(); slot++) {
			InventoryItem inventoryItem = inventory.getItem(slot);
			if (isLog(inventoryItem)) {
				amount += inventoryItem.getAmount();
			}
		}
		return amount;
	}

	private static boolean isLog(InventoryItem item) {
		return item != null && item.item.isGlobalIngredient("anylog");
	}

	private static boolean isDirtPile(InventoryItem item) {
		return item != null && DirtPileItem.stringID.equals(item.item.getStringID());
	}

	private static boolean isShovel(InventoryItem item) {
		return item != null && item.item instanceof ShovelToolItem;
	}

	private static boolean isWithinRange(PlayerMob player, int tileX, int tileY) {
		return player.getPositionPoint().distance(tileX * 32.0 + 16.0, tileY * 32.0 + 16.0) <= interactRange;
	}

	private static boolean isShovelInRange(Level level, PlayerMob player, InventoryItem item, int tileX, int tileY) {
		ShovelToolItem shovel = (ShovelToolItem)item.item;
		return shovel.isTileInRange(level, tileX, tileY, player, null, item);
	}

	private static void setTile(Level level, int tileX, int tileY, int tileID) {
		level.setTile(tileX, tileY, tileID);
		level.sendTileUpdatePacket(tileX, tileY);
		level.getLevelTile(tileX, tileY).checkAround();
		level.getLevelObject(tileX, tileY).checkAround();
	}

	private static void dropItem(Level level, int tileX, int tileY, InventoryItem item) {
		level.entityManager.pickups.add(item.getPickupEntity(level, tileX * 32.0F + 16.0F, tileY * 32.0F + 16.0F));
	}
}
