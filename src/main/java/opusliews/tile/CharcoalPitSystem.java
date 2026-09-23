package opusliews.tile;

import necesse.engine.registries.ObjectRegistry;
import necesse.engine.registries.TileRegistry;
import necesse.engine.network.packet.PacketPlayerInventorySlot;
import necesse.engine.util.GameMath;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.Inventory;
import necesse.inventory.InventoryItem;
import necesse.inventory.PlayerInventorySlot;
import necesse.inventory.item.TorchItem;
import necesse.inventory.item.toolItem.shovelToolItem.ShovelToolItem;
import necesse.level.maps.Level;
import opusliews.item.DirtPileItem;
import opusliews.item.FirestarterItem;
import opusliews.item.FireableMatItem;
import opusliews.deephole.DeepHoleSystem;
import opusliews.network.PacketCharcoalPitInteract;
import opusliews.object.TrapdoorObject;
import opusliews.object.HoleCaveLadderObject;
import opusliews.tile.CharcoalPitLevelData.StoredLog;
import opusliews.tile.CharcoalPitLevelData.StoredFiringItem;

import java.util.*;

public final class CharcoalPitSystem {
	private static final int requiredLogs = 32;
	private static final int requiredBrickFiringLogs = 16;
	private static final int interactRange = 100;

	private CharcoalPitSystem() {
	}

	public static boolean tryClientInteract(PlayerMob player, int levelX, int levelY) {
		Level level = player.getLevel();
		if (level == null || !level.isClient()) return false;

		int tileX = GameMath.getTileCoordinate(levelX);
		int tileY = GameMath.getTileCoordinate(levelY);
		if (!level.isTileWithinBounds(tileX, tileY) || !isPitTile(level, tileX, tileY)) return false;

		InventoryItem selected = player.getSelectedItem();
		int tileID = level.getTileID(tileX, tileY);
		boolean allowedAction = false;

		if (tileID == TileRegistry.getTileID(ShallowHoleTile.stringID)) {
			if (hasHoleCaveLadder(level, tileX, tileY)) return false;
			if (hasBlockingHoleObject(level, tileX, tileY)) return true;
			allowedAction = isDirtPile(selected) || isLog(selected) || isFireableItem(selected);
		} else if (tileID == TileRegistry.getTileID(DeepHoleTile.stringID)) {
			allowedAction = isDirtPile(selected);
		} else if (getFiringPitCount(tileID) > 0) {
			allowedAction = isShovel(selected) || isLog(selected) || isFireableItem(selected)
					|| isTorch(selected) || isFirestarter(selected);
		} else if (tileID == TileRegistry.getTileID(FiringPitLogTile.stringID)) {
			allowedAction = isShovel(selected) || isTorch(selected) || isFirestarter(selected);
		} else if (tileID == TileRegistry.getTileID(BurningFiringPitTile.stringID)) {
			return true;
		} else if (tileID == TileRegistry.getTileID(CharcoalPitTile.stringID)) {
			allowedAction = isDirtPile(selected) || isShovel(selected) || isTorch(selected);
		} else if (tileID == TileRegistry.getTileID(CoveredCharcoalPitTile.stringID)) {
			allowedAction = isShovel(selected) || isTorch(selected) || isFirestarter(selected);
		} else if (tileID == TileRegistry.getTileID(BurningCharcoalPitTile.stringID)) {
			return true;
		}

		if (allowedAction) level.getClient().network.sendPacket(new PacketCharcoalPitInteract(tileX, tileY));
		return true;
	}

