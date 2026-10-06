package opusliews.worldgengating;

import java.util.ArrayList;
import necesse.inventory.InventoryItem;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import opusliews.logging.Logging;

public final class WorldgenCarriedStationSystem {
	private WorldgenCarriedStationSystem() {
	}

	public static ArrayList<InventoryItem> tagNaturalStationDrop(
			GameObject object,
			Level level,
			int objectLayerID,
			int tileX,
			int tileY,
			String purpose,
			ArrayList<InventoryItem> originalDrops
	) {
		if (level == null || object == null || originalDrops == null || !"onDestroyed".equals(purpose)) return originalDrops;

		WorldgenGatingLevelData data = WorldgenGatingLevelData.get(level, true);
		if (data == null) return originalDrops;
		WorldgenGatingLevelData.Entry entry = data.getEntry(objectLayerID, tileX, tileY);
		if (entry == null || !entry.active || entry.type != WorldgenGatingData.NaturalType.CRAFTING_STATION) return originalDrops;

		boolean tagged = false;
		String droppedStationItemID = object.getObjectItem() == null ? null : object.getObjectItem().getStringID();
		for (InventoryItem item : originalDrops) {
			if (item == null || item.item == null) continue;
			if (droppedStationItemID == null || !droppedStationItemID.equals(item.item.getStringID())) continue;

			item.getGndData().setBoolean(WorldgenGatingData.naturalKey, true);
			item.getGndData().setString(WorldgenGatingData.naturalTypeKey, WorldgenGatingData.NaturalType.CRAFTING_STATION.name());
			item.getGndData().setString(WorldgenGatingData.tierKey, entry.tier.name());
			tagged = true;
		}

		if (tagged) {
			Logging.logMessage("[WorldgenGatingDebug] PACKAGE STATION object=" + object.getStringID()
					+ " pos=" + tileX + "," + tileY + " tier=" + entry.tier
					+ " playerPlaced=" + level.objectLayer.isPlayerPlaced(objectLayerID, tileX, tileY));
		}
		return originalDrops;
	}

	public static void restorePlacedNaturalStation(
			Level level,
			int objectLayerID,
			int tileX,
			int tileY,
			GameObject object,
			InventoryItem item
	) {
		if (level == null || object == null || item == null || !level.isServer() || !isCarriedNaturalStation(item)) return;
		WorldgenLootTier tier = getCarriedTier(item);
		if (tier == null) return;

		WorldgenStationProgressionSystem.StationRequirement requirement =
				WorldgenStationProgressionSystem.getNaturalStationRequirement(object.getStringID());
		if (requirement == null) return;

		WorldgenGatingLevelData data = WorldgenGatingLevelData.get(level, true);
		if (data == null) return;
		data.markCarriedStation(objectLayerID, tileX, tileY, object.getStringID(), tier, true);
		Logging.logMessage("[WorldgenGatingDebug] RESTORE PLACED STATION object=" + object.getStringID()
				+ " pos=" + tileX + "," + tileY + " tier=" + tier + " playerPlaced=true");
	}

	public static boolean isCarriedNaturalStation(InventoryItem item) {
		if (item == null) return false;
		return item.getGndData().getBoolean(WorldgenGatingData.naturalKey)
				&& WorldgenGatingData.NaturalType.CRAFTING_STATION.name().equals(
						item.getGndData().getString(WorldgenGatingData.naturalTypeKey, null)
				);
	}

	private static WorldgenLootTier getCarriedTier(InventoryItem item) {
		if (!isCarriedNaturalStation(item)) return null;
		String tierName = item.getGndData().getString(WorldgenGatingData.tierKey, null);
		if (tierName == null) return null;
		try {
			return WorldgenLootTier.valueOf(tierName);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}
}
