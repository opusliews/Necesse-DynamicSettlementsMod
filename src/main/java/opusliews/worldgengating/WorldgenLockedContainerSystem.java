package opusliews.worldgengating;

import java.util.ArrayList;
import necesse.engine.localization.Localization;
import necesse.engine.network.client.Client;
import necesse.engine.network.gameNetworkData.GNDItem;
import necesse.engine.network.gameNetworkData.GNDItemInventory;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.JournalChallengeRegistry;
import necesse.engine.sound.SoundEffect;
import necesse.engine.sound.SoundManager;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.entity.objectEntity.interfaces.OEInventory;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.GameResources;
import necesse.gfx.gameTooltips.GameTooltipManager;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.gfx.gameTooltips.StringTooltips;
import necesse.gfx.gameTooltips.TooltipLocation;
import necesse.inventory.Inventory;
import necesse.inventory.InventoryItem;
import necesse.inventory.PlayerInventorySlot;
import necesse.inventory.item.Item;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import necesse.level.maps.hudManager.floatText.ChatBubbleText;
import opusliews.journal.GuideJournalRegistry;
import opusliews.logging.Logging;

public final class WorldgenLockedContainerSystem {
	public static final String discoveryChallengeStringID = "ds_lockedcontainers_discovered";

	private WorldgenLockedContainerSystem() {
	}

	public static boolean handleInteract(LevelObject levelObject, PlayerMob player) {
		if (levelObject == null || player == null) return false;
		Level level = levelObject.level;
		String objectID = levelObject.object == null ? "null" : levelObject.object.getStringID();
		Logging.logMessage("[WorldgenGatingDebug] INTERACT hook side=" + side(level)
				+ " level=" + level.getIdentifier()
				+ " object=" + objectID
				+ " layer=" + levelObject.layerID
				+ " pos=" + levelObject.tileX + "," + levelObject.tileY
				+ " playerPlaced=" + level.objectLayer.isPlayerPlaced(levelObject.layerID, levelObject.tileX, levelObject.tileY));

		WorldgenGatingLevelData data = WorldgenGatingLevelData.get(level, true);
		if (data == null) {
			Logging.logMessage("[WorldgenGatingDebug] INTERACT PASS reason=no-level-data side=" + side(level) + " object=" + objectID
					+ " pos=" + levelObject.tileX + "," + levelObject.tileY);
			return false;
		}

		WorldgenGatingLevelData.Entry entry = data.getOrClassifyEntry(levelObject.layerID, levelObject.tileX, levelObject.tileY);
		if (entry == null) {
			Logging.logMessage("[WorldgenGatingDebug] INTERACT PASS reason=no-entry side=" + side(level) + " object=" + objectID
					+ " pos=" + levelObject.tileX + "," + levelObject.tileY);
			return false;
		}
		Logging.logMessage("[WorldgenGatingDebug] INTERACT entry side=" + side(level)
				+ " object=" + objectID
				+ " pos=" + levelObject.tileX + "," + levelObject.tileY
				+ " type=" + entry.type
				+ " tier=" + entry.tier
				+ " requiredToolTier=" + entry.tier.toolTier
				+ " active=" + entry.active);

		if (!entry.active) {
			Logging.logMessage("[WorldgenGatingDebug] INTERACT PASS reason=inactive side=" + side(level) + " object=" + objectID
					+ " pos=" + levelObject.tileX + "," + levelObject.tileY);
			return false;
		}

		if (entry.type == WorldgenGatingData.NaturalType.LOCKED_CONTAINER) {
			boolean hasHeldKey = hasHeldMatchingKey(player, entry.tier);

			if (level.isClient()) {
				Logging.logMessage("[WorldgenGatingDebug] INTERACT BLOCK client locked-container object=" + objectID
						+ " pos=" + levelObject.tileX + "," + levelObject.tileY
						+ " hasHeldMatchingKey=" + hasHeldKey);

				if (!hasHeldKey) {
					playLockedSound(level, levelObject.tileX, levelObject.tileY);
					level.hudManager.addElement(new ChatBubbleText(
							player,
							Localization.translate("worldgengating", "chestlocked")
					));
				}

				return true;
			}

			ServerClient client = player.getServerClient();
			discover(client);
			Logging.logMessage("[WorldgenGatingDebug] INTERACT SERVER locked-container object=" + objectID
					+ " pos=" + levelObject.tileX + "," + levelObject.tileY
					+ " tier=" + entry.tier + " hasHeldMatchingKey=" + hasHeldKey);
			if (!hasHeldKey || !consumeHeldMatchingKey(player, entry.tier)) {
				Logging.logMessage("[WorldgenGatingDebug] INTERACT BLOCK server locked-container reason=no-held-key object=" + objectID
						+ " pos=" + levelObject.tileX + "," + levelObject.tileY);
				return true;
			}

			data.setEntryActive(entry.objectLayerID, entry.tileX, entry.tileY, false, true);
			Logging.logMessage("[WorldgenGatingDebug] INTERACT UNLOCK server object=" + objectID + " pos=" + levelObject.tileX + "," + levelObject.tileY);
			levelObject.object.interact(level, levelObject.tileX, levelObject.tileY, player);
			return true;
		}

		if (entry.type == WorldgenGatingData.NaturalType.TIED_SACK) {
			Logging.logMessage("[WorldgenGatingDebug] INTERACT BLOCK tied-sack side=" + side(level)
					+ " object=" + objectID + " pos=" + levelObject.tileX + "," + levelObject.tileY);
			if (level.isClient()) {
				level.hudManager.addElement(new ChatBubbleText(
						player,
						Localization.translate("worldgengating", "sacktied")
				));
				playLockedSound(level, levelObject.tileX, levelObject.tileY);
			}
			return true;
		}

		if (entry.type == WorldgenGatingData.NaturalType.CRAFTING_STATION) {
			WorldgenStationProgressionSystem.StationRequirement requirement =
					WorldgenStationProgressionSystem.getNaturalStationRequirement(objectID);
			boolean allowed = requirement != null && WorldgenStationProgressionSystem.canUseNaturalStation(player, objectID);

			if (level.isClient()) {
				if (!allowed) {
					level.hudManager.addElement(new ChatBubbleText(
							player,
							Localization.translate("worldgengating", "stationunknown")
					));
				}
				return !allowed;
			}

			Logging.logMessage("[WorldgenGatingDebug] STATION INTERACT server object=" + objectID
					+ " pos=" + levelObject.tileX + "," + levelObject.tileY
					+ " family=" + (requirement == null ? "null" : requirement.family)
					+ " stationTier=" + (requirement == null ? -1 : requirement.tier)
					+ " craftedTier=" + (requirement == null ? -1 : WorldgenStationProgressionSystem.getHighestCraftedTier(player, requirement.family))
					+ " allowed=" + allowed);
			return !allowed;
		}

		Logging.logMessage("[WorldgenGatingDebug] INTERACT PASS reason=non-interaction-gated-type side=" + side(level)
				+ " object=" + objectID + " type=" + entry.type + " pos=" + levelObject.tileX + "," + levelObject.tileY);
		return false;
	}

