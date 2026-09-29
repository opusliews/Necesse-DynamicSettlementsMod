package opusliews.multilevelsettlement;

import necesse.engine.registries.SettlerThoughtRegistry;
import necesse.engine.util.LevelIdentifier;
import necesse.engine.util.PointHashMap;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.LevelSettler;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementBed;
import necesse.level.maps.levelData.settlementData.notifications.SettlementNotificationSeverity;
import necesse.level.maps.levelData.settlementData.settler.SettlerMob;
import opusliews.logging.Logging;

import java.awt.Point;
import java.util.ArrayList;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class SettlementCaveBedSystem {
	public static final String strandedFromSurfaceThoughtStringID = "dsstrandedfromsurface";
	public static final String strandedFromCaveThoughtStringID = "dsstrandedfromcave";
	public static final int strandedHappinessModifier = -20;
	private static final long strandedCacheDuration = 5000L;
	private static final Map<ServerSettlementData, BedState> states = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<HumanMob, StrandedCache> strandedCache = Collections.synchronizedMap(new WeakHashMap<>());
	private static final ThreadLocal<RoomContext> roomContext = new ThreadLocal<>();
	private static Field levelSettlerBedField;
	private static Field settlementBedSettlerField;

	private SettlementCaveBedSystem() {
	}

	public static void registerThought() {
		SettlerThoughtRegistry.registerSettlerThought(strandedFromSurfaceThoughtStringID,
				new necesse.level.maps.levelData.settlementData.settler.thoughts.SimpleSettlerThought("settlement", "strandedfromsurface", strandedHappinessModifier));
		SettlerThoughtRegistry.registerSettlerThought(strandedFromCaveThoughtStringID,
				new necesse.level.maps.levelData.settlementData.settler.thoughts.SimpleSettlerThought("settlement", "strandedfromcave", strandedHappinessModifier));
	}

	public static void logCaveBedScan(ServerSettlementData settlement) {
		if (!Logging.logEnabled || settlement == null) return;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null) return;
		Level cave = domain.getLoadedLevel(SettlementLevelType.CAVE);
		java.awt.Rectangle bounds = domain.getTileBounds(SettlementLevelType.CAVE);
		if (cave == null || bounds == null) return;
		int beds = 0;
		for (int x = bounds.x; x < bounds.x + bounds.width; x++) {
			for (int y = bounds.y; y < bounds.y + bounds.height; y++) {
				if (SettlementBed.isValidBed(cave, x, y)) beds++;
			}
		}
		Logging.logMessage("[CaveBeds] Cave bed scan settlement=" + settlement.uniqueID + " validBeds=" + beds + " bounds=" + bounds);
	}

	public static SettlementCaveBed getOrCreateCaveBed(ServerSettlementData settlement, int tileX, int tileY) {
		if (settlement == null) return null;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null) return null;
		Level cave = domain.getLoadedLevel(SettlementLevelType.CAVE);
		if (cave == null && settlement.getServer() != null && settlement.getServer().world.levelExists(domain.getLevelIdentifier(SettlementLevelType.CAVE))) {
			cave = settlement.getServer().world.getLevel(domain.getLevelIdentifier(SettlementLevelType.CAVE));
		}
		if (cave == null) {
			if (Logging.logEnabled) Logging.logMessage("[CaveBeds] Cannot resolve cave level settlement=" + settlement.uniqueID + " bed=" + tileX + "," + tileY);
			return null;
		}
		if (!domain.isTileWithinBounds(cave.getIdentifier(), tileX, tileY)) {
			if (Logging.logEnabled) Logging.logMessage("[CaveBeds] Rejected cave bed outside settlement bounds settlement=" + settlement.uniqueID + " bed=" + tileX + "," + tileY);
			return null;
		}

		BedState state = getState(settlement);
		SettlementCaveBed existing = (SettlementCaveBed)state.beds.get(tileX, tileY);
		if (existing != null) {
			if (existing.isValidBed()) return existing;
			state.beds.remove(tileX, tileY);
			if (Logging.logEnabled) Logging.logMessage("[CaveBeds] Removed invalid cached cave bed settlement=" + settlement.uniqueID + " bed=" + tileX + "," + tileY);
		}

		SettlementCaveRoom room = (SettlementCaveRoom)state.rooms.get(tileX, tileY);
		if (room == null) room = new SettlementCaveRoom(settlement, state.rooms, cave, tileX, tileY);
		SettlementCaveBed bed = new SettlementCaveBed(settlement, cave, room, tileX, tileY);
		if (!bed.isValidBed()) return null;
		state.beds.put(tileX, tileY, bed);
		if (Logging.logEnabled) Logging.logMessage("[CaveBeds] Registered cave bed settlement=" + settlement.uniqueID + " level=" + cave.getIdentifier() + " bed=" + tileX + "," + tileY);
		return bed;
	}

	public static SettlementCaveBed findBestCaveBedForSettler(LevelSettler settler) {
		if (settler == null || settler.data == null) return null;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settler.data);
		if (domain == null) return null;
		Level cave = domain.getLoadedLevel(SettlementLevelType.CAVE);
		if (cave == null) return null;
		java.awt.Rectangle bounds = domain.getTileBounds(SettlementLevelType.CAVE);
		if (bounds == null) return null;

		SettlementCaveBed best = null;
		int bestScore = Integer.MIN_VALUE;
		for (int x = bounds.x; x < bounds.x + bounds.width; x++) {
			for (int y = bounds.y; y < bounds.y + bounds.height; y++) {
				SettlementCaveBed bed = getOrCreateCaveBed(settler.data, x, y);
				if (bed == null || bed.getSettler() != null || bed.isLocked || !settler.settler.isValidBed(bed)) continue;
				int score = bed.getHappinessScore();
				if (best == null || score > bestScore) {
					best = bed;
					bestScore = score;
				}
			}
		}
		if (best != null && Logging.logEnabled) Logging.logMessage("[CaveBeds] Best cave bed settler=" + settler.mobUniqueID + " bed=" + best.tileX + "," + best.tileY + " score=" + bestScore);
		return best;
	}

	public static boolean assignCaveBed(LevelSettler settler, int tileX, int tileY) {
		if (settler == null || settler.data == null) return false;
		SettlementCaveBed bed = getOrCreateCaveBed(settler.data, tileX, tileY);
		if (bed == null) {
			if (Logging.logEnabled) Logging.logMessage("[CaveBeds] Assignment failed because cave bed is invalid settler=" + settler.mobUniqueID + " bed=" + tileX + "," + tileY);
			return false;
		}
		return assignCaveBed(settler, bed, true);
	}

	public static boolean assignCaveBed(LevelSettler settler, SettlementCaveBed bed, boolean persist) {
		if (settler == null || bed == null || settler.data != bed.data) return false;
		if (!bed.isValidBed()) return false;
		LevelSettler occupied = bed.getSettler();
		if (occupied != null && occupied != settler) {
			if (Logging.logEnabled) Logging.logMessage("[CaveBeds] Assignment rejected because bed is occupied settler=" + settler.mobUniqueID + " occupant=" + occupied.mobUniqueID + " bed=" + bed.tileX + "," + bed.tileY);
			return false;
		}

		SettlementBed previous = settler.getBed();
		if (previous != null && previous != bed) {
			if (!setBedSettler(previous, null)) return false;
		}
		if (!setLevelSettlerBed(settler, bed)) return false;
		bed.setAssignedSettler(settler);
		settler.markRoomDirty();
		if (previous != null && !(previous instanceof SettlementCaveBed)) settler.data.rooms.recalculateStats(previous.tileX, previous.tileY);
		if (bed.getRoom() != null) bed.getRoom().recalculateStats();
		SettlerMob mob = settler.getMob();
		if (mob != null) settler.data.networkData.notifications.removeNotification("nobed", mob);
		updateHome(settler);

		if (persist) {
			SettlementCaveBedLevelData data = SettlementCaveBedLevelData.get(settler.data.getLevel(), true);
			if (data != null) data.setAssignedBed(settler.mobUniqueID, bed.tileX, bed.tileY);
		}
		strandedCache.remove(mob instanceof HumanMob ? (HumanMob)mob : null);
		settler.data.sendEvent(necesse.inventory.container.settlement.events.SettlementSettlersChangedEvent.class);
		if (Logging.logEnabled) Logging.logMessage("[CaveBeds] Assigned cave bed settler=" + settler.mobUniqueID + " bed=" + bed.tileX + "," + bed.tileY + " settlement=" + settler.data.uniqueID);
		return true;
	}

	public static boolean clearCaveBed(LevelSettler settler, boolean persist) {
		if (settler == null || !(settler.getBed() instanceof SettlementCaveBed)) return false;
		SettlementCaveBed old = (SettlementCaveBed)settler.getBed();
		if (!setBedSettler(old, null)) return false;
		if (!setLevelSettlerBed(settler, null)) return false;
		settler.markRoomDirty();
		if (old.getRoom() != null) old.getRoom().recalculateStats();
		SettlerMob mob = settler.getMob();
		if (mob != null && mob.canSubmitNoBedNotification()) {
			settler.data.networkData.notifications.submitNotification("nobed", mob, SettlementNotificationSeverity.NOTE);
		}
		if (persist) {
			SettlementCaveBedLevelData data = SettlementCaveBedLevelData.get(settler.data.getLevel(), false);
			if (data != null) data.clearAssignedBed(settler.mobUniqueID);
		}
		updateHome(settler);
		if (Logging.logEnabled) Logging.logMessage("[CaveBeds] Cleared cave bed settler=" + settler.mobUniqueID + " oldBed=" + old.tileX + "," + old.tileY);
		return true;
	}

	public static void onNonCaveBedAssigned(LevelSettler settler) {
		if (settler == null || settler.data == null || settler.getBed() instanceof SettlementCaveBed) return;
		SettlementCaveBedLevelData data = SettlementCaveBedLevelData.get(settler.data.getLevel(), false);
		if (data != null && data.getAssignedBed(settler.mobUniqueID) != null) {
			data.clearAssignedBed(settler.mobUniqueID);
			if (Logging.logEnabled) Logging.logMessage("[CaveBeds] Cleared persisted cave bed because settler now has surface/no bed settler=" + settler.mobUniqueID);
		}
		SettlerMob mob = settler.getMob();
		if (mob instanceof HumanMob) strandedCache.remove((HumanMob)mob);
	}

	public static void restoreAssignments(ServerSettlementData settlement) {
		if (settlement == null) return;
		BedState state = getState(settlement);
		if (state.restoredAssignments) return;
		SettlementCaveBedLevelData data = SettlementCaveBedLevelData.get(settlement.getLevel(), false);
		if (data == null) {
			state.restoredAssignments = true;
			return;
		}

		boolean pendingSettlers = false;
		for (Map.Entry<Integer, Point> entry : data.getAssignedBedsSnapshot().entrySet()) {
			LevelSettler settler = settlement.getSettler(entry.getKey());
			if (settler == null) {
				pendingSettlers = true;
				if (Logging.logEnabled && !state.restorePendingLogged) Logging.logMessage("[CaveBeds] Deferring cave bed restore until settlers finish loading mob=" + entry.getKey() + " bed=" + entry.getValue().x + "," + entry.getValue().y);
				continue;
			}
			SettlementCaveBed bed = getOrCreateCaveBed(settlement, entry.getValue().x, entry.getValue().y);
			if (bed == null || !assignCaveBed(settler, bed, false)) {
				if (Logging.logEnabled) Logging.logMessage("[CaveBeds] FAILED to restore cave bed assignment settler=" + entry.getKey() + " bed=" + entry.getValue().x + "," + entry.getValue().y);
			}
		}

		if (pendingSettlers) {
			state.restorePendingLogged = true;
			return;
		}

		state.restoredAssignments = true;
		state.restorePendingLogged = false;
		if (Logging.logEnabled && !data.getAssignedBedsSnapshot().isEmpty()) Logging.logMessage("[CaveBeds] Restored persisted cave bed assignments settlement=" + settlement.uniqueID);
	}

	public static void validateAssignedCaveBed(LevelSettler settler) {
		if (settler == null || !(settler.getBed() instanceof SettlementCaveBed)) return;
		SettlementCaveBed bed = (SettlementCaveBed)settler.getBed();
		if (bed.isValidBed()) return;
		if (Logging.logEnabled) Logging.logMessage("[CaveBeds] Assigned cave bed became invalid; clearing settler=" + settler.mobUniqueID + " bed=" + bed.tileX + "," + bed.tileY);
		clearCaveBed(settler, true);
	}

	public static void updateHome(LevelSettler settler) {
		updateAssignedBedHome(settler);
	}

	public static void updateAssignedBedHome(LevelSettler settler) {
		if (settler == null || settler.getBed() == null) return;
		SettlerMob mob = settler.getMob();
		if (mob == null || mob.getMob() == null) return;
		SettlementBed bed = settler.getBed();
		LevelIdentifier bedLevel = getBedLevelIdentifier(settler, bed);
		Level currentLevel = mob.getMob().getLevel();
		if (currentLevel != null && bedLevel != null && currentLevel.getIdentifier().equals(bedLevel)) mob.setHome(new Point(bed.tileX, bed.tileY));
		else mob.setHome(null);
	}

	public static boolean isStranded(HumanMob human) {
		if (human == null || !human.isServer() || human.getLevel() == null || !human.isSettler()) return false;
		ServerSettlementData settlement = human.getSettlerSettlementServerData();
		if (settlement == null) return false;
		LevelSettler levelSettler = settlement.getSettler(human.getUniqueID());
		if (levelSettler == null) return false;
		SettlementBed bed = levelSettler.getBed();
		if (bed == null) return false;
		LevelIdentifier bedLevel = getBedLevelIdentifier(levelSettler, bed);
		if (bedLevel == null || bedLevel.equals(human.getLevel().getIdentifier())) return false;

		long now = human.getWorldEntity().getTime();
		StrandedCache cache = strandedCache.get(human);
		if (cache != null && cache.expiresAt >= now && cache.currentLevel.equals(human.getLevel().getIdentifier()) && cache.bedLevel.equals(bedLevel) && cache.bedX == bed.tileX && cache.bedY == bed.tileY) {
			return cache.stranded;
		}

		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(levelSettler.data);
		boolean stranded = true;
		if (domain == null) {
			if (Logging.logEnabled) Logging.logMessage("[CaveBeds] Stranded check failed because settlement domain is unavailable settler=" + human.getUniqueID() + " currentLevel=" + human.getLevel().getIdentifier() + " bedLevel=" + bedLevel);
		}
		else {
			SettlementLevelType bedType = domain.getLevelType(bedLevel);
			if (bedType == null) {
				if (Logging.logEnabled) Logging.logMessage("[CaveBeds] Stranded check failed because bed level is outside settlement domain settler=" + human.getUniqueID() + " bedLevel=" + bedLevel + " domain=" + domain);
			}
			else {
				stranded = SettlementCrossLevelRouting.findBestTransitionRoute(human, domain, bedType) == null;
			}
		}
		strandedCache.put(human, new StrandedCache(now + strandedCacheDuration, human.getLevel().getIdentifier(), bedLevel, bed.tileX, bed.tileY, stranded));
		if (cache == null || cache.stranded != stranded) {
			if (Logging.logEnabled) Logging.logMessage("[CaveBeds] Stranded state changed settler=" + human.getUniqueID() + " stranded=" + stranded + " currentLevel=" + human.getLevel().getIdentifier() + " bedLevel=" + bedLevel + " bed=" + bed.tileX + "," + bed.tileY);
		}
		return stranded;
	}

	public static String getStrandedThoughtStringID(HumanMob human) {
		if (!isStranded(human)) return null;
		ServerSettlementData settlement = human.getSettlerSettlementServerData();
		if (settlement == null) return null;
		LevelSettler levelSettler = settlement.getSettler(human.getUniqueID());
		if (levelSettler == null) return null;
		SettlementBed bed = levelSettler.getBed();
		if (bed == null) return null;
		LevelIdentifier bedLevel = getBedLevelIdentifier(levelSettler, bed);
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (bedLevel == null || domain == null) return null;
		SettlementLevelType bedLevelType = domain.getLevelType(bedLevel);
		if (bedLevelType == SettlementLevelType.SURFACE) return strandedFromSurfaceThoughtStringID;
		if (bedLevelType == SettlementLevelType.CAVE) return strandedFromCaveThoughtStringID;
		if (Logging.logEnabled) Logging.logMessage("[CaveBeds] Stranded settler has unsupported bed level settler=" + human.getUniqueID() + " bedLevel=" + bedLevel);
		return null;
	}

	public static LevelIdentifier getBedLevelIdentifier(LevelSettler settler, SettlementBed bed) {
		if (bed instanceof SettlementCaveBed) return ((SettlementCaveBed)bed).getBedLevel().getIdentifier();
		return settler == null || settler.data == null ? null : settler.data.getLevel().getIdentifier();
	}

	public static SettlementBed addOrValidateBedForCurrentRoomContext(ServerSettlementData settlement, int tileX, int tileY, boolean addOnlyPlayerPlaced) {
		RoomContext context = roomContext.get();
		if (context == null || context.settlement != settlement) return null;
		if (addOnlyPlayerPlaced && !context.caveLevel.objectLayer.isPlayerPlaced(tileX, tileY)) return null;
		return getOrCreateCaveBed(settlement, tileX, tileY);
	}

	public static boolean hasCaveRoomContext(ServerSettlementData settlement) {
		RoomContext context = roomContext.get();
		return context != null && context.settlement == settlement;
	}

	static void beginCaveRoomCalculation(ServerSettlementData settlement, Level caveLevel) {
		roomContext.set(new RoomContext(settlement, caveLevel));
	}

	static void endCaveRoomCalculation() {
		roomContext.remove();
	}


	public static void onCaveRoomLevelChanged(Level level, int tileX, int tileY) {
		if (level == null || !level.isServer() || !level.isLoadingComplete() || level.getServer() == null) return;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.findDomainQuiet(level.getServer(), level.getIdentifier(), tileX, tileY);
		if (domain == null || domain.getLevelType(level.getIdentifier()) != SettlementLevelType.CAVE) return;
		ServerSettlementData settlement = domain.getSettlement();
		BedState state = states.get(settlement);
		if (state == null || state.beds.isEmpty()) return;

		state.rooms.clear();
		ArrayList<Object> beds = new ArrayList<>(state.beds.values());
		for (Object value : beds) {
			if (!(value instanceof SettlementCaveBed)) continue;
			SettlementCaveBed bed = (SettlementCaveBed)value;
			SettlementCaveRoom room = (SettlementCaveRoom)state.rooms.get(bed.tileX, bed.tileY);
			if (room == null) {
				room = new SettlementCaveRoom(settlement, state.rooms, level, bed.tileX, bed.tileY);
				bed.setCaveRoom(room);
				room.getRoomSize();
			}
			else {
				bed.setCaveRoom(room);
			}
		}

		beds = new ArrayList<>(state.beds.values());
		for (Object value : beds) {
			if (!(value instanceof SettlementCaveBed)) continue;
			SettlementCaveBed bed = (SettlementCaveBed)value;
			SettlementCaveRoom room = (SettlementCaveRoom)state.rooms.get(bed.tileX, bed.tileY);
			if (room != null) bed.setCaveRoom(room);
			LevelSettler settler = bed.getSettler();
			if (settler != null) settler.markRoomDirty();
		}
	}

	public static void invalidateStrandedCache(ServerSettlementData settlement) {
		if (settlement == null) return;
		synchronized (strandedCache) {
			strandedCache.entrySet().removeIf(entry -> {
				HumanMob human = entry.getKey();
				if (human == null || !human.isSettler()) return true;
				ServerSettlementData data = human.getSettlerSettlementServerData();
				return data == settlement;
			});
		}
	}

	private static BedState getState(ServerSettlementData settlement) {
		synchronized (states) {
			return states.computeIfAbsent(settlement, ignored -> new BedState());
		}
	}

	private static boolean setLevelSettlerBed(LevelSettler settler, SettlementBed bed) {
		try {
			if (levelSettlerBedField == null) {
				levelSettlerBedField = LevelSettler.class.getDeclaredField("bed");
				levelSettlerBedField.setAccessible(true);
			}
			levelSettlerBedField.set(settler, bed);
			return true;
		} catch (ReflectiveOperationException e) {
			if (Logging.logEnabled) Logging.logMessage("[CaveBeds] FAILED to set LevelSettler bed via reflection settler=" + settler.mobUniqueID + " error=" + e);
			return false;
		}
	}

	private static boolean setBedSettler(SettlementBed bed, LevelSettler settler) {
		if (bed instanceof SettlementCaveBed) {
			((SettlementCaveBed)bed).setAssignedSettler(settler);
			return true;
		}
		try {
			if (settlementBedSettlerField == null) {
				settlementBedSettlerField = SettlementBed.class.getDeclaredField("settler");
				settlementBedSettlerField.setAccessible(true);
			}
			settlementBedSettlerField.set(bed, settler);
			return true;
		} catch (ReflectiveOperationException e) {
			if (Logging.logEnabled) Logging.logMessage("[CaveBeds] FAILED to update previous SettlementBed occupant via reflection bed=" + bed.tileX + "," + bed.tileY + " error=" + e);
			return false;
		}
	}

	private static final class BedState {
		final PointHashMap beds = new PointHashMap();
		final PointHashMap rooms = new PointHashMap();
		boolean restoredAssignments;
		boolean restorePendingLogged;
	}

	private static final class RoomContext {
		final ServerSettlementData settlement;
		final Level caveLevel;

		RoomContext(ServerSettlementData settlement, Level caveLevel) {
			this.settlement = settlement;
			this.caveLevel = caveLevel;
		}
	}

	private static final class StrandedCache {
		final long expiresAt;
		final LevelIdentifier currentLevel;
		final LevelIdentifier bedLevel;
		final int bedX;
		final int bedY;
		final boolean stranded;

		StrandedCache(long expiresAt, LevelIdentifier currentLevel, LevelIdentifier bedLevel, int bedX, int bedY, boolean stranded) {
			this.expiresAt = expiresAt;
			this.currentLevel = currentLevel;
			this.bedLevel = bedLevel;
			this.bedX = bedX;
			this.bedY = bedY;
			this.stranded = stranded;
		}
	}
}
