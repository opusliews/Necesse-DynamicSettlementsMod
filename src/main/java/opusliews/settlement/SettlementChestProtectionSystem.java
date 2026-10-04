package opusliews.settlement;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ObjectLayerRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.util.LevelIdentifier;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.entity.objectEntity.interfaces.OEInventory;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.ToolDamageItem;
import necesse.inventory.item.toolItem.ToolType;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import necesse.level.maps.levelData.settlementData.CachedSettlementData;
import necesse.level.maps.levelData.settlementData.NetworkSettlementData;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementBoundsManager;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;
import opusliews.network.PacketSettlementChestProtectionSync;

public final class SettlementChestProtectionSystem {
	public static final String saveKey = "dynamicSettlementsProtectSettlementChests";
	public static final String ctrlMapKey = "dynamicSettlementsChestProtectionCtrl";

	private static final Map<ServerSettlementData, Boolean> serverSettings = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<Integer, Boolean> clientSettings = new ConcurrentHashMap<>();
	private static final Map<Integer, ClientSettlementInfo> clientSettlementInfo = new ConcurrentHashMap<>();

	private SettlementChestProtectionSystem() {
	}

	public static boolean isProtected(ServerSettlementData settlement) {
		if (settlement == null) return false;
		return Boolean.TRUE.equals(serverSettings.get(settlement));
	}

	public static void setProtected(ServerSettlementData settlement, boolean value) {
		if (settlement == null) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Cannot update setting for null settlement");
			return;
		}

		if (value) serverSettings.put(settlement, true);
		else serverSettings.remove(settlement);

		if (settlement.networkData != null) settlement.networkData.markDirty(false);
		else if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Settlement network data missing while updating setting settlement=" + settlement.uniqueID + " enabled=" + value);

