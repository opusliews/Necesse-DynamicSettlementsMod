package opusliews.multilevelsettlement;

import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.util.LevelIdentifier;
import necesse.engine.util.PointHashMap;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.level.maps.levelData.settlementData.LevelSettler;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementBed;
import opusliews.logging.Logging;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class SettlerBedReservationSystem {
	private static final String hasReservationKey = "dsHasReservedBed";
	private static final String settlementKey = "dsReservedBedSettlement";
	private static final String levelKey = "dsReservedBedLevel";
	private static final String tileXKey = "dsReservedBedX";
	private static final String tileYKey = "dsReservedBedY";

	private static final Map<HumanMob, BedReservation> reservations = Collections.synchronizedMap(new WeakHashMap<>());
	private static Field settlementBedsField;

	private SettlerBedReservationSystem() {
	}

	public static void captureBeforeDowned(HumanMob human) {
		if (human == null || !human.isServer() || !human.isSettler() || human.levelSettler == null) return;
		SettlementBed bed = human.levelSettler.getBed();
		if (bed == null || human.levelSettler.data == null) return;

		LevelIdentifier bedLevel = SettlementCaveBedSystem.getBedLevelIdentifier(human.levelSettler, bed);
		if (bedLevel == null) return;

		BedReservation reservation = new BedReservation(human.levelSettler.data.uniqueID, bedLevel, bed.tileX, bed.tileY);
		reservations.put(human, reservation);
		if (Logging.logEnabled) {
			Logging.logMessage("[BedReservation] Preserved bed before settler was downed settler=" + human.getUniqueID()
					+ " settlement=" + reservation.settlementUniqueID
					+ " level=" + reservation.level
					+ " bed=" + reservation.tileX + "," + reservation.tileY);
		}
	}

	public static SettlementBed adjustSelectedBed(LevelSettler settler, SettlementBed selected) {
		if (settler == null || settler.data == null) return selected;
		HumanMob human = getHuman(settler);
		BedReservation own = human == null ? null : reservations.get(human);
		if (own != null && own.settlementUniqueID == settler.data.uniqueID) {
			SettlementBed reserved = resolveReservedBed(settler.data, own);
			if (reserved != null && reserved.isValidBed() && settler.settler.isValidBed(reserved)) {
				LevelSettler occupant = reserved.getSettler();
				if (occupant == null || occupant.mobUniqueID == settler.mobUniqueID) return reserved;
			}
			clearReservation(human, "reserved bed became unavailable before revival");
		}

		if (selected == null || !isReservedForOther(settler, selected)) return selected;
		if (selected instanceof SettlementCaveBed) return SettlementCaveBedSystem.findBestCaveBedForSettler(settler);
		return findBestSurfaceBed(settler);
	}

	public static boolean isReservedForOther(LevelSettler settler, SettlementBed bed) {
		if (settler == null || settler.data == null || bed == null) return false;
		LevelIdentifier bedLevel = SettlementCaveBedSystem.getBedLevelIdentifier(settler, bed);
		if (bedLevel == null) return false;

		synchronized (reservations) {
			for (Map.Entry<HumanMob, BedReservation> entry : reservations.entrySet()) {
				HumanMob owner = entry.getKey();
				BedReservation reservation = entry.getValue();
				if (owner == null || reservation == null || owner.getUniqueID() == settler.mobUniqueID) continue;
				if (reservation.settlementUniqueID != settler.data.uniqueID) continue;
				if (!reservation.level.equals(bedLevel)) continue;
				if (reservation.tileX == bed.tileX && reservation.tileY == bed.tileY) return true;
			}
		}
		return false;
	}

	public static void onBedAssigned(LevelSettler settler, SettlementBed bed, boolean success) {
		if (!success || settler == null || bed == null) return;
		HumanMob human = getHuman(settler);
		if (human == null) return;
		BedReservation reservation = reservations.get(human);
		if (reservation == null || reservation.settlementUniqueID != settler.data.uniqueID) return;
		LevelIdentifier bedLevel = SettlementCaveBedSystem.getBedLevelIdentifier(settler, bed);
		if (bedLevel == null) return;
		if (!reservation.level.equals(bedLevel) || reservation.tileX != bed.tileX || reservation.tileY != bed.tileY) return;
		clearReservation(human, "original bed restored");
	}

	public static void addSaveData(HumanMob human, SaveData save) {
		if (human == null || save == null) return;
		BedReservation reservation = reservations.get(human);
		if (reservation == null) return;
		save.addBoolean(hasReservationKey, true);
		save.addInt(settlementKey, reservation.settlementUniqueID);
		save.addSafeString(levelKey, reservation.level.stringID);
		save.addInt(tileXKey, reservation.tileX);
		save.addInt(tileYKey, reservation.tileY);
	}

	public static void applyLoadData(HumanMob human, LoadData save) {
		if (human == null || save == null) return;
		if (!save.getBoolean(hasReservationKey, false, false)) {
			reservations.remove(human);
			return;
		}

		int settlementUniqueID = save.getInt(settlementKey, 0, false);
		String levelStringID = save.getSafeString(levelKey, null, false);
		int tileX = save.getInt(tileXKey, 0, false);
		int tileY = save.getInt(tileYKey, 0, false);
		if (settlementUniqueID == 0 || levelStringID == null) {
			reservations.remove(human);
			return;
		}

		reservations.put(human, new BedReservation(settlementUniqueID, new LevelIdentifier(levelStringID), tileX, tileY));
	}

	private static SettlementBed resolveReservedBed(ServerSettlementData settlement, BedReservation reservation) {
		if (settlement == null || reservation == null) return null;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain != null && domain.getLevelType(reservation.level) == SettlementLevelType.CAVE) {
			return SettlementCaveBedSystem.getOrCreateCaveBed(settlement, reservation.tileX, reservation.tileY);
		}
		if (settlement.getLevel() != null && settlement.getLevel().getIdentifier().equals(reservation.level)) {
			return settlement.addOrValidateBed(reservation.tileX, reservation.tileY);
		}
		return null;
	}

	private static SettlementBed findBestSurfaceBed(LevelSettler settler) {
		PointHashMap beds = getSurfaceBeds(settler.data);
		if (beds == null) return null;
		SettlementBed best = null;
		int bestScore = Integer.MIN_VALUE;
		for (Object value : beds.values()) {
			if (!(value instanceof SettlementBed)) continue;
			SettlementBed bed = (SettlementBed)value;
			if (bed.getSettler() != null || bed.isLocked || !settler.settler.isValidBed(bed) || isReservedForOther(settler, bed)) continue;
			int score = bed.getHappinessScore();
			if (best == null || score > bestScore) {
				best = bed;
				bestScore = score;
			}
		}
		return best;
	}

	private static PointHashMap getSurfaceBeds(ServerSettlementData settlement) {
		try {
			if (settlementBedsField == null) {
				settlementBedsField = ServerSettlementData.class.getDeclaredField("beds");
				settlementBedsField.setAccessible(true);
			}
			Object value = settlementBedsField.get(settlement);
			return value instanceof PointHashMap ? (PointHashMap)value : null;
		} catch (ReflectiveOperationException e) {
			if (Logging.logEnabled) Logging.logMessage("[BedReservation] Could not inspect settlement beds: " + e.getMessage());
			return null;
		}
	}

	private static HumanMob getHuman(LevelSettler settler) {
		if (settler == null) return null;
		Object mob = settler.getMob();
		return mob instanceof HumanMob ? (HumanMob)mob : null;
	}

	private static void clearReservation(HumanMob human, String reason) {
		if (human == null) return;
		BedReservation removed = reservations.remove(human);
		if (removed != null && Logging.logEnabled) {
			Logging.logMessage("[BedReservation] Cleared reservation settler=" + human.getUniqueID() + " reason=" + reason);
		}
	}

	private static final class BedReservation {
		final int settlementUniqueID;
		final LevelIdentifier level;
		final int tileX;
		final int tileY;

		BedReservation(int settlementUniqueID, LevelIdentifier level, int tileX, int tileY) {
			this.settlementUniqueID = settlementUniqueID;
			this.level = level;
			this.tileX = tileX;
			this.tileY = tileY;
		}
	}
}
