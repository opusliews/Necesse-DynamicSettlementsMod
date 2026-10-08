package opusliews.forge;

import necesse.engine.network.server.ServerClient;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.entity.objectEntity.ProcessingForgeObjectEntity;
import necesse.inventory.InventoryItem;
import necesse.inventory.InventoryRange;
import necesse.inventory.container.Container;
import necesse.inventory.container.object.FueledProcessingOEInventoryContainer;
import necesse.level.maps.Level;
import opusliews.crafting.CraftingStoragePool;
import opusliews.logging.Logging;
import opusliews.object.CraftingTaskBoardObjectEntity;
import opusliews.object.DynamicCraftingStationObjectEntity;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class ForgeManualCleanupSystem {
	// The expensive level-wide relationship discovery is cached. The lightweight
	// inventory-state gate still runs every forge tick so closing a manually-used
	// forge cannot race with automation and leave blocking items behind.
	private static final Object CACHE_LOCK = new Object();
	private static final WeakHashMap<Level, LevelTopologyCache> topologyCaches = new WeakHashMap<>();

	private ForgeManualCleanupSystem() {
	}

	public static void serverTick(ProcessingForgeObjectEntity forge) {

		if (forge == null || !forge.getLevel().isServer()) return;
		if (!hasCleanupCandidate(forge)) return;
		if (isPlayerUsingForge(forge)) return;
		if (isAutomationAssigned(forge)) return;

		List<DynamicCraftingStationObjectEntity> stations = getLinkedStations(forge);
		if (stations.isEmpty()) return;

		if (forge.getNextProcessTask() != null) {
			if (hasOutputItems(forge)
					&& ForgeHeatSystem.getRemainingFuelTime(forge) > 0
					&& !forge.isFuelRunning()) {
				boolean drained = moveOutputItems(forge, stations);
				if (drained) {
					forge.forceNextUpdate();
					forge.inventory.markFullDirty();
					Logging.logMessage("[ForgeCleanup] Drained blocking manual outputs at "
							+ forge.tileX + "," + forge.tileY);
				}
			}
			return;
		}

		boolean changed = moveOutputItems(forge, stations);
		if (changed) {
			forge.forceNextUpdate();
			forge.inventory.markFullDirty();

			if (forge.getNextProcessTask() != null) {
				Logging.logMessage("[ForgeCleanup] Drained manual outputs and resumed pending forge batch at "
						+ forge.tileX + "," + forge.tileY);
				return;
			}
		}

		boolean returnedInputs = moveInputItems(forge, stations);
		if (returnedInputs) {
			forge.forceNextUpdate();
			forge.inventory.markFullDirty();
		}

		if (changed || returnedInputs) {
			Logging.logMessage("[ForgeCleanup] Cleared unattended manual forge items at "
					+ forge.tileX + "," + forge.tileY
					+ " outputsMoved=" + changed
					+ " inputsMoved=" + returnedInputs);
		}

	}

	private static boolean hasCleanupCandidate(ProcessingForgeObjectEntity forge) {
		if (forge.getNextProcessTask() != null) {
			return hasOutputItems(forge)
					&& ForgeHeatSystem.getRemainingFuelTime(forge) > 0
					&& !forge.isFuelRunning();
		}

		int firstNonFuelSlot = forge.fuelSlots;
		for (int slot = firstNonFuelSlot; slot < forge.inventory.getSize(); slot++) {
			if (!forge.inventory.isSlotClear(slot)) return true;
		}
		return false;
	}

	/**
	 * Invalidates only transient topology data. Nothing here is persisted or
	 * synchronized to clients; the authoritative link/assignment state remains in
	 * the object entities and is rebuilt lazily after load or configuration edits.
	 */
	public static void onStationForgeLinksChanged(DynamicCraftingStationObjectEntity station) {
		if (station == null || station.getLevel() == null || !station.getLevel().isServer()) return;
		invalidateLevelTopology(station.getLevel());
	}

	public static void onBoardForgeAssignmentsChanged(CraftingTaskBoardObjectEntity board) {
		if (board == null || board.getLevel() == null || !board.getLevel().isServer()) return;
		invalidateLevelTopology(board.getLevel());
	}

	private static void invalidateLevelTopology(Level level) {
		synchronized (CACHE_LOCK) {
			LevelTopologyCache cache = topologyCaches.get(level);
			if (cache != null) cache.dirty = true;
		}
	}

	private static LevelTopologyCache getTopologyCache(Level level) {
		synchronized (CACHE_LOCK) {
			LevelTopologyCache cache = topologyCaches.get(level);
			if (cache == null) {
				cache = new LevelTopologyCache();
				topologyCaches.put(level, cache);
			}
			if (cache.dirty) rebuildTopologyCache(level, cache);
			return cache;
		}
	}

	private static void rebuildTopologyCache(Level level, LevelTopologyCache cache) {

		HashMap<Long, ArrayList<Point>> stationsByForge = new HashMap<>();
		ArrayList<Point> boards = new ArrayList<>();

		for (Object object : level.entityManager.objectEntities) {
			if (object instanceof DynamicCraftingStationObjectEntity) {
				DynamicCraftingStationObjectEntity station = (DynamicCraftingStationObjectEntity)object;
				Point stationPoint = new Point(station.tileX, station.tileY);
				for (Point forgePoint : station.getLinkedForges()) {
					long key = pointKey(forgePoint.x, forgePoint.y);
					ArrayList<Point> points = stationsByForge.get(key);
					if (points == null) {
						points = new ArrayList<>();
						stationsByForge.put(key, points);
					}
					points.add(new Point(stationPoint));
				}
			}
			if (object instanceof CraftingTaskBoardObjectEntity) {
				CraftingTaskBoardObjectEntity board = (CraftingTaskBoardObjectEntity)object;
				boards.add(new Point(board.tileX, board.tileY));
			}
		}

		Comparator<Point> pointOrder = Comparator.comparingInt((Point point) -> point.x)
				.thenComparingInt(point -> point.y);
		boards.sort(pointOrder);
		for (ArrayList<Point> points : stationsByForge.values()) points.sort(pointOrder);

		cache.stationsByForge = stationsByForge;
		cache.boardPoints = boards;
		cache.dirty = false;

	}

	private static long pointKey(int x, int y) {
		return ((long)x << 32) ^ (y & 0xffffffffL);
	}

	private static boolean isPlayerUsingForge(ProcessingForgeObjectEntity forge) {

		for (Object object : forge.getLevel().getServer().getClients()) {
			if (!(object instanceof ServerClient)) continue;
			ServerClient client = (ServerClient)object;
			Container container = client.getContainer();
			if (!(container instanceof FueledProcessingOEInventoryContainer)) continue;
			FueledProcessingOEInventoryContainer forgeContainer = (FueledProcessingOEInventoryContainer)container;
			if (forgeContainer.fueledProcessingObjectEntity == forge) return true;
		}
		return false;

	}

	private static boolean isAutomationAssigned(ProcessingForgeObjectEntity forge) {

		Point forgePoint = new Point(forge.tileX, forge.tileY);
		LevelTopologyCache cache = getTopologyCache(forge.getLevel());
		boolean stale = false;
		for (Point boardPoint : cache.boardPoints) {
			ObjectEntity objectEntity = forge.getLevel().entityManager.getObjectEntity(boardPoint.x, boardPoint.y);
			if (!(objectEntity instanceof CraftingTaskBoardObjectEntity)) {
				stale = true;
				continue;
			}
			if (((CraftingTaskBoardObjectEntity)objectEntity).hasForgeAssignment(forgePoint)) return true;
		}
		if (stale) invalidateLevelTopology(forge.getLevel());
		return false;

	}

	private static List<DynamicCraftingStationObjectEntity> getLinkedStations(ProcessingForgeObjectEntity forge) {

		Point forgePoint = new Point(forge.tileX, forge.tileY);
		LevelTopologyCache cache = getTopologyCache(forge.getLevel());
		List<Point> stationPoints = cache.stationsByForge.get(pointKey(forge.tileX, forge.tileY));
		if (stationPoints == null || stationPoints.isEmpty()) return new ArrayList<>();

		ArrayList<DynamicCraftingStationObjectEntity> stations = new ArrayList<>();
		boolean stale = false;
		for (Point stationPoint : stationPoints) {
			ObjectEntity objectEntity = forge.getLevel().entityManager.getObjectEntity(stationPoint.x, stationPoint.y);
			if (!(objectEntity instanceof DynamicCraftingStationObjectEntity)) {
				stale = true;
				continue;
			}
			DynamicCraftingStationObjectEntity station = (DynamicCraftingStationObjectEntity)objectEntity;
			if (!station.hasLinkedForge(forgePoint)) {
				stale = true;
				continue;
			}
			stations.add(station);
		}
		if (stale) invalidateLevelTopology(forge.getLevel());
		return stations;

	}

	private static boolean hasOutputItems(ProcessingForgeObjectEntity forge) {

		int outputStart = forge.fuelSlots + forge.inputSlots;
		for (int slot = outputStart; slot < forge.inventory.getSize(); slot++) {
			if (!forge.inventory.isSlotClear(slot)) return true;
		}
		return false;

	}

	private static boolean moveOutputItems(
			ProcessingForgeObjectEntity forge,
			List<DynamicCraftingStationObjectEntity> stations
	) {

		boolean changed = false;
		int outputStart = forge.fuelSlots + forge.inputSlots;
		for (int slot = outputStart; slot < forge.inventory.getSize(); slot++) {
			InventoryItem item = forge.inventory.getItem(slot);
			if (item == null) continue;
			InventoryItem remaining = item.copy();
			int before = remaining.getAmount();
			addToLinkedStorages(forge, stations, remaining, false);
			if (remaining.getAmount() >= before) continue;
			setForgeSlot(forge, slot, remaining);
			changed = true;
		}
		return changed;

	}

	private static boolean moveInputItems(
			ProcessingForgeObjectEntity forge,
			List<DynamicCraftingStationObjectEntity> stations
	) {

		boolean changed = false;
		int inputStart = forge.fuelSlots;
		int inputEnd = forge.fuelSlots + forge.inputSlots;
		for (int slot = inputStart; slot < inputEnd; slot++) {
			InventoryItem item = forge.inventory.getItem(slot);
			if (item == null) continue;
			InventoryItem remaining = item.copy();
			int before = remaining.getAmount();
			addToLinkedStorages(forge, stations, remaining, true);
			if (remaining.getAmount() >= before) continue;
			setForgeSlot(forge, slot, remaining);
			changed = true;
		}
		return changed;

	}

	private static void addToLinkedStorages(
			ProcessingForgeObjectEntity forge,
			List<DynamicCraftingStationObjectEntity> stations,
			InventoryItem remaining,
			boolean input
	) {

		for (DynamicCraftingStationObjectEntity station : stations) {
			List<Point> points = input ? station.getInputStorages() : station.getOutputStorages();
			for (Point point : points) {
				if (remaining.getAmount() <= 0) return;
				InventoryRange range = CraftingStoragePool.getLinkedStorageRange(forge.getLevel(), point);
				if (range == null) continue;
				range.inventory.addItem(
						forge.getLevel(),
						null,
						remaining,
						range.startSlot,
						range.endSlot,
						input ? "manualforgeinputcleanup" : "manualforgeoutputcleanup"
				);
			}
		}

	}

	private static void setForgeSlot(ProcessingForgeObjectEntity forge, int slot, InventoryItem remaining) {

		if (remaining == null || remaining.getAmount() <= 0) {
			forge.inventory.clearSlot(slot);
		} else {
			forge.inventory.setItem(slot, remaining);
			forge.inventory.markDirty(slot);
		}

	}

	private static final class LevelTopologyCache {
		private boolean dirty = true;
		private Map<Long, ArrayList<Point>> stationsByForge = new HashMap<>();
		private List<Point> boardPoints = new ArrayList<>();
	}
}