	public static int handleDamage(Level level, int objectLayerID, int tileX, int tileY, int damage, float toolTier, ServerClient client) {
		String objectID = "null";
		if (level != null) {
			try {
				objectID = level.getObject(objectLayerID, tileX, tileY).getStringID();
			} catch (Exception ignored) {
			}
		}
		Logging.logMessage("[WorldgenGatingDebug] DAMAGE hook side=" + side(level)
				+ " level=" + (level == null ? "null" : level.getIdentifier())
				+ " object=" + objectID
				+ " layer=" + objectLayerID
				+ " pos=" + tileX + "," + tileY
				+ " incomingDamage=" + damage
				+ " toolTier=" + toolTier
				+ " client=" + (client == null ? "null" : client.getName()));

		if (damage <= 0 || level == null) {
			Logging.logMessage("[WorldgenGatingDebug] DAMAGE PASS reason=invalid-input object=" + objectID + " damage=" + damage);
			return damage;
		}
		WorldgenGatingLevelData data = WorldgenGatingLevelData.get(level, true);
		if (data == null) {
			Logging.logMessage("[WorldgenGatingDebug] DAMAGE PASS reason=no-level-data side=" + side(level)
					+ " object=" + objectID + " pos=" + tileX + "," + tileY);
			return damage;
		}

		WorldgenGatingLevelData.Entry entry = data.getOrClassifyEntry(objectLayerID, tileX, tileY);
		if (entry == null) {
			Logging.logMessage("[WorldgenGatingDebug] DAMAGE PASS reason=no-entry side=" + side(level)
					+ " object=" + objectID + " pos=" + tileX + "," + tileY
					+ " playerPlaced=" + level.objectLayer.isPlayerPlaced(objectLayerID, tileX, tileY));
			return damage;
		}
		Logging.logMessage("[WorldgenGatingDebug] DAMAGE entry side=" + side(level)
				+ " object=" + objectID
				+ " pos=" + tileX + "," + tileY
				+ " type=" + entry.type
				+ " tier=" + entry.tier
				+ " requiredToolTier=" + entry.tier.toolTier
				+ " active=" + entry.active
				+ " toolTier=" + toolTier);

		if (!entry.active) {
			Logging.logMessage("[WorldgenGatingDebug] DAMAGE PASS reason=inactive object=" + objectID + " pos=" + tileX + "," + tileY);
			return damage;
		}

		if (entry.type == WorldgenGatingData.NaturalType.LOCKED_CONTAINER && client != null) {
			discover(client);
		}

		if (toolTier < entry.tier.toolTier) {
			Logging.logMessage("[WorldgenGatingDebug] DAMAGE BLOCK object=" + objectID
					+ " pos=" + tileX + "," + tileY
					+ " toolTier=" + toolTier + " required=" + entry.tier.toolTier);
			return 0;
		}

		if (entry.type == WorldgenGatingData.NaturalType.TIED_SACK) {
			Logging.logMessage("[WorldgenGatingDebug] DAMAGE CUT-TIE object=" + objectID
					+ " pos=" + tileX + "," + tileY + " toolTier=" + toolTier);
			if (level.isServer()) {
				spillInventory(level, tileX, tileY);
				data.setEntryActive(objectLayerID, tileX, tileY, false, true);
			}
			return 0;
		}

		Logging.logMessage("[WorldgenGatingDebug] DAMAGE ALLOW object=" + objectID
				+ " pos=" + tileX + "," + tileY
				+ " toolTier=" + toolTier + " required=" + entry.tier.toolTier
				+ " damage=" + damage);
		return damage;
	}

