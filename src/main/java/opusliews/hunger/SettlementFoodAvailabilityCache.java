package opusliews.hunger;

import necesse.engine.util.LevelIdentifier;
import necesse.inventory.Inventory;
import necesse.inventory.InventoryItem;
import necesse.inventory.InventoryRange;
import necesse.inventory.item.Item;
import necesse.inventory.item.placeableItem.consumableItem.food.FoodConsumableItem;
import necesse.inventory.itemFilter.ItemCategoriesFilter;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementLevelStorageManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Transient server-side cache of which assigned settlement storages currently contain edible food.
 *
 * This cache is deliberately only a pre-filter. Reservation state, restrict zones, movement/reachability
 * and future pickup simulation are still checked live by the callers before food is considered usable.
 * Any cache uncertainty falls back to the old live scan instead of returning a false negative.
 */
public final class SettlementFoodAvailabilityCache {
    private static final long SAFETY_RECONCILE_NANOS = 5_000_000_000L;

    private static final Map<ServerSettlementData, SettlementState> settlementStates =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Inventory, ArrayList<WeakReference<StorageEntry>>> inventoryWatchers =
            Collections.synchronizedMap(new WeakHashMap<>());

    private SettlementFoodAvailabilityCache() {
    }

    /**
     * Returns false only when the cache is confidently current and there is no diet-compatible food
     * anywhere in assigned settlement storage on this level. Returns true on uncertainty so callers
     * safely fall back to their normal live validation.
     */
    public static boolean levelMayContainEdibleFood(ServerSettlementData settlement, Level level, ItemCategoriesFilter dietFilter) {
        try {
            if (settlement == null || level == null) return true;
            SettlementState state = ensureFresh(settlement);
            if (state == null) return true;

            List<StorageEntry> entries;
            synchronized (state) {
                if (state.topologyDirty) return true;
                if (state.uncertainLevels.contains(level.getIdentifier())) return true;
                entries = state.entriesByLevel.get(level.getIdentifier());
                if (entries == null || entries.isEmpty()) return false;
                entries = new ArrayList<>(entries);
            }

            for (StorageEntry entry : entries) {
                int result = entry.compatibleFoodState(dietFilter);
                if (result != StorageEntry.NO_FOOD) return true;
            }
            return false;
        }
        catch (Throwable error) {
            logFailure("levelMayContainEdibleFood", settlement, level, error);
            return true;
        }

    }

    /**
     * Same fail-open rule as levelMayContainEdibleFood, but for one already discovered storage.
     */
    public static boolean storageMayContainEdibleFood(ServerSettlementData settlement, SettlementInventory storage, ItemCategoriesFilter dietFilter) {
        try {
            if (settlement == null || storage == null) return true;
            SettlementState state = ensureFresh(settlement);
            if (state == null) return true;

            StorageEntry match = null;
            synchronized (state) {
                if (state.topologyDirty) return true;
                List<StorageEntry> entries = state.entriesByLevel.get(storage.level == null ? null : storage.level.getIdentifier());
                if (entries == null) return true;
                for (StorageEntry entry : entries) {
                    if (entry.getStorage() == storage) {
                        match = entry;
                        break;
                    }
                }
            }
            if (match == null) return true;
            return match.compatibleFoodState(dietFilter) != StorageEntry.NO_FOOD;
        }
        catch (Throwable error) {
            logFailure("storageMayContainEdibleFood", settlement, storage == null ? null : storage.level, error);
            return true;
        }

    }

    /** Called by inventory mutation patches. This is intentionally cheap for unregistered inventories. */
    public static void onInventorySlotChanged(Inventory inventory, int slot) {
        if (inventory == null || slot < 0) return;

        ArrayList<WeakReference<StorageEntry>> watched;
        synchronized (inventoryWatchers) {
            ArrayList<WeakReference<StorageEntry>> refs = inventoryWatchers.get(inventory);
            if (refs == null || refs.isEmpty()) return;
            watched = new ArrayList<>(refs);
        }

        try {
            boolean removeDead = false;
            for (WeakReference<StorageEntry> reference : watched) {
                StorageEntry entry = reference.get();
                if (entry == null) {
                    removeDead = true;
                    continue;
                }
                entry.refreshSlot(inventory, slot);
            }

            if (removeDead) cleanupWatcher(inventory);
        }
        catch (Throwable error) {
            if (Logging.logEnabled) {
                Logging.logMessage("[FoodCache] Inventory mutation update failed inventory=" + System.identityHashCode(inventory)
                        + " slot=" + slot + " error=" + error.getClass().getSimpleName() + ": " + error.getMessage());
            }
        }

    }

