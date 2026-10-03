package opusliews.zones;

import necesse.engine.util.LevelIdentifier;
import necesse.level.maps.levelData.settlementData.zones.SettlementWorkZone;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public final class SettlementZoneClientCache {
	private static final HashMap<Integer, Snapshot> snapshots = new HashMap<>();

	private SettlementZoneClientCache() {
	}

	public static synchronized void apply(
			int settlementUniqueID,
			Map<Integer, LevelIdentifier> restrictOwners,
			Map<Integer, LevelIdentifier> workOwners,
			Map<Integer, SettlementWorkZone> workZones
	) {
		snapshots.put(settlementUniqueID, new Snapshot(
				new HashMap<>(restrictOwners),
				new HashMap<>(workOwners),
				new HashMap<>(workZones)
		));
	}

	public static synchronized LevelIdentifier getRestrictOwner(int settlementUniqueID, int uniqueID) {
		Snapshot snapshot = snapshots.get(settlementUniqueID);
		return snapshot == null ? null : snapshot.restrictOwners.get(uniqueID);
	}

	public static synchronized LevelIdentifier getWorkOwner(int settlementUniqueID, int uniqueID) {
		Snapshot snapshot = snapshots.get(settlementUniqueID);
		return snapshot == null ? null : snapshot.workOwners.get(uniqueID);
	}

	public static synchronized boolean isWorkZoneOnLevel(int settlementUniqueID, int uniqueID, LevelIdentifier currentLevel) {
		LevelIdentifier owner = getWorkOwner(settlementUniqueID, uniqueID);
		if (owner == null) return currentLevel != null && currentLevel.isSurface();
		return owner.equals(currentLevel);
	}

	public static synchronized boolean isRestrictZoneOnLevel(int settlementUniqueID, int uniqueID, LevelIdentifier currentLevel) {
		LevelIdentifier owner = getRestrictOwner(settlementUniqueID, uniqueID);
		if (owner == null) return currentLevel != null && currentLevel.isSurface();
		return owner.equals(currentLevel);
	}

	public static synchronized HashSet<Integer> getCustomWorkZoneIDs(int settlementUniqueID) {
		Snapshot snapshot = snapshots.get(settlementUniqueID);
		return snapshot == null ? new HashSet<>() : new HashSet<>(snapshot.workZones.keySet());
	}

	public static synchronized HashMap<Integer, SettlementWorkZone> getCustomWorkZones(int settlementUniqueID) {
		Snapshot snapshot = snapshots.get(settlementUniqueID);
		return snapshot == null ? new HashMap<>() : new HashMap<>(snapshot.workZones);
	}

	public static synchronized void setLocalWorkOwner(int settlementUniqueID, int uniqueID, LevelIdentifier owner) {
		Snapshot snapshot = snapshots.computeIfAbsent(settlementUniqueID, ignored -> new Snapshot(new HashMap<>(), new HashMap<>(), new HashMap<>()));
		if (owner == null) snapshot.workOwners.remove(uniqueID);
		else snapshot.workOwners.put(uniqueID, owner);
	}

	public static synchronized void setLocalRestrictOwner(int settlementUniqueID, int uniqueID, LevelIdentifier owner) {
		Snapshot snapshot = snapshots.computeIfAbsent(settlementUniqueID, ignored -> new Snapshot(new HashMap<>(), new HashMap<>(), new HashMap<>()));
		if (owner == null) snapshot.restrictOwners.remove(uniqueID);
		else snapshot.restrictOwners.put(uniqueID, owner);
	}

	public static synchronized LevelIdentifier findWorkOwner(int uniqueID) {
		for (Snapshot snapshot : snapshots.values()) {
			LevelIdentifier owner = snapshot.workOwners.get(uniqueID);
			if (owner != null) return owner;
		}
		return null;
	}

	public static synchronized LevelIdentifier findRestrictOwner(int uniqueID) {
		for (Snapshot snapshot : snapshots.values()) {
			LevelIdentifier owner = snapshot.restrictOwners.get(uniqueID);
			if (owner != null) return owner;
		}
		return null;
	}

	private static final class Snapshot {
		final HashMap<Integer, LevelIdentifier> restrictOwners;
		final HashMap<Integer, LevelIdentifier> workOwners;
		final HashMap<Integer, SettlementWorkZone> workZones;

		Snapshot(
				HashMap<Integer, LevelIdentifier> restrictOwners,
				HashMap<Integer, LevelIdentifier> workOwners,
				HashMap<Integer, SettlementWorkZone> workZones
		) {
			this.restrictOwners = restrictOwners;
			this.workOwners = workOwners;
			this.workZones = workZones;
		}
	}
}