	public static boolean hasObtainedAnyWorldgenKey(Client client) {
		if (client == null) return false;

		return client.characterStats.items_obtained.isItemObtained("demonickey")
				|| client.characterStats.items_obtained.isItemObtained("runickey")
				|| client.characterStats.items_obtained.isItemObtained("ivykey")
				|| client.characterStats.items_obtained.isItemObtained("quartzkey")
				|| client.characterStats.items_obtained.isItemObtained("tungstenkey")
				|| client.characterStats.items_obtained.isItemObtained("glacialkey")
				|| client.characterStats.items_obtained.isItemObtained("dryadkey")
				|| client.characterStats.items_obtained.isItemObtained("myceliumkey")
				|| client.characterStats.items_obtained.isItemObtained("ancientfossilkey");
	}

	public static void addHoverTooltip(Level level, int objectLayerID, int tileX, int tileY) {
		if (level == null || !level.isClient()) return;
		WorldgenGatingLevelData data = WorldgenGatingLevelData.get(level, true);
		if (data == null) return;

		WorldgenGatingLevelData.Entry entry = data.getOrClassifyEntry(objectLayerID, tileX, tileY);
		if (entry == null || !entry.active) return;

		if (entry.type == WorldgenGatingData.NaturalType.LOCKED_CONTAINER) {
			String tierName = Localization.translate("worldgengating", entry.tier.getTierLocalizationKey());
			String keyName = ItemRegistry.getDisplayName(ItemRegistry.getItemID(entry.tier.keyStringID));
			if ("trialentrance".equals(entry.objectStringID)) {
				GameTooltipManager.addTooltip(new StringTooltips(
						Localization.translate("worldgengating", "lockedhint1"),
						Localization.translate("worldgengating", "lockedhint3", "key", keyName)
				), TooltipLocation.INTERACT_FOCUS);
			}
			else {
				GameTooltipManager.addTooltip(new StringTooltips(
						Localization.translate("worldgengating", "lockedhint1"),
						Localization.translate("worldgengating", "lockedhint2", "tier", tierName),
						Localization.translate("worldgengating", "lockedhint3", "key", keyName)
				), TooltipLocation.INTERACT_FOCUS);
			}
		}
		else if (entry.type == WorldgenGatingData.NaturalType.TIED_SACK) {
			String tierName = Localization.translate("worldgengating", entry.tier.getTierLocalizationKey());
			GameTooltipManager.addTooltip(new StringTooltips(
					Localization.translate("worldgengating", "tiedhint1"),
					Localization.translate("worldgengating", "tiedhint2", "tier", tierName),
					Localization.translate("worldgengating", "tiedhint3")
			), TooltipLocation.INTERACT_FOCUS);
		}
	}