    /** Called by markFullDirty-style mutation paths when an exact changed slot is not available. */
    public static void onInventoryFullyChanged(Inventory inventory) {
        if (inventory == null) return;
        ArrayList<WeakReference<StorageEntry>> watched;
        synchronized (inventoryWatchers) {
            ArrayList<WeakReference<StorageEntry>> refs = inventoryWatchers.get(inventory);
            if (refs == null || refs.isEmpty()) return;
            watched = new ArrayList<>(refs);
        }

        try {
            boolean removeDead = false;
            for (WeakReference<StorageEntry> reference : watched) {
                StorageEntry entry = reference.get();
                if (entry == null) {
                    removeDead = true;
                    continue;
                }
                entry.rescanIfWatching(inventory);
            }
            if (removeDead) cleanupWatcher(inventory);
        }
        catch (Throwable error) {
            if (Logging.logEnabled) {
                Logging.logMessage("[FoodCache] Full inventory mutation update failed inventory=" + System.identityHashCode(inventory)
                        + " error=" + error.getClass().getSimpleName() + ": " + error.getMessage());
            }
        }

    }

    /** Marks assigned-storage topology dirty. Contents remain usable only until the next query rebuilds. */
    public static void invalidateTopology(ServerSettlementData settlement, String reason) {
        if (settlement == null) return;
        SettlementState state = getState(settlement);
        synchronized (state) {
            state.topologyDirty = true;
        }
        if (Logging.logEnabled) {
            Logging.logMessage("[FoodCache] Invalidated topology settlement=" + settlement.uniqueID
                    + " reason=" + (reason == null ? "unknown" : reason));
        }
    }

    /** Explicit cleanup for settlement/world teardown. */
    public static void remove(ServerSettlementData settlement) {
        if (settlement == null) return;
        synchronized (settlementStates) {
            settlementStates.remove(settlement);
        }
    }

    private static SettlementState ensureFresh(ServerSettlementData settlement) {
        SettlementState state = getState(settlement);
        long now = System.nanoTime();
        boolean rebuild;
        synchronized (state) {
            rebuild = state.topologyDirty || now >= state.nextSafetyReconcileNanos;
        }
        if (!rebuild) return state;

        rebuild(settlement, state, now);
        return state;
    }

    private static void rebuild(ServerSettlementData settlement, SettlementState state, long nowNanos) {
        try {
            Map<LevelIdentifier, List<StorageEntry>> rebuilt = new HashMap<>();
            Set<LevelIdentifier> uncertainLevels = new HashSet<>();
            int storageCount = 0;
            int foodSlotCount = 0;

            for (SettlementInventory storage : SettlementLevelStorageManager.getStorage(settlement)) {
                if (storage == null || storage.level == null) continue;
                InventoryRange range;
                try {
                    range = storage.getInventoryRange();
                }
                catch (Throwable error) {
                    uncertainLevels.add(storage.level.getIdentifier());
                    if (Logging.logEnabled) {
                        Logging.logMessage("[FoodCache] Could not resolve storage inventory settlement=" + settlement.uniqueID
                                + " level=" + storage.level.getIdentifier() + " tile=" + storage.tileX + "," + storage.tileY
                                + " error=" + error.getClass().getSimpleName() + ": " + error.getMessage());
                    }
                    continue;
                }
                if (range == null || range.inventory == null) {
                    uncertainLevels.add(storage.level.getIdentifier());
                    continue;
                }

                StorageEntry entry = new StorageEntry(storage, range.inventory, range.startSlot, range.endSlot);
                entry.rescanAll();
                rebuilt.computeIfAbsent(storage.level.getIdentifier(), ignored -> new ArrayList<>()).add(entry);
                registerWatcher(range.inventory, entry);
                storageCount++;
                foodSlotCount += entry.foodSlotCount();
            }

            synchronized (state) {
                state.entriesByLevel = rebuilt;
                state.uncertainLevels = uncertainLevels;
                state.topologyDirty = false;
                state.nextSafetyReconcileNanos = nowNanos + SAFETY_RECONCILE_NANOS;
            }

            if (Logging.logEnabled) {
                Logging.logMessage("[FoodCache] Rebuilt settlement=" + settlement.uniqueID
                        + " storages=" + storageCount + " foodSlots=" + foodSlotCount);
            }
        }
        catch (Throwable error) {
            synchronized (state) {
                // Keep the cache fail-open until a later query can successfully rebuild it.
                state.topologyDirty = true;
                state.nextSafetyReconcileNanos = nowNanos + 1_000_000_000L;
            }
            if (Logging.logEnabled) {
                Logging.logMessage("[FoodCache] Rebuild failed settlement=" + settlement.uniqueID
                        + " error=" + error.getClass().getSimpleName() + ": " + error.getMessage());
            }
        }

    }

    private static SettlementState getState(ServerSettlementData settlement) {
        synchronized (settlementStates) {
            return settlementStates.computeIfAbsent(settlement, ignored -> new SettlementState());
        }
    }

