package opusliews.worldgengating;

import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ObjectLayerRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.entity.manager.ObjectDestroyedListenerEntityComponent;
import necesse.entity.manager.ObjectPlacedListenerEntityComponent;
import necesse.entity.manager.RegionLoadedListenerEntityComponent;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.entity.objectEntity.interfaces.OEInventory;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;
import necesse.level.maps.regionSystem.Region;
import opusliews.logging.Logging;
import opusliews.network.PacketRequestWorldgenGatingData;
import opusliews.network.PacketWorldgenGatingState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class WorldgenGatingLevelData extends LevelData implements
		RegionLoadedListenerEntityComponent,
		ObjectPlacedListenerEntityComponent,
		ObjectDestroyedListenerEntityComponent {
	public static final String managerKey = "opusworldgengating";
	private static final String pirateDisplayMarkerPrefix = "dsPirateDisplay_";

	private final Map<String, Entry> entries = new HashMap<>();

	public static WorldgenGatingLevelData get(Level level, boolean createNewIfNull) {
		if (level == null) return null;
		LevelData existing = level.getLevelData(managerKey);
		if (existing instanceof WorldgenGatingLevelData) return (WorldgenGatingLevelData)existing;
		if (!createNewIfNull) return null;

		WorldgenGatingLevelData data = new WorldgenGatingLevelData();
		level.addLevelData(managerKey, data);
		Logging.logMessage("[WorldgenGatingDebug] Created level data side=" + side(level) + " level=" + level.getIdentifier());
		return data;
	}

	@Override
	public void onLoadingComplete() {
		Logging.logMessage("[WorldgenGatingDebug] onLoadingComplete BEGIN side=" + side(level) + " level=" + level.getIdentifier() + " savedEntries=" + entries.size());
		level.regionManager.forEachLoadedRegions(this::scanRegion);
		Logging.logMessage("[WorldgenGatingDebug] onLoadingComplete END side=" + side(level) + " level=" + level.getIdentifier() + " entries=" + entries.size());
		if (isServer() && !entries.isEmpty()) {
			Logging.logMessage("[WorldgenGating] Classified " + entries.size() + " natural gated object parts on " + level.getIdentifier());
		}
		if (level.isClient()) {
			level.getClient().network.sendPacket(new PacketRequestWorldgenGatingData(level));
		}
	}

	@Override
	public void onRegionLoaded(Region region) {
		scanRegion(region);
	}

	@Override
	public void onObjectPlaced(GameObject object, int objectLayerID, int tileX, int tileY, ServerClient client) {
		if (object == null || object.getID() == 0) return;

		if (client != null || level.objectLayer.isPlayerPlaced(objectLayerID, tileX, tileY)) {
			entries.remove(key(objectLayerID, tileX, tileY));
			return;
		}

		classifyNaturalObject(object, objectLayerID, tileX, tileY, "placed");
	}

	@Override
	public void onObjectDestroyed(GameObject object, int objectLayerID, int tileX, int tileY, ServerClient client, ArrayList itemsDropped) {
		entries.remove(key(objectLayerID, tileX, tileY));
		if (objectLayerID == 0 && object != null && "sprucedisplay".equals(object.getStringID())) {
			level.gndData.setBoolean(pirateDisplayMarkerKey(tileX, tileY), false);
		}
	}

	public Entry getEntry(int objectLayerID, int tileX, int tileY) {
		Entry entry = entries.get(key(objectLayerID, tileX, tileY));
		if (entry == null) return null;
		if (!entry.matches(level, objectLayerID, tileX, tileY)) {
			entries.remove(key(objectLayerID, tileX, tileY));
			return null;
		}
		return entry;
	}

	public void markSpecial(int objectLayerID, int tileX, int tileY, WorldgenGatingData.NaturalType type, WorldgenLootTier tier) {
		GameObject object = level.getObject(objectLayerID, tileX, tileY);
		if (object == null || object.getID() == 0 || level.objectLayer.isPlayerPlaced(objectLayerID, tileX, tileY)) {
			Logging.logMessage("[WorldgenGatingDebug] markSpecial SKIPPED side=" + side(level) + " level=" + level.getIdentifier()
					+ " layer=" + objectLayerID + " pos=" + tileX + "," + tileY
					+ " object=" + (object == null ? "null" : object.getStringID())
					+ " playerPlaced=" + level.objectLayer.isPlayerPlaced(objectLayerID, tileX, tileY));
			return;
		}
		Entry existing = getEntry(objectLayerID, tileX, tileY);
		WorldgenLootTier finalTier = existing == null ? tier : WorldgenLootTier.max(existing.tier, tier);
		Entry entry = new Entry(objectLayerID, tileX, tileY, object.getStringID(), type, finalTier, existing == null || existing.active, existing != null && existing.allowPlayerPlaced);
		entries.put(key(objectLayerID, tileX, tileY), entry);
		logClassification("special", object, objectLayerID, tileX, tileY, type, finalTier);
		if (level.isServer()) {
			level.getServer().network.sendToClientsWithTile(new PacketWorldgenGatingState(level, entry), level, tileX, tileY);
		}
		if (objectLayerID == 0 && "sprucedisplay".equals(object.getStringID()) && tier == WorldgenTierTable.PIRATE_DISPLAY_STAND) {
			level.gndData.setBoolean(pirateDisplayMarkerKey(tileX, tileY), true);
		}
	}

	public void markCarriedLocked(int objectLayerID, int tileX, int tileY, String objectStringID, WorldgenLootTier tier, boolean sync) {
		Entry entry = new Entry(objectLayerID, tileX, tileY, objectStringID, WorldgenGatingData.NaturalType.LOCKED_CONTAINER, tier, true, true);
		entries.put(key(objectLayerID, tileX, tileY), entry);
		Logging.logMessage("[WorldgenGatingDebug] CARRIED LOCKED side=" + side(level) + " level=" + level.getIdentifier()
				+ " object=" + objectStringID + " layer=" + objectLayerID + " pos=" + tileX + "," + tileY + " tier=" + tier);
		if (sync && level.isServer()) {
			level.getServer().network.sendToClientsWithTile(
					new PacketWorldgenGatingState(level, entry),
					level,
					tileX,
					tileY
			);
		}
	}

	public void applySyncedEntry(int objectLayerID, int tileX, int tileY, String objectStringID, WorldgenGatingData.NaturalType type, WorldgenLootTier tier, boolean active, boolean allowPlayerPlaced) {
		entries.put(key(objectLayerID, tileX, tileY), new Entry(objectLayerID, tileX, tileY, objectStringID, type, tier, active, allowPlayerPlaced));
	}

	public SaveData getSyncSaveData() {
		SaveData save = new SaveData("WORLDGENGATING");
		addSaveData(save);
		return save;
	}

	public void setEntryActive(int objectLayerID, int tileX, int tileY, boolean active, boolean sync) {
		Entry entry = getEntry(objectLayerID, tileX, tileY);
		if (entry == null || entry.active == active) return;
		entry.active = active;
		if (sync && level.isServer()) {
			level.getServer().network.sendToClientsWithTile(
					new PacketWorldgenGatingState(level, objectLayerID, tileX, tileY, active),
					level,
					tileX,
					tileY
			);
		}
	}

	private void scanRegion(Region region) {
		Iterator<Entry> iterator = entries.values().iterator();
		while (iterator.hasNext()) {
			Entry entry = iterator.next();
			if (inside(region, entry.tileX, entry.tileY) && !entry.matches(level, entry.objectLayerID, entry.tileX, entry.tileY)) {
				iterator.remove();
			}
		}

		for (int tileX = region.tileXOffset; tileX < region.tileXOffset + region.tileWidth; tileX++) {
			for (int tileY = region.tileYOffset; tileY < region.tileYOffset + region.tileHeight; tileY++) {
				for (int layerID = 0; layerID < ObjectLayerRegistry.getTotalLayers(); layerID++) {
					if (level.objectLayer.isPlayerPlaced(layerID, tileX, tileY)) continue;
					GameObject object = level.getObject(layerID, tileX, tileY);
					if (object == null || object.getID() == 0) continue;
					classifyNaturalObject(object, layerID, tileX, tileY, "scan");
				}
			}
		}
	}

	public Entry getOrClassifyEntry(int objectLayerID, int tileX, int tileY) {
		Entry entry = getEntry(objectLayerID, tileX, tileY);
		if (entry != null) {
			upgradeEntryTierFromContents(entry, "refresh", level.isServer());
			return entry;
		}
		if (level.objectLayer.isPlayerPlaced(objectLayerID, tileX, tileY)) return null;

		GameObject object = level.getObject(objectLayerID, tileX, tileY);
		if (object == null || object.getID() == 0) return null;

		classifyNaturalObject(object, objectLayerID, tileX, tileY, "lazy");
		entry = getEntry(objectLayerID, tileX, tileY);
		if (entry != null) upgradeEntryTierFromContents(entry, "lazy-content", level.isServer());
		return entry;
	}

	private void classifyNaturalObject(GameObject object, int objectLayerID, int tileX, int tileY, String source) {
		String objectStringID = object.getStringID();
		Entry existing = entries.get(key(objectLayerID, tileX, tileY));
		if (existing != null && existing.matches(level, objectLayerID, tileX, tileY)) return;
		if (objectLayerID == 0 && "sprucedisplay".equals(objectStringID) && level.gndData.getBoolean(pirateDisplayMarkerKey(tileX, tileY))) {
			WorldgenLootTier tier = WorldgenLootTier.max(WorldgenTierTable.PIRATE_DISPLAY_STAND, getContentTier(tileX, tileY));
			entries.put(key(objectLayerID, tileX, tileY), new Entry(objectLayerID, tileX, tileY, objectStringID, WorldgenGatingData.NaturalType.LOCKED_CONTAINER, tier));
			logClassification(source + "-pirate", object, objectLayerID, tileX, tileY, WorldgenGatingData.NaturalType.LOCKED_CONTAINER, tier);
			return;
		}

		WorldgenGatingData.NaturalType type = WorldgenObjectClassifier.getNaturalType(objectStringID);
		if (type == null) return;

		WorldgenLootTier tier = WorldgenObjectClassifier.getRequiredTier(level, tileX, tileY, objectStringID, type);
		if (tier == null) tier = WorldgenLootTier.DEMONIC;
		if (usesContentTier(type)) tier = WorldgenLootTier.max(tier, getContentTier(tileX, tileY));
		entries.put(key(objectLayerID, tileX, tileY), new Entry(objectLayerID, tileX, tileY, objectStringID, type, tier));
		logClassification(source, object, objectLayerID, tileX, tileY, type, tier);
	}

	public void refreshNaturalObjectFromContents(int objectLayerID, int tileX, int tileY) {
		if (level.objectLayer.isPlayerPlaced(objectLayerID, tileX, tileY)) return;
		GameObject object = level.getObject(objectLayerID, tileX, tileY);
		if (object == null || object.getID() == 0) return;
		classifyNaturalObject(object, objectLayerID, tileX, tileY, "post-loot");
		Entry entry = getEntry(objectLayerID, tileX, tileY);
		if (entry != null) upgradeEntryTierFromContents(entry, "post-loot-content", level.isServer());
	}

	private void upgradeEntryTierFromContents(Entry entry, String source, boolean sync) {
		if (!usesContentTier(entry.type)) return;
		WorldgenLootTier contentTier = getContentTier(entry.tileX, entry.tileY);
		WorldgenLootTier upgraded = WorldgenLootTier.max(entry.tier, contentTier);
		if (upgraded == null || upgraded == entry.tier) return;
		entry.tier = upgraded;
		Logging.logMessage("[WorldgenGatingDebug] TIER UPGRADE source=" + source + " side=" + side(level)
				+ " object=" + entry.objectStringID + " pos=" + entry.tileX + "," + entry.tileY + " tier=" + upgraded);
		if (sync && level.isServer()) {
			level.getServer().network.sendToClientsWithTile(new PacketWorldgenGatingState(level, entry), level, entry.tileX, entry.tileY);
		}
	}

	private static boolean usesContentTier(WorldgenGatingData.NaturalType type) {
		return type == WorldgenGatingData.NaturalType.LOCKED_CONTAINER
				|| type == WorldgenGatingData.NaturalType.TIED_SACK;
	}

	private WorldgenLootTier getContentTier(int tileX, int tileY) {
		ObjectEntity entity = level.entityManager.getObjectEntity(tileX, tileY);
		if (entity == null || !entity.implementsOEInventory()) return null;
		return WorldgenContentTierResolver.getHighestTier(((OEInventory)entity).getInventory());
	}

	@Override
	public void addSaveData(SaveData save) {
		super.addSaveData(save);
		for (Entry entry : entries.values()) {
			SaveData entrySave = new SaveData("ENTRY");
			entrySave.addInt("layer", entry.objectLayerID);
			entrySave.addInt("x", entry.tileX);
			entrySave.addInt("y", entry.tileY);
			entrySave.addUnsafeString("object", entry.objectStringID);
			entrySave.addUnsafeString("type", entry.type.name());
			entrySave.addUnsafeString("tier", entry.tier.name());
			entrySave.addBoolean("active", entry.active);
			entrySave.addBoolean("allowPlayerPlaced", entry.allowPlayerPlaced);
			save.addSaveData(entrySave);
		}
	}

	@Override
	public void applyLoadData(LoadData save) {
		entries.clear();
		for (LoadData entryLoad : save.getLoadDataByName("ENTRY")) {
			try {
				int layer = entryLoad.getInt("layer", 0, false);
				int x = entryLoad.getInt("x");
				int y = entryLoad.getInt("y");
				String object = entryLoad.getUnsafeString("object", null, false);
				String typeString = entryLoad.getUnsafeString("type", null, false);
				String tierString = entryLoad.getUnsafeString("tier", null, false);
				if (object == null || typeString == null || tierString == null) continue;
				WorldgenGatingData.NaturalType type = WorldgenGatingData.NaturalType.valueOf(typeString);
				WorldgenLootTier tier = WorldgenLootTier.valueOf(tierString);
				boolean active = entryLoad.getBoolean("active", true, false);
				boolean allowPlayerPlaced = entryLoad.getBoolean("allowPlayerPlaced", false, false);
				entries.put(key(layer, x, y), new Entry(layer, x, y, object, type, tier, active, allowPlayerPlaced));
			} catch (Exception ignored) {
			}
		}
		Logging.logMessage("[WorldgenGatingDebug] applyLoadData side=" + side(level) + " level=" + level.getIdentifier() + " loadedEntries=" + entries.size());
	}

	private void logClassification(String source, GameObject object, int objectLayerID, int tileX, int tileY, WorldgenGatingData.NaturalType type, WorldgenLootTier tier) {
		String biome = "unknown";
		try {
			biome = level.getBiome(tileX, tileY).getStringID();
		} catch (Exception ignored) {
		}
		Logging.logMessage("[WorldgenGatingDebug] CLASSIFY source=" + source
				+ " side=" + side(level)
				+ " level=" + level.getIdentifier()
				+ " object=" + object.getStringID()
				+ " layer=" + objectLayerID
				+ " pos=" + tileX + "," + tileY
				+ " type=" + type
				+ " tier=" + tier
				+ " toolTier=" + tier.toolTier
				+ " biome=" + biome
				+ " deep=" + level.isDeepCaveLevel()
				+ " playerPlaced=" + level.objectLayer.isPlayerPlaced(objectLayerID, tileX, tileY));
	}

	private static String side(Level level) {
		if (level == null) return "null";
		if (level.isServer()) return "SERVER";
		if (level.isClient()) return "CLIENT";
		return "OTHER";
	}

	private static boolean inside(Region region, int tileX, int tileY) {
		return tileX >= region.tileXOffset && tileX < region.tileXOffset + region.tileWidth
				&& tileY >= region.tileYOffset && tileY < region.tileYOffset + region.tileHeight;
	}

	private static String pirateDisplayMarkerKey(int tileX, int tileY) {
		return pirateDisplayMarkerPrefix + tileX + "_" + tileY;
	}

	private static String key(int layerID, int tileX, int tileY) {
		return layerID + ":" + tileX + ":" + tileY;
	}

	public static class Entry {
		public final int objectLayerID;
		public final int tileX;
		public final int tileY;
		public final String objectStringID;
		public final WorldgenGatingData.NaturalType type;
		public WorldgenLootTier tier;
		public boolean active;
		public final boolean allowPlayerPlaced;

		public Entry(int objectLayerID, int tileX, int tileY, String objectStringID, WorldgenGatingData.NaturalType type, WorldgenLootTier tier) {
			this(objectLayerID, tileX, tileY, objectStringID, type, tier, true, false);
		}

		public Entry(int objectLayerID, int tileX, int tileY, String objectStringID, WorldgenGatingData.NaturalType type, WorldgenLootTier tier, boolean active) {
			this(objectLayerID, tileX, tileY, objectStringID, type, tier, active, false);
		}

		public Entry(int objectLayerID, int tileX, int tileY, String objectStringID, WorldgenGatingData.NaturalType type, WorldgenLootTier tier, boolean active, boolean allowPlayerPlaced) {
			this.objectLayerID = objectLayerID;
			this.tileX = tileX;
			this.tileY = tileY;
			this.objectStringID = objectStringID;
			this.type = type;
			this.tier = tier;
			this.active = active;
			this.allowPlayerPlaced = allowPlayerPlaced;
		}

		public boolean matches(Level level, int objectLayerID, int tileX, int tileY) {
			if (this.objectLayerID != objectLayerID || this.tileX != tileX || this.tileY != tileY) return false;
			if (!allowPlayerPlaced && level.objectLayer.isPlayerPlaced(objectLayerID, tileX, tileY)) return false;
			GameObject object = level.getObject(objectLayerID, tileX, tileY);
			return object != null && object.getID() != 0 && objectStringID.equals(object.getStringID());
		}
	}
}