	public static boolean blocksSettlementStorage(Level level, int tileX, int tileY) {
		WorldgenGatingLevelData data = WorldgenGatingLevelData.get(level, true);
		if (data == null) return false;
		WorldgenGatingLevelData.Entry entry = data.getOrClassifyEntry(0, tileX, tileY);
		if (entry == null || !entry.active) return false;
		return entry.type == WorldgenGatingData.NaturalType.LOCKED_CONTAINER
				|| entry.type == WorldgenGatingData.NaturalType.TIED_SACK;
	}

	public static ArrayList<InventoryItem> packageLockedContainerDrop(GameObject object, Level level, int objectLayerID, int tileX, int tileY, String purpose, ArrayList<InventoryItem> originalDrops) {
		if (object == null || level == null || !"onDestroyed".equals(purpose)) return originalDrops;
		WorldgenGatingLevelData data = WorldgenGatingLevelData.get(level, true);
		if (data == null) return originalDrops;

		WorldgenGatingLevelData.Entry entry = data.getEntry(objectLayerID, tileX, tileY);
		if (entry == null || !entry.active || entry.type != WorldgenGatingData.NaturalType.LOCKED_CONTAINER) return originalDrops;

		ObjectEntity entity = level.entityManager.getObjectEntity(tileX, tileY);
		if (entity == null || !entity.implementsOEInventory()) {
			Logging.logMessage("[WorldgenGatingDebug] PACKAGE DROP skipped reason=no-inventory object=" + object.getStringID()
					+ " pos=" + tileX + "," + tileY);
			return originalDrops;
		}

		Inventory sourceInventory = ((OEInventory)entity).getInventory();
		Inventory storedInventory = Inventory.getInventory(sourceInventory.getContentPacket());
		InventoryItem packed = new InventoryItem(object.getObjectItem(), 1);
		packed.getGndData().setBoolean(WorldgenGatingData.naturalKey, true);
		packed.getGndData().setString(WorldgenGatingData.naturalTypeKey, WorldgenGatingData.NaturalType.LOCKED_CONTAINER.name());
		packed.getGndData().setString(WorldgenGatingData.tierKey, entry.tier.name());
		packed.getGndData().setBoolean(WorldgenGatingData.lockedKey, true);
		packed.getGndData().setItem(WorldgenGatingData.storedInventoryKey, new GNDItemInventory(storedInventory));

		ArrayList<InventoryItem> result = new ArrayList<>();
		result.add(packed);
		Logging.logMessage("[WorldgenGatingDebug] PACKAGE DROP object=" + object.getStringID()
				+ " pos=" + tileX + "," + tileY + " tier=" + entry.tier + " items=" + countStoredItems(storedInventory));
		return result;
	}

	public static ArrayList<InventoryItem> suppressLockedContainerEntityDrops(Level level, int objectLayerID, int tileX, int tileY, String purpose, ArrayList<InventoryItem> originalDrops) {
		if (level == null || !"onDestroyed".equals(purpose)) return originalDrops;
		WorldgenGatingLevelData data = WorldgenGatingLevelData.get(level, true);
		if (data == null) return originalDrops;
		WorldgenGatingLevelData.Entry entry = data.getEntry(objectLayerID, tileX, tileY);
		if (entry == null || !entry.active || entry.type != WorldgenGatingData.NaturalType.LOCKED_CONTAINER) return originalDrops;

		ObjectEntity entity = level.entityManager.getObjectEntity(tileX, tileY);
		if (entity == null || !entity.implementsOEInventory()) return originalDrops;
		Logging.logMessage("[WorldgenGatingDebug] SUPPRESS CONTENT DROPS pos=" + tileX + "," + tileY + " count=" + originalDrops.size());
		return new ArrayList<>();
	}

	public static void restorePlacedLockedContainer(Level level, int objectLayerID, int tileX, int tileY, GameObject object, InventoryItem item) {
		if (level == null || object == null || item == null || !level.isServer() || !isPackedLockedContainer(item)) return;
		WorldgenLootTier tier = getPackedTier(item);
		if (tier == null) return;

		GNDItem stored = item.getGndData().getItem(WorldgenGatingData.storedInventoryKey);
		if (!(stored instanceof GNDItemInventory)) return;
		ObjectEntity entity = level.entityManager.getObjectEntity(tileX, tileY);
		if (entity == null || !entity.implementsOEInventory()) return;

		Inventory restored = ((GNDItemInventory)stored).inventory;
		((OEInventory)entity).getInventory().override(restored, true, true);
		WorldgenGatingLevelData data = WorldgenGatingLevelData.get(level, true);
		data.markCarriedLocked(objectLayerID, tileX, tileY, object.getStringID(), tier, true);
		Logging.logMessage("[WorldgenGatingDebug] RESTORE PLACED LOCKED object=" + object.getStringID()
				+ " pos=" + tileX + "," + tileY + " tier=" + tier + " items=" + countStoredItems(restored));
	}