    private static void registerWatcher(Inventory inventory, StorageEntry entry) {
        synchronized (inventoryWatchers) {
            ArrayList<WeakReference<StorageEntry>> refs = inventoryWatchers.computeIfAbsent(inventory, ignored -> new ArrayList<>());
            refs.removeIf(ref -> ref.get() == null || ref.get() == entry);
            refs.add(new WeakReference<>(entry));
        }
    }

    private static void cleanupWatcher(Inventory inventory) {
        synchronized (inventoryWatchers) {
            ArrayList<WeakReference<StorageEntry>> refs = inventoryWatchers.get(inventory);
            if (refs == null) return;
            refs.removeIf(ref -> ref.get() == null);
            if (refs.isEmpty()) inventoryWatchers.remove(inventory);
        }
    }

    private static boolean isFoodItem(Item item) {
        if (item == null || !item.isFoodItem() || !(item instanceof FoodConsumableItem)) return false;
        FoodConsumableItem food = (FoodConsumableItem)item;
        return food.nutrition > 0 && food.quality != null;
    }

    private static void logFailure(String where, ServerSettlementData settlement, Level level, Throwable error) {
        if (!Logging.logEnabled) return;
        Logging.logMessage("[FoodCache] Fail-open " + where
                + " settlement=" + (settlement == null ? "null" : settlement.uniqueID)
                + " level=" + (level == null ? "null" : level.getIdentifier())
                + " error=" + error.getClass().getSimpleName() + ": " + error.getMessage());
    }

    private static final class SettlementState {
        Map<LevelIdentifier, List<StorageEntry>> entriesByLevel = new HashMap<>();
        Set<LevelIdentifier> uncertainLevels = new HashSet<>();
        boolean topologyDirty = true;
        long nextSafetyReconcileNanos;
    }

    private static final class StorageEntry {
        static final int UNKNOWN = -1;
        static final int NO_FOOD = 0;
        static final int HAS_FOOD = 1;

        private final WeakReference<SettlementInventory> storageReference;
        private final WeakReference<Inventory> inventoryReference;
        private final int startSlot;
        private final int endSlot;
        private final HashMap<Integer, Item> foodItemsBySlot = new HashMap<>();

        StorageEntry(SettlementInventory storage, Inventory inventory, int startSlot, int endSlot) {
            this.storageReference = new WeakReference<>(storage);
            this.inventoryReference = new WeakReference<>(inventory);
            this.startSlot = startSlot;
            this.endSlot = endSlot;
        }

        SettlementInventory getStorage() {
            return storageReference.get();
        }

        synchronized void rescanAll() {
            Inventory inventory = inventoryReference.get();
            if (inventory == null) return;
            foodItemsBySlot.clear();
            int max = Math.min(endSlot, inventory.getSize() - 1);
            for (int slot = Math.max(0, startSlot); slot <= max; slot++) refreshSlotInternal(inventory, slot);
        }

        void refreshSlot(Inventory inventory, int slot) {
            synchronized (this) {
                Inventory watched = inventoryReference.get();
                if (watched == null || watched != inventory || slot < startSlot || slot > endSlot) return;
                refreshSlotInternal(inventory, slot);
            }
        }

        void rescanIfWatching(Inventory inventory) {
            synchronized (this) {
                Inventory watched = inventoryReference.get();
                if (watched == null || watched != inventory) return;
                rescanAll();
            }
        }

        private void refreshSlotInternal(Inventory inventory, int slot) {
            InventoryItem stack = inventory.getItem(slot);
            if (stack != null && stack.getAmount() > 0 && isFoodItem(stack.item)) {
                foodItemsBySlot.put(slot, stack.item);
            } else {
                foodItemsBySlot.remove(slot);
            }
        }

        synchronized int compatibleFoodState(ItemCategoriesFilter dietFilter) {
            SettlementInventory storage = storageReference.get();
            Inventory inventory = inventoryReference.get();
            if (storage == null || inventory == null) return UNKNOWN;

            for (Item item : foodItemsBySlot.values()) {
                if (item == null) continue;
                if (dietFilter == null || dietFilter.isItemAllowed(item)) return HAS_FOOD;
            }

            // Before returning a negative, verify the storage still points at the same backing inventory
            // and range. Object replacement can swap inventories without changing the settlement-storage
            // assignment itself; that case must fall back to the live scan until the safety rebuild.
            try {
                InventoryRange current = storage.getInventoryRange();
                if (current == null || current.inventory != inventory || current.startSlot != startSlot || current.endSlot != endSlot) {
                    return UNKNOWN;
                }
            }
            catch (Throwable ignored) {
                return UNKNOWN;
            }
            return NO_FOOD;
        }

        synchronized int foodSlotCount() {
            return foodItemsBySlot.size();
        }
    }
}
