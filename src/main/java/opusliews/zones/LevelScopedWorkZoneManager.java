package opusliews.zones;

import necesse.engine.localization.message.GameMessage;
import necesse.engine.network.server.ServerClient;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.util.LevelIdentifier;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementWorkZoneManager;
import necesse.level.maps.levelData.settlementData.zones.SettlementWorkZone;
import necesse.level.maps.levelData.settlementData.zones.SettlementWorkZoneRegistry;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementPersonalityLevelSystem;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class LevelScopedWorkZoneManager extends SettlementWorkZoneManager {
	public final Level level;
	public final LevelIdentifier levelIdentifier;

	public LevelScopedWorkZoneManager(ServerSettlementData settlement, Level level) {
		super(settlement);
		this.level = level;
		this.levelIdentifier = level.getIdentifier();
	}

	public void loadEntries(Iterable<SettlementIndependentZonesLevelData.WorkEntry> entries) {
		zones.clear();
		for (SettlementIndependentZonesLevelData.WorkEntry entry : entries) {
			try {
				SettlementWorkZone zone = SettlementWorkZoneRegistry.getNewZone(entry.zoneID);
				zone.applySaveData(new LoadData(entry.saveScript), zones.values(), 0, 0);
				if (zone.getUniqueID() != entry.uniqueID) {
					if (Logging.logEnabled) Logging.logMessage("[IndependentZones] Work-zone save ID mismatch entry=" + entry.uniqueID + " loaded=" + zone.getUniqueID());
					continue;
				}
				if (!zone.shouldRemove()) {
					zones.put(zone.getUniqueID(), zone);
					zone.init(this);
				}
			}
			catch (RuntimeException error) {
				if (Logging.logEnabled) Logging.logMessage("[IndependentZones] Failed loading work zone level=" + levelIdentifier + " uniqueID=" + entry.uniqueID + " error=" + error.getClass().getSimpleName() + ": " + error.getMessage());
			}
		}
	}

	public SettlementWorkZone createLevelZone(int zoneID, int uniqueID, Rectangle rectangle, Point anchor) {
		if (getZone(uniqueID) != null) return getZone(uniqueID);
		SettlementWorkZone zone = SettlementWorkZoneRegistry.getNewZone(zoneID);
		zone.setUniqueID(uniqueID);
		Rectangle bounds = data.networkData.getTileRectangle();
		boolean changed = zone.expandZone(level, rectangle, anchor, (x, y) -> !bounds.contains(x, y) || zones.values().stream().anyMatch(other -> ((SettlementWorkZone)other).containsTile(x, y)));
		if (!changed || zone.shouldRemove()) {
			zone.remove();
			return null;
		}
		zone.generateDefaultName(zones.values());
		zones.put(uniqueID, zone);
		zone.init(this);
		return zone;
	}

	public SettlementWorkZone expandLevelZone(int uniqueID, Rectangle rectangle, Point anchor) {
		SettlementWorkZone zone = getZone(uniqueID);
		if (zone == null) return null;
		Rectangle bounds = data.networkData.getTileRectangle();
		zone.expandZone(level, rectangle, anchor, (x, y) -> !bounds.contains(x, y) || zones.values().stream().anyMatch(other -> other != zone && ((SettlementWorkZone)other).containsTile(x, y)));
		return zone;
	}

	public SettlementWorkZone shrinkLevelZone(int uniqueID, Rectangle rectangle) {
		SettlementWorkZone zone = getZone(uniqueID);
		if (zone == null) return null;
		if (zone.shrinkZone(level, rectangle) && zone.shouldRemove()) {
			zones.remove(uniqueID);
			zone.remove();
			return null;
		}
		return zone;
	}

	public SettlementWorkZone removeLevelZone(int uniqueID) {
		SettlementWorkZone zone = getZone(uniqueID);
		if (zone != null) {
			zones.remove(uniqueID);
			zone.remove();
		}
		return zone;
	}

	public void tickSecondLevel() {
		HashMap<Integer, SettlementWorkZone> before = new HashMap<>();
		for (Object value : zones.values()) {
			SettlementWorkZone zone = (SettlementWorkZone)value;
			before.put(zone.getUniqueID(), zone);
		}
		withContext(super::tickSecond);
		for (Integer uniqueID : before.keySet()) {
			if (!zones.containsKey(uniqueID)) {
				SettlementWorkZone removed = before.get(uniqueID);
				if (Logging.logEnabled) Logging.logMessage("[IndependentZonesDebug] Custom work zone disappeared during tick settlement="
						+ data.uniqueID + " level=" + levelIdentifier + " uniqueID=" + uniqueID
						+ " type=" + (removed == null ? "null" : removed.getStringID())
						+ " sizeBefore=" + (removed == null ? -1 : removed.size())
						+ " removedFlag=" + (removed != null && removed.isRemoved())
						+ " shouldRemove=" + (removed != null && removed.shouldRemove()));
				SettlementIndependentZoneSystem.removePersistedWork(data, uniqueID);
			}
		}
		persistAll();
	}

	public void tickJobsLevel() {
		withContext(super::tickJobs);
	}

	public void persistAll() {
		for (Object value : zones.values()) SettlementIndependentZoneSystem.persistWorkZone(data, levelIdentifier, (SettlementWorkZone)value);
	}

	public void persist(SettlementWorkZone zone) {
		if (zone != null && !zone.isRemoved()) SettlementIndependentZoneSystem.persistWorkZone(data, levelIdentifier, zone);
	}

	public void runWithContext(Runnable runnable) {
		withContext(runnable);
	}

	private void withContext(Runnable runnable) {
		Level previous = SettlementPersonalityLevelSystem.getContextLevel(data);
		SettlementPersonalityLevelSystem.beginLevelContext(data, level);
		try {
			runnable.run();
		}
		finally {
			if (previous != null) SettlementPersonalityLevelSystem.beginLevelContext(data, previous);
			else SettlementPersonalityLevelSystem.endBedLevelContext();
		}
	}
}