		if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Updated settlement=" + settlement.uniqueID + " enabled=" + value);
		syncSettlementToRelevantClients(settlement);
	}

	public static boolean getClientProtected(int settlementUniqueID) {
		return Boolean.TRUE.equals(clientSettings.get(settlementUniqueID));
	}


	public static void clearClientState() {
		clientSettings.clear();
		clientSettlementInfo.clear();
		if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Cleared client settlement protection cache");
	}

	public static void setClientProtected(int settlementUniqueID, boolean value) {
		if (value) clientSettings.put(settlementUniqueID, true);
		else clientSettings.remove(settlementUniqueID);
		if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Client sync settlement=" + settlementUniqueID + " enabled=" + value);
	}

	public static void setClientProtected(int settlementUniqueID, boolean value, LevelIdentifier surfaceIdentifier, int flagTileX, int flagTileY, int flagTier) {
		setClientProtected(settlementUniqueID, value);
		if (surfaceIdentifier == null) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Cannot cache explicit client settlement bounds settlement=" + settlementUniqueID + " because surface identifier is null");
			return;
		}

		clientSettlementInfo.put(settlementUniqueID, new ClientSettlementInfo(
				settlementUniqueID,
				surfaceIdentifier,
				flagTileX,
				flagTileY,
				flagTier));
		if (Logging.logEnabled) {
			Logging.logMessage("[ChestProtection] Cached explicit client settlement bounds settlement=" + settlementUniqueID
					+ " surface=" + surfaceIdentifier + " flag=" + flagTileX + "," + flagTileY
					+ " tier=" + flagTier + " enabled=" + value);
		}
	}

	public static void setClientProtected(NetworkSettlementData networkData, boolean value) {
		if (networkData == null) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Cannot cache client settlement bounds because network data is null enabled=" + value);
			return;
		}

		setClientProtected(networkData.uniqueID, value);
		LevelIdentifier surfaceIdentifier = SettlementMultiLevelSystem.getSurfaceIdentifier(networkData.level.getIdentifier());
		if (surfaceIdentifier == null) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Cannot cache client settlement bounds settlement=" + networkData.uniqueID + " level=" + networkData.level.getIdentifier());
			return;
		}

		clientSettlementInfo.put(networkData.uniqueID, new ClientSettlementInfo(
				networkData.uniqueID,
				surfaceIdentifier,
				networkData.getTileX(),
				networkData.getTileY(),
				networkData.getFlagTier()));
		if (Logging.logEnabled) {
			Logging.logMessage("[ChestProtection] Cached client settlement bounds settlement=" + networkData.uniqueID
					+ " surface=" + surfaceIdentifier + " flag=" + networkData.getTileX() + "," + networkData.getTileY()
					+ " tier=" + networkData.getFlagTier() + " enabled=" + value);
		}
	}

	public static void addSaveData(ServerSettlementData settlement, SaveData save) {
		if (settlement == null || save == null) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Cannot save setting settlement=" + settlement + " save=" + save);
			return;
		}
		if (isProtected(settlement)) save.addBoolean(saveKey, true);
	}

	public static void applyLoadData(ServerSettlementData settlement, LoadData save) {
		if (settlement == null || save == null) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Cannot load setting settlement=" + settlement + " save=" + save);
			return;
		}
		boolean value = save.getBoolean(saveKey, false, false);
		if (value) serverSettings.put(settlement, true);
		else serverSettings.remove(settlement);
		if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Loaded settlement=" + settlement.uniqueID + " enabled=" + value);
		syncSettlementToRelevantClients(settlement);
	}

	public static void syncRelevantSettlements(ServerClient client) {
		if (client == null || client.playerMob == null || client.getLevel() == null) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Cannot sync nearby settlement protection because client/player/level is unavailable");
			return;
		}

		LevelIdentifier surfaceIdentifier = SettlementMultiLevelSystem.getSurfaceIdentifier(client.getLevel().getIdentifier());
		if (surfaceIdentifier == null) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Cannot sync nearby settlement protection for level=" + client.getLevel().getIdentifier());
			return;
		}

		int tileX = client.playerMob.getTileX();
		int tileY = client.playerMob.getTileY();
		SettlementsWorldData settlements = SettlementsWorldData.getSettlementsData(client.getServer());
		Iterator<CachedSettlementData> iterator = settlements.streamSettlements().iterator();
		int sent = 0;
		while (iterator.hasNext()) {
			CachedSettlementData cached = iterator.next();
			if (cached == null || !surfaceIdentifier.equals(cached.levelIdentifier)) continue;
			if (!cached.isTileWithinLoadedRegionBounds(tileX, tileY)) continue;

			ServerSettlementData settlement = settlements.getServerData(cached.uniqueID);
			if (settlement == null) settlement = settlements.getOrLoadServerData(cached.uniqueID);
			if (settlement == null) {
				if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Failed to load nearby settlement for client sync settlement=" + cached.uniqueID + " client=" + client.getName());
				continue;
			}

			client.sendPacket(new PacketSettlementChestProtectionSync(settlement, isProtected(settlement)));
			sent++;
		}

		if (Logging.logEnabled) {
			Logging.logMessage("[ChestProtection] Synced nearby settlement protection client=" + client.getName()
					+ " level=" + client.getLevel().getIdentifier() + " tile=" + tileX + "," + tileY + " settlements=" + sent);
		}
	}

	public static void syncSettlementToRelevantClients(ServerSettlementData settlement) {
		if (settlement == null || settlement.networkData == null || settlement.getLevel() == null || settlement.getLevel().getServer() == null) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Cannot broadcast settlement protection because settlement/network/server data is unavailable settlement=" + (settlement == null ? "null" : settlement.uniqueID));
			return;
		}

		LevelIdentifier surfaceIdentifier = SettlementMultiLevelSystem.getSurfaceIdentifier(settlement.getLevel().getIdentifier());
		if (surfaceIdentifier == null) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Cannot broadcast settlement protection because surface identifier is unavailable settlement=" + settlement.uniqueID);
			return;
		}

		int flagTileX = settlement.networkData.getTileX();
		int flagTileY = settlement.networkData.getTileY();
		int flagTier = settlement.networkData.getFlagTier();
		boolean enabled = isProtected(settlement);
		int sent = 0;
		for (Object value : settlement.getLevel().getServer().getClients()) {
			if (!(value instanceof ServerClient)) continue;
			ServerClient client = (ServerClient)value;
			if (client.playerMob == null || client.getLevel() == null) continue;

			LevelIdentifier clientSurfaceIdentifier = SettlementMultiLevelSystem.getSurfaceIdentifier(client.getLevel().getIdentifier());
			if (!surfaceIdentifier.equals(clientSurfaceIdentifier)) continue;
			if (!SettlementBoundsManager.isTileWithinLoadedRegionBounds(client.playerMob.getTileX(), client.playerMob.getTileY(), flagTileX, flagTileY, flagTier)) continue;

			client.sendPacket(new PacketSettlementChestProtectionSync(settlement, enabled));
			sent++;
		}

		if (Logging.logEnabled) {
			Logging.logMessage("[ChestProtection] Broadcast settlement protection settlement=" + settlement.uniqueID
					+ " enabled=" + enabled + " clients=" + sent);
		}
	}

	public static boolean resolveNetworkSetting(NetworkSettlementData networkData) {
		if (networkData == null) return false;
		ServerSettlementData serverData = networkData.getServerData();
		return serverData != null && isProtected(serverData);
	}

	public static boolean shouldProtectToolDamage(ToolDamageItem tool, Level level, int priorityObjectLayerID, int tileX, int tileY, InventoryItem item, boolean ctrlDown) {
		if (tool == null) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtectionSound] Protection rejected: tool is null tile=" + tileX + "," + tileY);
			return false;
		}
		if (level == null) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtectionSound] Protection rejected: level is null tile=" + tileX + "," + tileY);
			return false;
		}
		if (item == null) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtectionSound] Protection rejected: item is null level=" + level.getIdentifier() + " tile=" + tileX + "," + tileY);
			return false;
		}
		if (ctrlDown) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtectionSound] Protection bypassed by Ctrl level=" + level.getIdentifier() + " tile=" + tileX + "," + tileY);
			return false;
		}

		ToolType toolType = tool.getToolType(item);
		int layerID = findTargetObjectLayer(level, priorityObjectLayerID, tileX, tileY, toolType);
		if (layerID < 0) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtectionSound] Protection rejected: no target object layer level=" + level.getIdentifier() + " tile=" + tileX + "," + tileY + " priorityLayer=" + priorityObjectLayerID + " toolType=" + toolType);
			return false;
		}

		LevelObject target = level.getLevelObject(layerID, tileX, tileY);
		if (target == null || target.object == null || target.object.getID() == 0) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtectionSound] Protection rejected: target object missing level=" + level.getIdentifier() + " layer=" + layerID + " tile=" + tileX + "," + tileY);
			return false;
		}

		LevelObject master = (LevelObject)target.getMasterLevelObject().orElse(target);
		ObjectEntity objectEntity = level.entityManager.getObjectEntity(master.tileX, master.tileY);
		if (!(objectEntity instanceof OEInventory)) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtectionSound] Protection rejected: target is not OEInventory level=" + level.getIdentifier() + " layer=" + layerID + " tile=" + tileX + "," + tileY + " master=" + master.tileX + "," + master.tileY + " object=" + target.object.getStringID() + " entity=" + (objectEntity == null ? "null" : objectEntity.getClass().getName()));
			return false;
		}

		Integer settlementUniqueID = resolveSettlementUniqueID(level, master.tileX, master.tileY);
		if (settlementUniqueID == null) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtectionSound] Protection rejected: settlement could not be resolved level=" + level.getIdentifier() + " tile=" + master.tileX + "," + master.tileY + " object=" + target.object.getStringID());
			return false;
		}

		boolean enabled;
		if (level.isServer()) {
			SettlementLevelDomain domain = SettlementMultiLevelSystem.findDomainQuiet(level.getServer(), level.getIdentifier(), master.tileX, master.tileY);
			if (domain == null || domain.getSettlement() == null) {
				if (Logging.logEnabled) Logging.logMessage("[ChestProtectionSound] Protection rejected: server settlement domain missing level=" + level.getIdentifier() + " tile=" + master.tileX + "," + master.tileY + " resolvedSettlement=" + settlementUniqueID);
				return false;
			}
			enabled = isProtected(domain.getSettlement());
		} else {
			enabled = getClientProtected(settlementUniqueID);
		}

		if (Logging.logEnabled) {
			Logging.logMessage("[ChestProtectionSound] Protection state side=" + (level.isClient() ? "client" : level.isServer() ? "server" : "other")
					+ " settlement=" + settlementUniqueID + " enabled=" + enabled
					+ " level=" + level.getIdentifier() + " layer=" + layerID + " tile=" + master.tileX + "," + master.tileY
					+ " object=" + target.object.getStringID() + " entity=" + objectEntity.getClass().getName());
		}
		return enabled;
	}

	public static void playProtectedHitEffects(Level level, int priorityObjectLayerID, int tileX, int tileY, ToolType toolType, int mouseX, int mouseY) {
		if (level == null || !level.isClient()) return;

		int layerID = findTargetObjectLayer(level, priorityObjectLayerID, tileX, tileY, toolType);
		if (layerID < 0) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Could not play protected no-damage effects: target layer not found level=" + level.getIdentifier() + " tile=" + tileX + "," + tileY);
			return;
		}

		GameObject object = level.getObject(layerID, tileX, tileY);
		if (object == null || object.getID() == 0) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Could not play protected no-damage effects: target object missing level=" + level.getIdentifier() + " layer=" + layerID + " tile=" + tileX + "," + tileY);
			return;
		}

		// Match vanilla's exact effect path for a tool that is too weak to damage the object.
		object.spawnDebrisParticles(level, tileX, tileY, false, mouseX, mouseY);
		object.playDamageSound(level, tileX, tileY, false);
		if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Played protected no-damage effects level=" + level.getIdentifier() + " layer=" + layerID + " tile=" + tileX + "," + tileY + " object=" + object.getStringID());
	}


	private static Integer resolveSettlementUniqueID(Level level, int tileX, int tileY) {
		if (level == null) return null;
		if (level.isServer()) {
			SettlementLevelDomain domain = SettlementMultiLevelSystem.findDomainQuiet(level.getServer(), level.getIdentifier(), tileX, tileY);
			return domain == null ? null : domain.getSettlementUniqueID();
		}

		LevelIdentifier surfaceIdentifier = SettlementMultiLevelSystem.getSurfaceIdentifier(level.getIdentifier());
		if (surfaceIdentifier == null) return null;

		SettlementsWorldData settlements = SettlementsWorldData.getSettlementsData(level);
		int settlementUniqueID = settlements.getSettlementUniqueIDAtTile(surfaceIdentifier, tileX, tileY);
		if (settlementUniqueID != 0) return settlementUniqueID;

		for (ClientSettlementInfo info : clientSettlementInfo.values()) {
			if (!info.surfaceIdentifier.equals(surfaceIdentifier)) continue;
			if (!SettlementBoundsManager.isTileWithinBounds(tileX, tileY, info.flagTileX, info.flagTileY, info.flagTier)) continue;
			if (Logging.logEnabled) {
				Logging.logMessage("[ChestProtection] Resolved client settlement through cached multi-level bounds settlement=" + info.settlementUniqueID
						+ " level=" + level.getIdentifier() + " tile=" + tileX + "," + tileY);
			}
			return info.settlementUniqueID;
		}

		if (Logging.logEnabled) {
			Logging.logMessage("[ChestProtection] Could not resolve client settlement for inventory object level=" + level.getIdentifier()
					+ " projectedSurface=" + surfaceIdentifier + " tile=" + tileX + "," + tileY);
		}
		return null;
	}

	private static int findTargetObjectLayer(Level level, int priorityObjectLayerID, int tileX, int tileY, ToolType toolType) {
		if (toolType != ToolType.AXE && toolType != ToolType.PICKAXE && toolType != ToolType.ALL) return -1;

		if (priorityObjectLayerID >= 0 && priorityObjectLayerID < ObjectLayerRegistry.getTotalLayers()) {
			int objectID = level.getObjectID(priorityObjectLayerID, tileX, tileY);
			if (objectID != 0) {
				GameObject object = level.getObject(priorityObjectLayerID, tileX, tileY);
				if (object != null && object.toolType != ToolType.UNBREAKABLE) return priorityObjectLayerID;
			}
		}

		for (int layerID = ObjectLayerRegistry.getTotalLayers() - 1; layerID >= 0; layerID--) {
			int objectID = level.getObjectID(layerID, tileX, tileY);
			if (objectID == 0) continue;
			GameObject object = level.getObject(layerID, tileX, tileY);
			if (object != null && object.toolType != ToolType.UNBREAKABLE) return layerID;
		}
		return -1;
	}
	private static final class ClientSettlementInfo {
		private final int settlementUniqueID;
		private final LevelIdentifier surfaceIdentifier;
		private final int flagTileX;
		private final int flagTileY;
		private final int flagTier;

		private ClientSettlementInfo(int settlementUniqueID, LevelIdentifier surfaceIdentifier, int flagTileX, int flagTileY, int flagTier) {
			this.settlementUniqueID = settlementUniqueID;
			this.surfaceIdentifier = surfaceIdentifier;
			this.flagTileX = flagTileX;
			this.flagTileY = flagTileY;
			this.flagTier = flagTier;
		}
	}

}
