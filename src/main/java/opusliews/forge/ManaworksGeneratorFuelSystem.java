package opusliews.forge;

import necesse.entity.objectEntity.FueledProcessingTechInventoryObjectEntity;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.inventory.InventoryItem;
import necesse.level.maps.Level;
import opusliews.crafting.CraftingStoragePool;
import opusliews.logging.Logging;
import opusliews.object.CraftingTaskBoardObjectEntity;
import opusliews.object.DynamicCraftingStationObjectEntity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Optional Manaworks integration. Discovers connected generators using Manaworks' own mana-grid APIs. */
public final class ManaworksGeneratorFuelSystem {
	private static final String GENERATOR_CLASS = "vdgmana.entity.DemonicGeneratorObjectEntity";
	private static final String GRID_CLASS = "vdgmana.power.ManaGrid";
	private static final String NODE_CLASS = "vdgmana.power.IManaNode";
	private static final int GRID_RANGE = 8;

	private ManaworksGeneratorFuelSystem() {}

	public static void refill(CraftingTaskBoardObjectEntity board, CraftingStoragePool pool) {
		if (board == null || pool == null || !pool.hasInputs() || !board.getLevel().isServer()) return;
		DynamicCraftingStationObjectEntity station = board.getLinkedStationEntity();
		if (station == null) return;
		Set<Long> seen = new HashSet<>();
		for (FueledProcessingTechInventoryObjectEntity forge : station.getCompatibleLinkedForges()) {
			if (!ForgeLinkCompatibility.isManaworks(forge) || ForgeLinkCompatibility.isPowered(forge)) continue;
			try {
				for (ObjectEntity generator : findConnectedGenerators(forge)) {
					long key = ((long)generator.tileX << 32) | (generator.tileY & 0xffffffffL);
					if (!seen.add(key)) continue;
					refillGenerator(generator, pool);
				}
			} catch (ReflectiveOperationException | RuntimeException ex) {
				Logging.logMessage("[ManaworksGeneratorFuel] Failed to inspect grid at forge=" + forge.tileX + "," + forge.tileY + " error=" + ex);
			}
		}
	}

	private static List<ObjectEntity> findConnectedGenerators(FueledProcessingTechInventoryObjectEntity forge) throws ReflectiveOperationException {
		Level level = forge.getLevel();
		ClassLoader loader = forge.getClass().getClassLoader();
		Class<?> gridClass = loader.loadClass(GRID_CLASS);
		Class<?> nodeClass = loader.loadClass(NODE_CLASS);
		Method expand = gridClass.getMethod("expandConnectedNodes", Level.class, ObjectEntity.class, int.class);
		Method sources = gridClass.getMethod("collectPureSourcesAround", Level.class, List.class, int.class);
		Set<ObjectEntity> generators = new HashSet<>();
		// A generator can feed a Forge directly, without intermediate network nodes.
		addGenerators(generators, sources.invoke(null, level, Collections.singletonList(forge), GRID_RANGE));
		// A Forge can also receive mana from sources connected through relay/ley nodes.
		for (int dx = -GRID_RANGE; dx <= GRID_RANGE; dx++) {
			for (int dy = -GRID_RANGE; dy <= GRID_RANGE; dy++) {
				ObjectEntity nearby = level.entityManager.getObjectEntity(forge.tileX + dx, forge.tileY + dy);
				if (nearby == null || !nodeClass.isInstance(nearby)) continue;
				Object connected = expand.invoke(null, level, nearby, GRID_RANGE);
				if (connected instanceof List<?>) addGenerators(generators, sources.invoke(null, level, connected, GRID_RANGE));
				// One reachable network is sufficient: the same network's sources are already included.
				return new ArrayList<>(generators);
			}
		}
		return new ArrayList<>(generators);
	}

	private static void addGenerators(Set<ObjectEntity> generators, Object candidates) {
		if (!(candidates instanceof List<?>)) return;
		for (Object candidate : (List<?>)candidates) {
			if (candidate instanceof ObjectEntity && GENERATOR_CLASS.equals(candidate.getClass().getName()))
				generators.add((ObjectEntity)candidate);
		}
	}

	private static void refillGenerator(ObjectEntity generator, CraftingStoragePool pool) throws ReflectiveOperationException {
		// Its own public predicate is authoritative: logs and spoiled food are rejected.
		Method validFuel = generator.getClass().getMethod("isFuelItem", InventoryItem.class);
		necesse.entity.objectEntity.InventoryObjectEntity inventoryEntity = (necesse.entity.objectEntity.InventoryObjectEntity)generator;
		// Keep the generator topped up modestly; Manaworks uses two input slots (0 and 1).
		int fuelCount = 0;
		for (int i = 0; i < 2; i++) {
			InventoryItem current = inventoryEntity.inventory.getItem(i);
			if (current != null) fuelCount += current.getAmount();
		}
		if (fuelCount >= 10) return;
		for (int i = fuelCount; i < 10; i++) {
			InventoryItem item = pool.takeFirstMatching(candidate -> {
				try {
					return Boolean.TRUE.equals(validFuel.invoke(null, candidate)) && inventoryEntity.inventory.canAddItem(
							generator.getLevel(), null, candidate, 0, 1, "dsmanageneratorfuel") > 0;
				} catch (ReflectiveOperationException ex) { return false; }
			});
			if (item == null) break;
			if (!inventoryEntity.inventory.addItem(generator.getLevel(), null, item, 0, 1, "dsmanageneratorfuel")) {
				if (!pool.addInputItemOrdered(item)) Logging.logMessage("[ManaworksGeneratorFuel] Fuel rollback failed generator=" + generator.tileX + "," + generator.tileY);
				break;
			}
			inventoryEntity.inventory.markFullDirty();
			if (Logging.logEnabled) Logging.logMessage("[ManaworksGeneratorFuel] Inserted " + item.item.getStringID() + " generator=" + generator.tileX + "," + generator.tileY);
		}
	}
}
