package opusliews.forge;

import necesse.engine.network.server.ServerClient;
import necesse.entity.objectEntity.ProcessingForgeObjectEntity;
import necesse.inventory.InventoryItem;
import necesse.inventory.InventoryRange;
import necesse.inventory.container.Container;
import necesse.inventory.container.object.FueledProcessingOEInventoryContainer;
import opusliews.crafting.CraftingStoragePool;
import opusliews.logging.Logging;
import opusliews.object.CraftingTaskBoardObjectEntity;
import opusliews.object.DynamicCraftingStationObjectEntity;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ForgeManualCleanupSystem {
	private ForgeManualCleanupSystem() {
	}

	public static void serverTick(ProcessingForgeObjectEntity forge) {
		if (forge == null || !forge.getLevel().isServer()) return;
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
		for (Object object : forge.getLevel().entityManager.objectEntities) {
			if (object instanceof CraftingTaskBoardObjectEntity
					&& ((CraftingTaskBoardObjectEntity)object).hasForgeAssignment(forgePoint)) {
				return true;
			}
		}
		return false;
	}

	private static List<DynamicCraftingStationObjectEntity> getLinkedStations(ProcessingForgeObjectEntity forge) {
		Point forgePoint = new Point(forge.tileX, forge.tileY);
		ArrayList<DynamicCraftingStationObjectEntity> stations = new ArrayList<>();
		for (Object object : forge.getLevel().entityManager.objectEntities) {
			if (!(object instanceof DynamicCraftingStationObjectEntity)) continue;
			DynamicCraftingStationObjectEntity station = (DynamicCraftingStationObjectEntity)object;
			if (station.hasLinkedForge(forgePoint)) stations.add(station);
		}
		stations.sort(Comparator
				.comparingInt((DynamicCraftingStationObjectEntity station) -> station.tileX)
				.thenComparingInt(station -> station.tileY));
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
}