	public static boolean isPackedLockedContainer(InventoryItem item) {
		if (item == null) return false;
		return item.getGndData().getBoolean(WorldgenGatingData.lockedKey)
				&& WorldgenGatingData.NaturalType.LOCKED_CONTAINER.name().equals(item.getGndData().getString(WorldgenGatingData.naturalTypeKey, null))
				&& item.getGndData().getItem(WorldgenGatingData.storedInventoryKey) instanceof GNDItemInventory;
	}

	public static WorldgenLootTier getPackedTier(InventoryItem item) {
		if (!isPackedLockedContainer(item)) return null;
		String tierName = item.getGndData().getString(WorldgenGatingData.tierKey, null);
		if (tierName == null) return null;
		try {
			return WorldgenLootTier.valueOf(tierName);
		}
		catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	public static void addPackedItemTooltips(InventoryItem item, ListGameTooltips tooltips) {
		WorldgenLootTier tier = getPackedTier(item);
		if (tier == null || tooltips == null) return;
		String tierName = Localization.translate("worldgengating", tier.getTierLocalizationKey());
		String keyName = ItemRegistry.getDisplayName(ItemRegistry.getItemID(tier.keyStringID));
		tooltips.add(new StringTooltips(Localization.translate("worldgengating", "lockedhint1")));
		tooltips.add(new StringTooltips(Localization.translate("worldgengating", "lockedhint2", "tier", tierName)));
		tooltips.add(new StringTooltips(Localization.translate("worldgengating", "lockedhint3", "key", keyName)));
	}

	private static int countStoredItems(Inventory inventory) {
		int count = 0;
		if (inventory == null) return count;
		for (int slot = 0; slot < inventory.getSize(); slot++) {
			InventoryItem item = inventory.getItem(slot);
			if (item != null) count += item.getAmount();
		}
		return count;
	}

	public static void discover(ServerClient client) {
		if (client == null) return;
		if (!JournalChallengeRegistry.doesChallengeExists(discoveryChallengeStringID)) return;
		if (GuideJournalRegistry.isDiscoveryChallengeCompleted(client, discoveryChallengeStringID)) return;
		GuideJournalRegistry.completeDiscoveryChallenge(client, discoveryChallengeStringID);
	}

	static boolean hasHeldMatchingKey(PlayerMob player, WorldgenLootTier tier) {
		if (player == null || tier == null) return false;
		InventoryItem selected = player.getSelectedItem();
		return selected != null && selected.getAmount() > 0 && tier.keyStringID.equals(selected.item.getStringID());
	}

	static boolean consumeHeldMatchingKey(PlayerMob player, WorldgenLootTier tier) {
		if (!hasHeldMatchingKey(player, tier)) return false;
		InventoryItem selected = player.getSelectedItem();
		PlayerInventorySlot selectedSlot = player.getSelectedItemSlot();
		if (selected == null || selectedSlot == null) return false;

		selected.setAmount(selected.getAmount() - 1);
		if (selected.getAmount() <= 0) {
			selectedSlot.setItem(player.getInv(), null);
		}
		else {
			selectedSlot.markDirty(player.getInv());
		}
		return true;
	}

	private static void spillInventory(Level level, int tileX, int tileY) {
		ObjectEntity entity = level.entityManager.getObjectEntity(tileX, tileY);
		if (entity == null || !entity.implementsOEInventory()) return;
		Inventory inventory = ((OEInventory)entity).getInventory();
		if (inventory == null) return;

		for (int slot = 0; slot < inventory.getSize(); slot++) {
			InventoryItem item = inventory.getItem(slot);
			if (item == null) continue;
			level.entityManager.pickups.add(item.copy().getPickupEntity(level, tileX * 32 + 16, tileY * 32 + 16));
			inventory.clearSlot(slot);
		}
	}

	private static String side(Level level) {
		if (level == null) return "null";
		if (level.isServer()) return "SERVER";
		if (level.isClient()) return "CLIENT";
		return "OTHER";
	}

	static void playLockedSound(Level level, int tileX, int tileY) {
		SoundManager.playSound(
				GameResources.cling,
				SoundEffect.effect(tileX * 32 + 16, tileY * 32 + 16).volume(0.65F).pitch(0.85F)
		);
	}
}