	public static void tryServerInteract(PlayerMob player, int tileX, int tileY) {
		Level level = player.getLevel();
		if (level == null || !level.isServer() || !level.isTileWithinBounds(tileX, tileY) || level.isProtected(tileX, tileY)) return;

		InventoryItem selected = player.getSelectedItem();
		if (selected == null || !isWithinRange(player, tileX, tileY)) return;

		int tileID = level.getTileID(tileX, tileY);
		if (tileID == TileRegistry.getTileID(ShallowHoleTile.stringID)) {
			if (hasBlockingHoleObject(level, tileX, tileY)) return;

			if (isDirtPile(selected)) {
				fillHoleWithDirt(level, player, selected, tileX, tileY);
			} else if (isLog(selected)) {
				fillHoleWithLogs(level, player, selected, tileX, tileY);
			} else if (isFireableItem(selected)) {
				addFireableItem(level, player, selected, tileX, tileY);
			}
			return;
		}

		if (tileID == TileRegistry.getTileID(DeepHoleTile.stringID)) {
			if (isDirtPile(selected) && !DeepHoleSystem.isShaftTransitionAt(level, tileX, tileY)) {
				boolean returnedLadder = DeepHoleSystem.fillDeepHole(level, tileX, tileY);
				if (returnedLadder) dropItem(level, tileX, tileY, new InventoryItem(HoleCaveLadderObject.stringID, 1));
				if (level.getTileID(tileX, tileY) != TileRegistry.getTileID(DeepHoleTile.stringID)) selected.setAmount(selected.getAmount() - 1);
			}
			return;
		}

		int firingItemCount = getFiringPitCount(tileID);
		if (firingItemCount > 0) {
			if (isShovel(selected) && isShovelInRange(level, player, selected, tileX, tileY)) {
				extractFiringPit(level, tileX, tileY);
			} else if (isFireableItem(selected) && firingItemCount < FiringPitTile.maxItems
					&& CharcoalPitLevelData.get(level, true).getLogs(tileX, tileY).isEmpty()) {
				addFireableItem(level, player, selected, tileX, tileY);
			} else if (isLog(selected)) {
				addFiringLogs(level, player, selected, tileX, tileY);
			} else if ((isTorch(selected) && isTorchInRange(level, player, selected, tileX, tileY))
					|| (isFirestarter(selected) && isWithinRange(player, tileX, tileY))) {
				igniteFiringPit(level, tileX, tileY);
			}
			return;
		}

		if (tileID == TileRegistry.getTileID(FiringPitLogTile.stringID)) {
			if (isShovel(selected) && isShovelInRange(level, player, selected, tileX, tileY)) {
				extractFiringPit(level, tileX, tileY);
			} else if ((isTorch(selected) && isTorchInRange(level, player, selected, tileX, tileY))
					|| (isFirestarter(selected) && isWithinRange(player, tileX, tileY))) {
				igniteFiringPit(level, tileX, tileY);
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

		if (tileID == TileRegistry.getTileID(CoveredCharcoalPitTile.stringID)) {
			if (isShovel(selected) && isShovelInRange(level, player, selected, tileX, tileY)) {
				extractPit(level, tileX, tileY, true);
			} else if ((isTorch(selected) && isTorchInRange(level, player, selected, tileX, tileY))
					|| (isFirestarter(selected) && isWithinRange(player, tileX, tileY))) {
				ignitePit(level, tileX, tileY);
			}
		}
	}

	public static boolean hasTrapdoor(Level level, int tileX, int tileY) {
		if (level == null || !level.isTileWithinBounds(tileX, tileY)) {
			return false;
		}

		int objectID = level.getObjectID(tileX, tileY);
		return objectID == ObjectRegistry.getObjectID(TrapdoorObject.openStringID)
				|| objectID == ObjectRegistry.getObjectID(TrapdoorObject.closedStringID);
	}

	public static boolean hasHoleCaveLadder(Level level, int tileX, int tileY) {
		return level != null
				&& level.isTileWithinBounds(tileX, tileY)
				&& level.getObjectID(tileX, tileY) == ObjectRegistry.getObjectID(HoleCaveLadderObject.stringID);
	}

	public static boolean hasBlockingHoleObject(Level level, int tileX, int tileY) {
		return hasTrapdoor(level, tileX, tileY) || hasHoleCaveLadder(level, tileX, tileY);
	}

	public static boolean isBurningFiringPit(Level level, int tileX, int tileY) {
		return level != null
				&& level.isTileWithinBounds(tileX, tileY)
				&& level.getTileID(tileX, tileY) == TileRegistry.getTileID(BurningFiringPitTile.stringID);
	}

	public static boolean isPitTile(Level level, int tileX, int tileY) {
		if (level == null || !level.isTileWithinBounds(tileX, tileY)) return false;

		int tileID = level.getTileID(tileX, tileY);
		return tileID == TileRegistry.getTileID(ShallowHoleTile.stringID)
				|| tileID == TileRegistry.getTileID(DeepHoleTile.stringID)
				|| getFiringPitCount(tileID) > 0
				|| tileID == TileRegistry.getTileID(FiringPitLogTile.stringID)
				|| tileID == TileRegistry.getTileID(BurningFiringPitTile.stringID)
				|| tileID == TileRegistry.getTileID(CharcoalPitTile.stringID)
				|| tileID == TileRegistry.getTileID(CoveredCharcoalPitTile.stringID)
				|| tileID == TileRegistry.getTileID(BurningCharcoalPitTile.stringID);
	}

	private static int getFiringPitCount(int tileID) {
		for (int itemCount = 1; itemCount <= FiringPitTile.maxItems; itemCount++) {
			if (tileID == TileRegistry.getTileID(FiringPitTile.getStringID(itemCount))) return itemCount;
		}
		return 0;
	}

	private static void addFireableItem(Level level, PlayerMob player, InventoryItem selected, int tileX, int tileY) {
		if (!(selected.item instanceof FireableMatItem) || selected.getAmount() <= 0) return;

		CharcoalPitLevelData data = CharcoalPitLevelData.get(level, true);
		if (level.getTileID(tileX, tileY) == TileRegistry.getTileID(ShallowHoleTile.stringID)) {
			data.removeFiringItems(tileX, tileY);
			data.removeLogs(tileX, tileY);
			data.removeFiringBurn(tileX, tileY);
		}
		List<StoredFiringItem> items = data.getFiringItems(tileX, tileY);
		if (items.size() >= FiringPitTile.maxItems) return;

		data.clearPendingCleanup(tileX, tileY);
		items.add(new StoredFiringItem(selected.item.getStringID()));
		data.setFiringItems(tileX, tileY, items);

		PlayerInventorySlot selectedSlot = player.getSelectedItemSlot();
		selected.setAmount(selected.getAmount() - 1);
		if (selected.getAmount() <= 0) {
			selectedSlot.setItem(player.getInv(), null);
		}
		else {
			selectedSlot.markDirty(player.getInv());
		}

		setTile(level, tileX, tileY, TileRegistry.getTileID(FiringPitTile.getStringID(items.size())));
	}

	private static void addFiringLogs(Level level, PlayerMob player, InventoryItem selected, int tileX, int tileY) {
		CharcoalPitLevelData data = CharcoalPitLevelData.get(level, true);
		if (!data.getLogs(tileX, tileY).isEmpty()) return;

		int itemCount = getFiringPitCount(level.getTileID(tileX, tileY));
		if (itemCount <= 0) return;

		List<StoredLog> removedLogs = removeLogs(player, selected.item.getStringID(), requiredBrickFiringLogs);
		if (removedLogs == null) return;

		data.setLogs(tileX, tileY, removedLogs);
		setTile(level, tileX, tileY, TileRegistry.getTileID(FiringPitLogTile.stringID));
	}

	private static void extractFiringPit(Level level, int tileX, int tileY) {
		CharcoalPitLevelData data = CharcoalPitLevelData.get(level, false);
		List<StoredLog> logs = data == null ? new ArrayList<>() : data.removeLogs(tileX, tileY);
		List<StoredFiringItem> items = data == null ? new ArrayList<>() : data.removeFiringItems(tileX, tileY);
		if (data != null) data.removeFiringBurn(tileX, tileY);

		setTile(level, tileX, tileY, TileRegistry.getTileID(ShallowHoleTile.stringID));
		for (StoredFiringItem item : items) dropItem(level, tileX, tileY, new InventoryItem(item.itemStringID, 1));
		for (StoredLog log : logs) dropItem(level, tileX, tileY, new InventoryItem(log.itemStringID, log.amount));
	}

	private static void igniteFiringPit(Level level, int tileX, int tileY) {
		CharcoalPitLevelData data = CharcoalPitLevelData.get(level, false);
		if (data == null || data.getFiringItems(tileX, tileY).isEmpty()) return;

		int storedLogAmount = 0;
		for (StoredLog log : data.getLogs(tileX, tileY)) storedLogAmount += Math.max(0, log.amount);
		if (storedLogAmount < requiredBrickFiringLogs) return;

		long fullDayDuration = (long)level.getWorldEntity().getDayTimeMax() * 1000L;
		data.igniteFiringPit(tileX, tileY, level.getWorldEntity().getWorldTime() + fullDayDuration);
	}


	private static void fillHoleWithDirt(Level level, PlayerMob player, InventoryItem selected, int tileX, int tileY) {
		selected.setAmount(selected.getAmount() - 1);
		setTile(level, tileX, tileY, ShallowHoleSystem.getFillTileID(level, tileX, tileY));
		CharcoalPitLevelData data = CharcoalPitLevelData.get(level, false);
		if (data != null) {
			data.removeLogs(tileX, tileY);
			data.removeBurn(tileX, tileY);
			data.removeFiringBurn(tileX, tileY);
			data.removeFiringItems(tileX, tileY);
			data.clearPendingCleanup(tileX, tileY);
		}
	}

	private static void fillHoleWithLogs(Level level, PlayerMob player, InventoryItem selected, int tileX, int tileY) {
		List<StoredLog> removedLogs = removeLogs(player, selected.item.getStringID(), requiredLogs);
		if (removedLogs == null) {
			return;
		}

		CharcoalPitLevelData data = CharcoalPitLevelData.get(level, true);
		data.clearPendingCleanup(tileX, tileY);
		data.setLogs(tileX, tileY, removedLogs);
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
		if (data != null) {
			data.removeBurn(tileX, tileY);
		}

		setTile(level, tileX, tileY, TileRegistry.getTileID(ShallowHoleTile.stringID));
		for (StoredLog log : logs) {
			dropItem(level, tileX, tileY, new InventoryItem(log.itemStringID, log.amount));
		}

		if (returnDirt) {
			dropItem(level, tileX, tileY, new InventoryItem(DirtPileItem.stringID, 1));
		}
	}

	private static void ignitePit(Level level, int tileX, int tileY) {
		CharcoalPitLevelData data = CharcoalPitLevelData.get(level, false);
		if (data == null || data.getLogs(tileX, tileY).isEmpty()) {
			return;
		}

		long fullDayDuration = (long)level.getWorldEntity().getDayTimeMax() * 1000L;
		long burnEndWorldTime = level.getWorldEntity().getWorldTime() + fullDayDuration;
		data.igniteCharcoalPit(tileX, tileY, burnEndWorldTime);
	}

	private static List<StoredLog> removeLogs(PlayerMob player, String preferredItemStringID, int amount) {
		Inventory inventory = player.getInv().main;
		if (countLogs(inventory) < amount) return null;

		Set<String> removalOrder = new LinkedHashSet<>();
		removalOrder.add(preferredItemStringID);
		for (int slot = 0; slot < inventory.getSize(); slot++) {
			InventoryItem inventoryItem = inventory.getItem(slot);
			if (isLog(inventoryItem)) removalOrder.add(inventoryItem.item.getStringID());
		}

		Map<String, Integer> removedByType = new LinkedHashMap<>();
		Set<Integer> changedSlots = new LinkedHashSet<>();
		int remaining = amount;
		for (String itemStringID : removalOrder) {
			remaining = removeLogTypeFromSlots(inventory, itemStringID, remaining, removedByType, changedSlots);
			if (remaining == 0) break;
		}

		if (player.isServerClient()) {
			for (int slot : changedSlots) {
				PlayerInventorySlot inventorySlot = new PlayerInventorySlot(player.getInv().main, slot);
				player.getServerClient().sendPacket(new PacketPlayerInventorySlot(player.getServerClient(), inventorySlot));
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
			Map<String, Integer> removedByType,
			Set<Integer> changedSlots
	) {
		int remaining = amount;
		for (int slot = 0; slot < inventory.getSize() && remaining > 0; slot++) {
			InventoryItem inventoryItem = inventory.getItem(slot);
			if (inventoryItem == null || !itemStringID.equals(inventoryItem.item.getStringID())) continue;

			int take = Math.min(remaining, inventoryItem.getAmount());
			inventory.setAmount(slot, inventoryItem.getAmount() - take);
			changedSlots.add(slot);
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

	private static boolean isFireableItem(InventoryItem item) {
		return item != null && item.item instanceof FireableMatItem;
	}

	private static boolean isDirtPile(InventoryItem item) {
		return item != null && DirtPileItem.stringID.equals(item.item.getStringID());
	}

	private static boolean isShovel(InventoryItem item) {
		return item != null && item.item instanceof ShovelToolItem;
	}

	private static boolean isTorch(InventoryItem item) {
		return item != null && item.item instanceof TorchItem;
	}

	private static boolean isFirestarter(InventoryItem item) {
		return item != null && FirestarterItem.stringID.equals(item.item.getStringID());
	}

	private static boolean isWithinRange(PlayerMob player, int tileX, int tileY) {
		return player.getPositionPoint().distance(tileX * 32.0 + 16.0, tileY * 32.0 + 16.0) <= interactRange;
	}

	private static boolean isShovelInRange(Level level, PlayerMob player, InventoryItem item, int tileX, int tileY) {
		ShovelToolItem shovel = (ShovelToolItem)item.item;
		return shovel.isTileInRange(level, tileX, tileY, player, null, item);
	}

	private static boolean isTorchInRange(Level level, PlayerMob player, InventoryItem item, int tileX, int tileY) {
		TorchItem torch = (TorchItem)item.item;
		int range = torch.getTorchPlaceRange(level, item, player);
		return player.getPositionPoint().distance(tileX * 32.0 + 16.0, tileY * 32.0 + 16.0) <= range;
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
