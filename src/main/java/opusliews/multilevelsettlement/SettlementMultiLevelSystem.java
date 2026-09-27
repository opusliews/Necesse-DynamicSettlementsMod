package opusliews.multilevelsettlement;

import necesse.engine.network.server.Server;
import necesse.engine.util.LevelIdentifier;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.level.maps.Level;
import necesse.level.maps.regionSystem.Region;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opusliews.logging.Logging;

import java.util.Collections;
import java.awt.Rectangle;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;

public final class SettlementMultiLevelSystem {
	private static final Map<ServerSettlementData, SettlementLevelDomain> domains = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<ServerSettlementData, Boolean> caveMissingLogged = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<ServerSettlementData, Boolean> caveLoadedLogged = Collections.synchronizedMap(new WeakHashMap<>());

	private SettlementMultiLevelSystem() {
	}

	public static SettlementLevelDomain get(ServerSettlementData settlement) {
		if (settlement == null) {
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelSettlement] Cannot resolve domain for null settlement");
			return null;
		}

		synchronized (domains) {
			SettlementLevelDomain existing = domains.get(settlement);
			if (existing != null) return existing;

			SettlementLevelDomain created = createDomain(settlement);
			if (created == null) return null;
			domains.put(settlement, created);
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelSettlement] Registered " + created);
			return created;
		}
	}



	public static void ensureCaveSettlementLoaded(ServerSettlementData settlement) {
		if (settlement == null || settlement.getServer() == null) return;
		SettlementLevelDomain domain = get(settlement);
		if (domain == null) return;

		LevelIdentifier caveIdentifier = domain.getLevelIdentifier(SettlementLevelType.CAVE);
		if (caveIdentifier == null) {
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelSettlement] Cannot keep cave loaded because cave identifier is missing settlement=" + settlement.uniqueID);
			return;
		}

		if (!settlement.getServer().world.levelExists(caveIdentifier)) {
			if (Logging.logEnabled && !Boolean.TRUE.equals(caveMissingLogged.get(settlement))) {
				caveMissingLogged.put(settlement, true);
				Logging.logMessage("[MultiLevelSettlement] Cave level does not exist yet; not generating it solely for settlement keep-loaded settlement=" + settlement.uniqueID + " cave=" + caveIdentifier);
			}
			return;
		}

		Level cave = settlement.getServer().world.getLevel(caveIdentifier);
		if (cave == null) {
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelSettlement] FAILED to load existing cave level settlement=" + settlement.uniqueID + " cave=" + caveIdentifier);
			return;
		}
		caveMissingLogged.remove(settlement);
		cave.unloadLevelBuffer = 0;

		Rectangle bounds = domain.getTileBounds(SettlementLevelType.CAVE);
		if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelSettlement] Cannot keep cave regions loaded because settlement bounds are invalid settlement=" + settlement.uniqueID + " bounds=" + bounds);
			return;
		}

		int startRegionX = cave.regionManager.getRegionXByTileLimited(bounds.x);
		int startRegionY = cave.regionManager.getRegionYByTileLimited(bounds.y);
		int endRegionX = cave.regionManager.getRegionXByTileLimited(bounds.x + bounds.width - 1);
		int endRegionY = cave.regionManager.getRegionYByTileLimited(bounds.y + bounds.height - 1);
		int keptRegions = 0;
		for (int regionX = startRegionX; regionX <= endRegionX; regionX++) {
			for (int regionY = startRegionY; regionY <= endRegionY; regionY++) {
				if (!cave.regionManager.isRegionWithinBounds(regionX, regionY)) continue;
				Region region = cave.regionManager.getRegion(regionX, regionY, true);
				if (region == null) {
					if (Logging.logEnabled) Logging.logMessage("[MultiLevelSettlement] FAILED to load cave settlement region settlement=" + settlement.uniqueID + " cave=" + caveIdentifier + " region=" + regionX + "," + regionY);
					continue;
				}
				region.unloadRegionBuffer.keepLoaded();
				keptRegions++;
			}
		}

		SettlementCaveBedSystem.restoreAssignments(settlement);

		if (Logging.logEnabled && !Boolean.TRUE.equals(caveLoadedLogged.get(settlement))) {
			SettlementCaveBedSystem.logCaveBedScan(settlement);
			caveLoadedLogged.put(settlement, true);
			Logging.logMessage("[MultiLevelSettlement] Cave settlement domain is now kept loaded settlement=" + settlement.uniqueID + " cave=" + caveIdentifier + " regions=" + keptRegions + " bounds=" + bounds);
		}
	}

	public static SettlementLevelDomain findDomain(Server server, LevelIdentifier levelIdentifier, int tileX, int tileY) {
		if (server == null || levelIdentifier == null) {
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelSettlement] Cannot find domain with null server/level identifier level=" + levelIdentifier + " tile=" + tileX + "," + tileY);
			return null;
		}

		LevelIdentifier surfaceIdentifier = getSurfaceIdentifier(levelIdentifier);
		if (surfaceIdentifier == null) return null;
		ServerSettlementData settlement = SettlementsWorldData.getSettlementsData(server).getServerDataAtTile(surfaceIdentifier, tileX, tileY);
		if (settlement == null) return null;
		SettlementLevelDomain domain = get(settlement);
		if (domain == null || !domain.containsLevel(levelIdentifier) || !domain.isTileWithinBounds(levelIdentifier, tileX, tileY)) return null;
		return domain;
	}

	public static void remove(ServerSettlementData settlement) {
		if (settlement == null) return;
		SettlementLevelDomain removed = domains.remove(settlement);
		caveMissingLogged.remove(settlement);
		caveLoadedLogged.remove(settlement);
		if (removed != null && Logging.logEnabled) Logging.logMessage("[MultiLevelSettlement] Removed domain settlement=" + settlement.uniqueID);
	}

	public static LevelIdentifier getSurfaceIdentifier(LevelIdentifier identifier) {
		if (identifier == null) {
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelSettlement] Cannot derive surface identifier from null");
			return null;
		}

		if (identifier.isOneWorldDimension()) {
			int dimension = identifier.getOneWorldDimension();
			if (dimension == 0 || dimension == -1) return LevelIdentifier.SURFACE_IDENTIFIER;
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelSettlement] Unsupported OneWorld level for surface/cave domain identifier=" + identifier + " dimension=" + dimension);
			return null;
		}

		int[] island = parseLegacyIslandIdentifier(identifier);
		if (island != null) {
			int dimension = island[2];
			if (dimension == 0 || dimension == -1) return new LevelIdentifier(island[0], island[1], 0);
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelSettlement] Unsupported island level for surface/cave domain identifier=" + identifier + " dimension=" + dimension);
			return null;
		}

		if (Logging.logEnabled) Logging.logMessage("[MultiLevelSettlement] Cannot derive surface identifier from non-island/non-OneWorld level identifier=" + identifier);
		return null;
	}

	public static LevelIdentifier getCaveIdentifier(LevelIdentifier identifier) {
		if (identifier == null) {
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelSettlement] Cannot derive cave identifier from null");
			return null;
		}

		if (identifier.isOneWorldDimension()) {
			int dimension = identifier.getOneWorldDimension();
			if (dimension == 0 || dimension == -1) return LevelIdentifier.CAVE_IDENTIFIER;
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelSettlement] Unsupported OneWorld level for surface/cave domain identifier=" + identifier + " dimension=" + dimension);
			return null;
		}

		int[] island = parseLegacyIslandIdentifier(identifier);
		if (island != null) {
			int dimension = island[2];
			if (dimension == 0 || dimension == -1) return new LevelIdentifier(island[0], island[1], -1);
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelSettlement] Unsupported island level for surface/cave domain identifier=" + identifier + " dimension=" + dimension);
			return null;
		}

		if (Logging.logEnabled) Logging.logMessage("[MultiLevelSettlement] Cannot derive cave identifier from non-island/non-OneWorld level identifier=" + identifier);
		return null;
	}

	private static int[] parseLegacyIslandIdentifier(LevelIdentifier identifier) {
		Matcher matcher = LevelIdentifier.islandStringPattern.matcher(identifier.stringID);
		if (!matcher.matches()) return null;
		try {
			return new int[] { Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)) };
		} catch (NumberFormatException e) {
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelSettlement] Failed to parse legacy island identifier=" + identifier + " error=" + e.getMessage());
			return null;
		}
	}

	private static SettlementLevelDomain createDomain(ServerSettlementData settlement) {
		LevelIdentifier settlementIdentifier = settlement.getLevel().getIdentifier();
		LevelIdentifier surfaceIdentifier = getSurfaceIdentifier(settlementIdentifier);
		LevelIdentifier caveIdentifier = getCaveIdentifier(settlementIdentifier);
		if (surfaceIdentifier == null || caveIdentifier == null) {
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelSettlement] Failed to create domain settlement=" + settlement.uniqueID + " settlementLevel=" + settlementIdentifier + " surface=" + surfaceIdentifier + " cave=" + caveIdentifier);
			return null;
		}
		return new SettlementLevelDomain(settlement, surfaceIdentifier, caveIdentifier);
	}
}
