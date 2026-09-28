package opusliews.multilevelsettlement;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import necesse.engine.util.LevelIdentifier;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.level.maps.levelData.settlementData.LevelSettler;
import necesse.level.maps.levelData.settlementData.RestrictZone;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.ZoneTester;
import necesse.level.maps.levelData.settlementData.zones.SettlementWorkZone;
import opusliews.logging.Logging;

public final class SettlementLevelZoneSystem {
	private SettlementLevelZoneSystem() {
	}

	public static ZoneTester getCurrentLevelJobRestriction(HumanMob human) {
		if (human == null || human.getLevel() == null || !human.isSettler()) return null;
		ServerSettlementData settlement = human.getSettlerSettlementServerData();
		if (settlement == null) return null;
		LevelSettler levelSettler = settlement.getSettler(human.getUniqueID());
		if (levelSettler == null) return null;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null || !domain.containsLevel(human.getLevel().getIdentifier())) return null;
		LevelIdentifier currentLevel = human.getLevel().getIdentifier();
		return (tileX, tileY) -> isTileAllowed(levelSettler, domain, currentLevel, tileX, tileY);
	}

	public static SettlementRouteRestriction getRouteRestriction(HumanMob human) {
		if (human == null || !human.isSettler()) return SettlementRouteRestriction.ALLOW_ALL;
		ServerSettlementData settlement = human.getSettlerSettlementServerData();
		if (settlement == null) return SettlementRouteRestriction.ALLOW_ALL;
		LevelSettler levelSettler = settlement.getSettler(human.getUniqueID());
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (levelSettler == null || domain == null) return SettlementRouteRestriction.ALLOW_ALL;
		return (levelIdentifier, tileX, tileY) -> isTileAllowed(levelSettler, domain, levelIdentifier, tileX, tileY);
	}

	public static boolean isTileAllowed(LevelSettler settler, SettlementLevelDomain domain, LevelIdentifier levelIdentifier, int tileX, int tileY) {
		if (settler == null || domain == null || levelIdentifier == null) return false;
		if (!domain.isTileWithinBounds(levelIdentifier, tileX, tileY)) return false;
		int restrictUniqueID = settler.getRestrictZoneUniqueID();
		if (restrictUniqueID == 0) return true;
		return isTileInRestrictZone(settler.data, levelIdentifier, restrictUniqueID, tileX, tileY);
	}

	public static boolean isTileInRestrictZone(ServerSettlementData settlement, LevelIdentifier levelIdentifier, int restrictUniqueID, int tileX, int tileY) {
		if (settlement == null || levelIdentifier == null || restrictUniqueID == 0) return true;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null || !domain.containsLevel(levelIdentifier)) return false;

		if (settlement.getLevel().getIdentifier().equals(levelIdentifier)) {
			RestrictZone zone = settlement.getRestrictZone(restrictUniqueID);
			return zone != null && zone.containsTile(tileX, tileY);
		}

		SettlementLevelZoneLevelData.Entry entry = getDataEntry(settlement, SettlementLevelZoneLevelData.ZoneKind.RESTRICT, levelIdentifier, restrictUniqueID);
		if (entry != null) return contains(entry.rectangles, tileX, tileY);

		// Until the cave restriction editor exists, mirror the surface geometry as the initial cave geometry.
		// The first cave-side edit will persist an independent override through setRestrictZoneGeometry.
		RestrictZone surfaceZone = settlement.getRestrictZone(restrictUniqueID);
		return surfaceZone != null && surfaceZone.containsTile(tileX, tileY);
	}

	public static void setRestrictZoneGeometry(ServerSettlementData settlement, LevelIdentifier levelIdentifier, int restrictUniqueID, Collection<Rectangle> rectangles) {
		if (!isNonSurfaceDomainLevel(settlement, levelIdentifier) || restrictUniqueID == 0) return;
		SettlementLevelZoneLevelData data = SettlementLevelZoneLevelData.get(settlement.getLevel(), true);
		if (data == null) return;
		data.setEntry(settlement.uniqueID, SettlementLevelZoneLevelData.ZoneKind.RESTRICT, levelIdentifier, restrictUniqueID, -1, normalizeRectangles(settlement, levelIdentifier, rectangles));
		if (Logging.logEnabled) Logging.logMessage("[LevelZones] Saved independent restriction geometry settlement=" + settlement.uniqueID + " level=" + levelIdentifier + " restrictZone=" + restrictUniqueID);
	}

	public static void clearRestrictZoneGeometry(ServerSettlementData settlement, LevelIdentifier levelIdentifier, int restrictUniqueID) {
		if (!isNonSurfaceDomainLevel(settlement, levelIdentifier) || restrictUniqueID == 0) return;
		SettlementLevelZoneLevelData data = SettlementLevelZoneLevelData.get(settlement.getLevel(), false);
		if (data != null) data.removeEntry(settlement.uniqueID, SettlementLevelZoneLevelData.ZoneKind.RESTRICT, levelIdentifier, restrictUniqueID);
	}

	public static List<Rectangle> getRestrictZoneGeometry(ServerSettlementData settlement, LevelIdentifier levelIdentifier, int restrictUniqueID) {
		if (settlement == null || levelIdentifier == null || restrictUniqueID == 0) return Collections.emptyList();
		SettlementLevelZoneLevelData.Entry entry = getDataEntry(settlement, SettlementLevelZoneLevelData.ZoneKind.RESTRICT, levelIdentifier, restrictUniqueID);
		if (entry != null) return entry.rectangles;
		RestrictZone zone = settlement.getRestrictZone(restrictUniqueID);
		if (zone == null) return Collections.emptyList();
		return copyRectangles(zone.getTileRectangles());
	}

	public static void setWorkZoneGeometry(ServerSettlementData settlement, LevelIdentifier levelIdentifier, int zoneID, int uniqueID, Collection<Rectangle> rectangles) {
		if (!isNonSurfaceDomainLevel(settlement, levelIdentifier) || uniqueID == 0) return;
		SettlementLevelZoneLevelData data = SettlementLevelZoneLevelData.get(settlement.getLevel(), true);
		if (data == null) return;
		data.setEntry(settlement.uniqueID, SettlementLevelZoneLevelData.ZoneKind.WORK, levelIdentifier, uniqueID, zoneID, normalizeRectangles(settlement, levelIdentifier, rectangles));
		if (Logging.logEnabled) Logging.logMessage("[LevelZones] Saved work zone geometry settlement=" + settlement.uniqueID + " level=" + levelIdentifier + " zoneID=" + zoneID + " uniqueID=" + uniqueID);
	}

	public static void removeWorkZoneGeometry(ServerSettlementData settlement, LevelIdentifier levelIdentifier, int uniqueID) {
		if (!isNonSurfaceDomainLevel(settlement, levelIdentifier) || uniqueID == 0) return;
		SettlementLevelZoneLevelData data = SettlementLevelZoneLevelData.get(settlement.getLevel(), false);
		if (data != null) data.removeEntry(settlement.uniqueID, SettlementLevelZoneLevelData.ZoneKind.WORK, levelIdentifier, uniqueID);
	}

	public static List<SettlementLevelZoneLevelData.Entry> getWorkZones(ServerSettlementData settlement, LevelIdentifier levelIdentifier) {
		if (settlement == null || levelIdentifier == null) return Collections.emptyList();
		// Surface zones stay owned by vanilla SettlementWorkZoneManager. This registry stores
		// only non-surface geometry to avoid duplicating vanilla zone state.
		if (settlement.getLevel().getIdentifier().equals(levelIdentifier)) return Collections.emptyList();
		SettlementLevelZoneLevelData data = SettlementLevelZoneLevelData.get(settlement.getLevel(), false);
		if (data == null) return Collections.emptyList();
		return data.getEntries(settlement.uniqueID, SettlementLevelZoneLevelData.ZoneKind.WORK, levelIdentifier);
	}

	public static boolean isTileInWorkZone(ServerSettlementData settlement, LevelIdentifier levelIdentifier, int uniqueID, int tileX, int tileY) {
		if (settlement == null || levelIdentifier == null || uniqueID == 0) return false;
		if (settlement.getLevel().getIdentifier().equals(levelIdentifier)) {
			SettlementWorkZone zone = settlement.getWorkZones().getZone(uniqueID);
			return zone != null && zone.containsTile(tileX, tileY);
		}
		SettlementLevelZoneLevelData.Entry entry = getDataEntry(settlement, SettlementLevelZoneLevelData.ZoneKind.WORK, levelIdentifier, uniqueID);
		return entry != null && contains(entry.rectangles, tileX, tileY);
	}

	public static void removeSettlement(ServerSettlementData settlement) {
		if (settlement == null) return;
		SettlementLevelZoneLevelData data = SettlementLevelZoneLevelData.get(settlement.getLevel(), false);
		if (data != null) data.clearSettlement(settlement.uniqueID);
	}

	private static SettlementLevelZoneLevelData.Entry getDataEntry(ServerSettlementData settlement, SettlementLevelZoneLevelData.ZoneKind kind, LevelIdentifier levelIdentifier, int uniqueID) {
		SettlementLevelZoneLevelData data = SettlementLevelZoneLevelData.get(settlement.getLevel(), false);
		return data == null ? null : data.getEntry(settlement.uniqueID, kind, levelIdentifier, uniqueID);
	}

	private static boolean isNonSurfaceDomainLevel(ServerSettlementData settlement, LevelIdentifier levelIdentifier) {
		if (settlement == null || levelIdentifier == null || settlement.getLevel().getIdentifier().equals(levelIdentifier)) return false;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		return domain != null && domain.containsLevel(levelIdentifier);
	}

	private static ArrayList<Rectangle> normalizeRectangles(ServerSettlementData settlement, LevelIdentifier levelIdentifier, Collection<Rectangle> rectangles) {
		ArrayList<Rectangle> result = new ArrayList<>();
		if (settlement == null || levelIdentifier == null || rectangles == null) return result;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null) return result;
		SettlementLevelType type = domain.getLevelType(levelIdentifier);
		Rectangle bounds = type == null ? null : domain.getTileBounds(type);
		if (bounds == null) return result;
		for (Rectangle rectangle : rectangles) {
			if (rectangle == null || rectangle.width <= 0 || rectangle.height <= 0) continue;
			Rectangle clipped = rectangle.intersection(bounds);
			if (clipped.width > 0 && clipped.height > 0) result.add(clipped);
		}
		return result;
	}

	private static boolean contains(Collection<Rectangle> rectangles, int tileX, int tileY) {
		if (rectangles == null) return false;
		for (Rectangle rectangle : rectangles) {
			if (rectangle != null && rectangle.contains(tileX, tileY)) return true;
		}
		return false;
	}

	private static List<Rectangle> copyRectangles(Iterable<Rectangle> rectangles) {
		ArrayList<Rectangle> result = new ArrayList<>();
		if (rectangles == null) return result;
		for (Rectangle rectangle : rectangles) {
			if (rectangle != null && rectangle.width > 0 && rectangle.height > 0) result.add(new Rectangle(rectangle));
		}
		return Collections.unmodifiableList(result);
	}
}
