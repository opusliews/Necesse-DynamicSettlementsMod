package opusliews.clay;

import necesse.engine.network.gameNetworkData.GNDItem;
import necesse.engine.network.gameNetworkData.GNDItemArrayList;
import necesse.engine.network.gameNetworkData.GNDItemInventoryItem;
import necesse.entity.mobs.job.EntityJobWorker;
import necesse.entity.mobs.job.WorkInventory;
import necesse.entity.mobs.job.activeJob.ActiveJobResult;
import necesse.entity.mobs.job.activeJob.DropOffSettlementStorageActiveJob;
import necesse.entity.mobs.job.activeJob.PickupSettlementStorageActiveJob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.level.maps.Level;
import opusliews.DSItemRegistry;
import opusliews.item.FireableMatItem;
import opusliews.item.FiredClayMatItem;
import opusliews.logging.Logging;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public final class ClayPackageSystem {
	private static final String contentsKey = "clayPackageContents";

	private ClayPackageSystem() {
	}

	public static boolean isPackage(InventoryItem item) {
		return item != null && DSItemRegistry.clayPackageStringID.equals(item.item.getStringID());
	}

	public static boolean isClayItem(InventoryItem item) {
		return item != null && isClayItem(item.item);
	}

	public static boolean isClayItem(Item item) {
		if (item == null || DSItemRegistry.clayPackageStringID.equals(item.getStringID())) return false;
		return item instanceof FireableMatItem || item instanceof FiredClayMatItem || FireableMatItem.isFiredItemStringID(item.getStringID());
	}

	public static boolean isUnfiredClayItem(Item item) {
		return item instanceof FireableMatItem;
	}

	public static boolean isFiredClayItem(Item item) {
		return item instanceof FiredClayMatItem || item != null && FireableMatItem.isFiredItemStringID(item.getStringID());
	}

	public static InventoryItem findPackage(Iterable items) {
		if (items == null) return null;

		for (Object value : items) {
			if (value instanceof InventoryItem && isPackage((InventoryItem)value)) {
				return (InventoryItem)value;
			}
		}

		return null;
	}

	public static boolean hasPackage(Iterable items) {
		return findPackage(items) != null;
	}

	public static InventoryItem newPackage() {
		return new InventoryItem(DSItemRegistry.clayPackageStringID, 1);
	}

	public static boolean add(InventoryItem clayPackage, InventoryItem item) {
		if (!isPackage(clayPackage) || !isClayItem(item) || item.getAmount() <= 0) {
			return false;
		}

		GNDItemArrayList contents = getContentsList(clayPackage, true);
		contents.add(new GNDItemInventoryItem(item.copy()));
		clayPackage.getGndData().setItem(contentsKey, contents);
		return true;
	}

	public static int getTotalAmount(InventoryItem clayPackage) {
		int total = 0;
		for (InventoryItem item : getContents(clayPackage)) total += item.getAmount();
		return total;
	}

	public static int getAmount(InventoryItem clayPackage, String itemStringID) {
		int total = 0;
		for (InventoryItem item : getContents(clayPackage)) {
			if (item.item.getStringID().equals(itemStringID)) total += item.getAmount();
		}
		return total;
	}

	public static boolean isEmpty(InventoryItem clayPackage) {
		return getTotalAmount(clayPackage) <= 0;
	}

	public static List<InventoryItem> getContents(InventoryItem clayPackage) {
		ArrayList<InventoryItem> result = new ArrayList<>();
		GNDItemArrayList contents = getContentsList(clayPackage, false);
		if (contents == null) return result;

		for (Object value : contents) {
			if (!(value instanceof GNDItemInventoryItem)) continue;
			InventoryItem item = ((GNDItemInventoryItem)value).invItem;
			if (item != null && item.getAmount() > 0) result.add(item.copy());
		}
		return result;
	}

	public static InventoryItem remove(InventoryItem clayPackage, String itemStringID, int amount) {
		if (amount <= 0) return null;
		GNDItemArrayList contents = getContentsList(clayPackage, false);
		if (contents == null) return null;

		for (int i = 0; i < contents.size(); i++) {
			GNDItem value = contents.get(i);
			if (!(value instanceof GNDItemInventoryItem)) continue;
			InventoryItem stored = ((GNDItemInventoryItem)value).invItem;
			if (stored == null || !stored.item.getStringID().equals(itemStringID)) continue;

			int removed = Math.min(amount, stored.getAmount());
			InventoryItem result = stored.copy(removed);
			stored.setAmount(stored.getAmount() - removed);
			if (stored.getAmount() <= 0) contents.remove(i);
			clayPackage.getGndData().setItem(contentsKey, contents);
			return result;
		}

		return null;
	}

	public static InventoryItem removeMatching(InventoryItem clayPackage, InventoryItem target, int amount, Level level) {
		if (target == null || amount <= 0) return null;
		GNDItemArrayList contents = getContentsList(clayPackage, false);
		if (contents == null) return null;

		for (int i = 0; i < contents.size(); i++) {
			GNDItem value = contents.get(i);
			if (!(value instanceof GNDItemInventoryItem)) continue;
			InventoryItem stored = ((GNDItemInventoryItem)value).invItem;
			if (stored == null || !stored.equals(level, target, true, false, "claypackage")) continue;

			int removed = Math.min(amount, stored.getAmount());
			InventoryItem result = stored.copy(removed);
			stored.setAmount(stored.getAmount() - removed);
			if (stored.getAmount() <= 0) contents.remove(i);
			clayPackage.getGndData().setItem(contentsKey, contents);
			return result;
		}

		return null;
	}


	public static boolean addToWorkInventory(WorkInventory workInventory, InventoryItem item) {
		if (workInventory == null || !isClayItem(item) || item.getAmount() <= 0) return false;
		InventoryItem clayPackage = findPackage(workInventory.items());
		if (clayPackage == null) {
			InventoryItem newPackage = newPackage();
			if (workInventory.getCanAddAmount(newPackage) <= 0) return false;
			workInventory.add(newPackage);
			clayPackage = findPackage(workInventory.items());
			if (clayPackage == null) return false;
		}
		boolean added = add(clayPackage, item);
		if (added) workInventory.markDirty();
		return added;
	}

	public static boolean addToRawWorkInventory(List<InventoryItem> workInventory, InventoryItem item) {
		if (workInventory == null || !isClayItem(item)) return false;
		InventoryItem clayPackage = findPackage(workInventory);
		if (clayPackage == null) {
			clayPackage = newPackage();
			workInventory.add(clayPackage);
		}
		return add(clayPackage, item);
	}

	public static void unpackAllPackages(List<InventoryItem> workInventory) {
		if (workInventory == null || workInventory.isEmpty()) return;

		ArrayList<InventoryItem> unpacked = new ArrayList<>();
		ListIterator<InventoryItem> iterator = workInventory.listIterator();
		while (iterator.hasNext()) {
			InventoryItem item = iterator.next();
			if (!isPackage(item)) continue;
			unpacked.addAll(getContents(item));
			iterator.remove();
		}

		workInventory.addAll(unpacked);
	}


	public static ActiveJobResult tryPickupPackageItem(
			PickupSettlementStorageActiveJob job,
			EntityJobWorker worker
	) {
		if (job == null || worker == null || job.slot == null) return null;
		InventoryItem reserved = job.slot.item;
		if (!isClayItem(reserved)) return null;

		if (worker.isInWorkAnimation()) return ActiveJobResult.PERFORMING;

		String clayType = isUnfiredClayItem(reserved.item) ? "unfired" : isFiredClayItem(reserved.item) ? "fired" : "unknown";
		Logging.logMessage("[ClayPackage] Intercepting haul pickup item="
				+ reserved.item.getStringID() + " type=" + clayType + " amount=" + reserved.getAmount()
				+ " worker=" + worker.getMobWorker().getUniqueID());

		WorkInventory workInventory = worker.getWorkInventory();
		InventoryItem clayPackage = findPackage(workInventory.items());
		if (clayPackage == null && workInventory.getCanAddAmount(newPackage()) <= 0) {
			Logging.logMessage("[ClayPackage] Pickup blocked: no work-inventory slot available for package");
			return ActiveJobResult.FAILED;
		}

		InventoryItem item = job.slot.pickupItem();
		if (item == null) {
			Logging.logMessage("[ClayPackage] Pickup failed: reserved slot returned null");
			return ActiveJobResult.FAILED;
		}

		if (clayPackage == null) {
			InventoryItem newPackage = newPackage();
			workInventory.add(newPackage);
			clayPackage = findPackage(workInventory.items());
			if (clayPackage == null) {
				Logging.logMessage("[ClayPackage] Pickup failed: package creation did not appear in work inventory");
				return ActiveJobResult.FAILED;
			}
			Logging.logMessage("[ClayPackage] Created package for worker=" + worker.getMobWorker().getUniqueID());
		}

		if (!add(clayPackage, item)) {
			Logging.logMessage("[ClayPackage] Pickup failed: could not add item=" + item.item.getStringID() + " to package");
			return ActiveJobResult.FAILED;
		}

		workInventory.markDirty();
		job.pickedUpItemRef.set(item.copy());
		worker.showPickupAnimation(job.tileX * 32 + 16, job.tileY * 32 + 16, item.item, 250, true);
		Logging.logMessage("[ClayPackage] Added item=" + item.item.getStringID() + " amount=" + item.getAmount()
				+ " packageTotal=" + getTotalAmount(clayPackage));
		return ActiveJobResult.FINISHED;
	}

	public static ActiveJobResult tryDropOffPackageItem(
			DropOffSettlementStorageActiveJob job,
			EntityJobWorker worker
	) {
		InventoryItem requested = job.dropOff.getItem();
		if (!isClayItem(requested)) return null;

		WorkInventory workInventory = worker.getWorkInventory();
		ListIterator iterator = workInventory.listIterator();
		while (iterator.hasNext()) {
			Object value = iterator.next();
			if (!(value instanceof InventoryItem)) continue;
			InventoryItem clayPackage = (InventoryItem)value;
			if (!isPackage(clayPackage)) continue;

			InventoryItem candidate = findMatching(clayPackage, requested, worker.getMobWorker().getLevel());
			if (candidate == null) continue;

			candidate.setAmount(Math.min(candidate.getAmount(), requested.getAmount()));
			int added = job.dropOff.addItem(candidate);
			if (added <= 0) return ActiveJobResult.FAILED;

			removeMatching(clayPackage, candidate, added, worker.getMobWorker().getLevel());
			if (isEmpty(clayPackage)) iterator.remove();
			workInventory.markDirty();
			worker.showPlaceAnimation(job.tileX * 32 + 16, job.tileY * 32 + 16, candidate.item, 250, true);
			return ActiveJobResult.FINISHED;
		}

		return null;
	}

	private static InventoryItem findMatching(InventoryItem clayPackage, InventoryItem target, Level level) {
		for (InventoryItem stored : getContents(clayPackage)) {
			if (stored.equals(level, target, true, false, "claypackage")) return stored;
		}
		return null;
	}

	private static GNDItemArrayList getContentsList(InventoryItem clayPackage, boolean create) {
		if (!isPackage(clayPackage)) return null;
		GNDItem value = clayPackage.getGndData().getItem(contentsKey);
		if (value instanceof GNDItemArrayList) return (GNDItemArrayList)value;
		return create ? new GNDItemArrayList() : null;
	}

}
