package opusliews.multilevelsettlement;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import necesse.engine.localization.message.StaticMessage;
import necesse.engine.util.GameLinkedList;
import necesse.engine.registries.JobTypeRegistry;
import necesse.engine.util.GameRandom;
import necesse.engine.util.LevelIdentifier;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.MobWasHitEvent;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.mobs.friendly.HusbandryMob;
import necesse.entity.mobs.job.EntityJobWorker;
import necesse.entity.mobs.job.FoundJob;
import necesse.entity.mobs.job.JobFinder;
import necesse.entity.mobs.job.HandlerFoundJobGenerator;
import necesse.entity.mobs.job.PreSequenceComputes;
import necesse.entity.mobs.job.JobSequence;
import necesse.entity.mobs.job.JobType;
import necesse.entity.mobs.job.JobTypeHandler;
import necesse.entity.mobs.job.LinkedListJobSequence;
import necesse.entity.mobs.job.WorkInventory;
import necesse.entity.mobs.job.activeJob.ActiveJob;
import necesse.entity.mobs.job.activeJob.ActiveJobHitResult;
import necesse.entity.mobs.job.activeJob.ActiveJobResult;
import necesse.entity.mobs.job.activeJob.ActiveJobTargetFoundResult;
import necesse.entity.mobs.job.activeJob.PickupSettlementStorageActiveJob;
import necesse.entity.pickup.ItemPickupEntity;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.ItemAttackerWeaponItem;
import necesse.inventory.item.armorItem.ArmorItem;
import necesse.inventory.item.placeableItem.consumableItem.food.FoodConsumableItem;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.jobs.AbstractLevelJob;
import necesse.level.maps.levelData.jobs.HasStorageLevelJob;
import necesse.level.maps.levelData.jobs.ConsumeFoodLevelJob;
import necesse.level.maps.levelData.jobs.MilkHusbandryMobLevelJob;
import necesse.level.maps.levelData.jobs.ShearHusbandryMobLevelJob;
import necesse.level.maps.levelData.jobs.StorePickupItemLevelJob;
import necesse.level.maps.levelData.jobs.JobMoveToTile;
import necesse.level.maps.levelData.jobs.ManageEquipmentLevelJob;
import necesse.level.maps.levelData.jobs.StartExpeditionLevelJob;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementMissionBoardMission;
import necesse.level.maps.levelData.settlementData.ZoneTester;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageEquipmentTypeIndex;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageItemIDIndex;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageRecord;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageRecords;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageRecordsRegionData;
import necesse.level.maps.levelData.settlementData.LevelSettler;
import opusliews.guard.GuardLevelAssignmentSystem;
import opusliews.logging.Logging;

public final class SettlementCrossLevelJobSystem {
	private static final double COST_BAND_PERCENT = 0.50;
	private static final double COST_BAND_MIN_TILES = 8.0;
	private static final double PREFERENCE_WEIGHT_MIN = 1.40;
	private static final double PREFERENCE_WEIGHT_MAX = 2.10;
	private static final double CURRENT_LEVEL_WEIGHT = 1.15;
	private static final double GUARD_ASSIGNED_LEVEL_WEIGHT = 4.0;
	private static final double RECREATION_RANDOM_KIND_CHANCE = 0.20;
	private static final long AGED_JOB_MS = 90_000L;
	private static final long FORCE_OLD_JOB_MS = 180_000L;
	private static final long STALE_JOB_KEY_MS = 300_000L;
	private static final long JOB_KEY_CLEANUP_INTERVAL_MS = 60_000L;

	private static final Map<JobKey, SeenJob> seenJobs = new HashMap<>();
	private static long nextSeenJobCleanupTime;

	private SettlementCrossLevelJobSystem() {
	}

	/**
	 * Returns a synthetic FoundJob only when another settlement level wins the level-choice
	 * roll. Returning null means vanilla JobFinder should continue completely unchanged.
	 */
	public static FoundJob findRemoteRelocation(JobFinder localFinder, boolean ignoreRecreationJobs) {
		if (localFinder == null || !(localFinder.mob instanceof HumanMob)) return null;
		HumanMob human = (HumanMob)localFinder.mob;
		if (human.getLevel() == null || !human.getLevel().isServer() || !human.isSettler()) return null;

		// Custom JobFinder constraints are used by following AI and special systems such as
		// guard breaks.
		if (localFinder.zoneTester != null || localFinder.searchBounds != null || localFinder.levelJobPredicate != null) return null;
		if (human.getJobFollowingMob() != null) return null;

		ServerSettlementData settlement = human.getSettlerSettlementServerData();
		if (settlement == null) return null;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null) {
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Cannot evaluate jobs because settlement domain is missing settler=" + human.getUniqueID());
			return null;
		}

		SettlementLevelType currentType = domain.getLevelType(human.getLevel().getIdentifier());
		if (currentType == null) {
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Current level is outside settlement domain settler=" + human.getUniqueID() + " level=" + human.getLevel().getIdentifier());
			return null;
		}

		SettlementLevelType otherType = currentType == SettlementLevelType.SURFACE ? SettlementLevelType.CAVE : SettlementLevelType.SURFACE;
		Level otherLevel = getLoadedLevel(domain, otherType);
		if (otherLevel == null) return null;

		SettlementCrossLevelRoute transitionRoute = SettlementCrossLevelRouting.findBestTransitionRouteQuiet(human, domain, otherType);
		if (transitionRoute == null || transitionRoute.ladder == null) {
			if (Logging.logEnabled) {
				Logging.logMessage("[CrossLevelJobs] Other settlement level has no reachable designated transition settler="
						+ human.getUniqueID()
						+ " current=" + currentType
						+ " other=" + otherType
						+ " tile=" + human.getTileX() + "," + human.getTileY()
						+ " preference=" + SettlementLevelPreferenceSystem.getPreference(human));
				SettlementCrossLevelRouting.findBestTransitionRoute(human, domain, otherType);
			}
			return null;
		}

		long now = human.getTime();
		cleanupSeenJobs(now);

		ArrayList<Candidate> candidates = new ArrayList<>();
		try {
			collectLocalCandidates(localFinder, ignoreRecreationJobs, domain, currentType, now, candidates);
		}
		catch (Exception e) {
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Local candidate discovery failed settler=" + human.getUniqueID() + " error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
			return null;
		}

		try {
			collectRemoteCandidates(human, localFinder, ignoreRecreationJobs, settlement, domain, otherLevel, otherType, now, candidates);
		}
		catch (Exception e) {
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Remote candidate discovery failed settler=" + human.getUniqueID() + " level=" + otherType + " error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
			return null;
		}

		if (candidates.isEmpty()) return null;
		candidates.sort(CANDIDATE_PRIORITY_COMPARATOR);

		List<Candidate> reachableBestPriority = findFirstReachablePriorityGroup(human, settlement, domain, candidates);
		if (reachableBestPriority.isEmpty()) return null;

		Candidate selected = selectCandidate(human, currentType, reachableBestPriority, now);
		if (selected == null || selected.levelType == currentType) return null;
		if (selected.route == null || selected.route.ladder == null) {
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Remote candidate won selection without a valid route settler=" + human.getUniqueID() + " job=" + describe(selected));
			return null;
		}

		if (Logging.logEnabled) {
			Logging.logMessage("[CrossLevelJobs] Selected remote job level settler=" + human.getUniqueID()
					+ " current=" + currentType
					+ " selected=" + selected.levelType
					+ " job=" + selected.found.job.getStringID()
					+ " tile=" + selected.found.job.getTileX() + "," + selected.found.job.getTileY()
					+ " cost=" + selected.travelCost
					+ " waitingMS=" + selected.waitingMs(now)
					+ " route=" + selected.route);
		}

		return new RelocationFoundJob(human, selected);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void collectLocalCandidates(JobFinder finder, boolean ignoreRecreationJobs, SettlementLevelDomain domain, SettlementLevelType levelType, long now, List<Candidate> out) {
		Stream stream = finder.streamFoundJobs(ignoreRecreationJobs);
		Iterator iterator = stream.iterator();
		while (iterator.hasNext()) {
			Object next = iterator.next();
			if (!(next instanceof FoundJob)) continue;
			FoundJob found = (FoundJob)next;

			// streamFoundJobs() is intentionally an earlier vanilla stage than findJob().
			// Some synthetic jobs always produce a FoundJob and only become invalid when
			// their sequence is created. We cannot call getSequence() speculatively here:
			// sequence construction can reserve items or mutate worker/job state, and a
			// remote candidate may still win the cross-level decision. For synthetic jobs
			// where the vanilla null-sequence conditions can be reproduced without side
			// effects, apply that preview before allowing them into level selection.
			if (!passesNonMutatingLocalSequencePreview(finder.worker, found)) continue;

			Candidate candidate = createCandidate(found, domain, levelType, now);
			if (candidate != null) out.add(candidate);
		}
	}

	private static boolean passesNonMutatingLocalSequencePreview(EntityJobWorker worker, FoundJob found) {
		return passesNonMutatingSequencePreview(worker, found, "local");
	}

	private static boolean passesNonMutatingSequencePreview(EntityJobWorker worker, FoundJob found, String source) {
		if (worker == null || found == null || found.job == null) return false;

		boolean valid = true;
		String reason = null;

		if (found.job instanceof StartExpeditionLevelJob) {
			valid = canStartExpeditionWithoutMutating(worker, (StartExpeditionLevelJob)found.job);
			reason = "no executable expedition sequence";
		}
		else if (found.job instanceof HasStorageLevelJob) {
			valid = canDropOffWorkInventoryWithoutMutating(worker, (HasStorageLevelJob)found.job);
			reason = "work inventory has nothing this storage can accept";
		}
		else if (found.job instanceof ManageEquipmentLevelJob) {
			valid = canManageEquipmentWithoutMutating(worker, (ManageEquipmentLevelJob)found.job);
			reason = "no equipment change would be produced";
		}
		else if (found.job instanceof ConsumeFoodLevelJob) {
			valid = canConsumeFoodWithoutMutating(worker, (ConsumeFoodLevelJob)found.job);
			reason = "no edible food is available";
		}
		else if (found.job instanceof StorePickupItemLevelJob) {
			valid = canStorePickupWithoutMutating(worker, (StorePickupItemLevelJob)found.job);
			reason = "pickup cannot currently be carried to settlement storage";
		}
		else if (found.job instanceof ShearHusbandryMobLevelJob) {
			valid = ((HusbandryMob)((ShearHusbandryMobLevelJob)found.job).target).canShear(new InventoryItem("shears"));
			reason = "husbandry target cannot currently be sheared";
		}
		else if (found.job instanceof MilkHusbandryMobLevelJob) {
			valid = ((HusbandryMob)((MilkHusbandryMobLevelJob)found.job).target).canMilk(new InventoryItem("bucket"));
			reason = "husbandry target cannot currently be milked";
		}

		if (!valid && Logging.logEnabled) {
			Logging.logMessage("[CrossLevelJobs] Candidate rejected by vanilla-equivalent non-mutating preview source=" + source
					+ " job=" + found.job.getStringID()
					+ " tile=" + found.job.getTileX() + "," + found.job.getTileY()
					+ " reason=" + reason);
		}
		return valid;
	}

	private static boolean canDropOffWorkInventoryWithoutMutating(EntityJobWorker worker, HasStorageLevelJob job) {
		if (worker.getWorkInventory().isEmpty()) return false;
		Iterator<InventoryItem> iterator = worker.getWorkInventory().items().iterator();
		while (iterator.hasNext()) {
			InventoryItem item = iterator.next();
			if (item != null && job.settlementInventory.canAddFutureDropOff(item) > 0) return true;
		}
		return false;
	}

	private static boolean canConsumeFoodWithoutMutating(EntityJobWorker worker, ConsumeFoodLevelJob job) {
		java.util.function.Predicate<InventoryItem> foodFilter = item -> {
			if (item == null || !item.item.isFoodItem()) return false;
			if (job.dietFilter != null && !job.dietFilter.isItemAllowed(item.item)) return false;
			FoodConsumableItem food = (FoodConsumableItem)item.item;
			return food.nutrition > 0 && food.quality != null;
		};

		if (worker.getWorkInventory().stream().anyMatch(foodFilter)) return true;
		return HasStorageLevelJob.getItemCount(worker, foodFilter, 1, false, false) > 0;
	}

	private static boolean canStorePickupWithoutMutating(EntityJobWorker worker, StorePickupItemLevelJob job) {
		if (!(job.target instanceof ItemPickupEntity)) return false;
		ItemPickupEntity pickup = (ItemPickupEntity)job.target;
		int available = pickup.getAvailableAmount();
		if (available <= 0) return false;
		available = Math.min(available, worker.getWorkInventory().getCanAddAmount(pickup.item));
		if (available <= 0) return false;
		InventoryItem dropOffItem = pickup.item.copy(Math.min(available, pickup.item.itemStackSize()));
		return !HasStorageLevelJob.findDropOffLocation(worker, dropOffItem, pickup.getPositionPoint()).isEmpty();
	}

	private static boolean canManageEquipmentWithoutMutating(EntityJobWorker worker, ManageEquipmentLevelJob job) {
		Mob mob = worker.getMobWorker();
		if (!(mob instanceof HumanMob)) return false;
		HumanMob human = (HumanMob)mob;
		SettlementStorageRecords storageRecords = PickupSettlementStorageActiveJob.getStorageRecords(worker);
		if (storageRecords == null) return false;
		SettlementStorageEquipmentTypeIndex index = (SettlementStorageEquipmentTypeIndex)storageRecords.getIndex(SettlementStorageEquipmentTypeIndex.class);
		if (index == null) return false;

		InventoryItem currentWeapon = human.getInventory().getItem(6);
		InventoryItem currentHead = human.getInventory().getItem(0);
		InventoryItem currentChest = human.getInventory().getItem(1);
		InventoryItem currentFeet = human.getInventory().getItem(2);

		EquipmentOption weapon = currentWeaponOption(currentWeapon, human, job);
		EquipmentOption head = currentArmorOption(currentHead, ArmorItem.ArmorType.HEAD, human, job);
		EquipmentOption chest = currentArmorOption(currentChest, ArmorItem.ArmorType.CHEST, human, job);
		EquipmentOption feet = currentArmorOption(currentFeet, ArmorItem.ArmorType.FEET, human, job);

		if (currentWeapon != null && weapon == null) return true;
		if (currentHead != null && head == null) return true;
		if (currentChest != null && chest == null) return true;
		if (currentFeet != null && feet == null) return true;

		List<EquipmentOption> weaponOptions = getStorageEquipmentOptions(worker, human, job, index, SettlementStorageEquipmentTypeIndex.EquipmentType.WEAPON);
		List<EquipmentOption> headOptions = getStorageEquipmentOptions(worker, human, job, index, SettlementStorageEquipmentTypeIndex.EquipmentType.HEAD);
		List<EquipmentOption> chestOptions = getStorageEquipmentOptions(worker, human, job, index, SettlementStorageEquipmentTypeIndex.EquipmentType.CHEST);
		List<EquipmentOption> feetOptions = getStorageEquipmentOptions(worker, human, job, index, SettlementStorageEquipmentTypeIndex.EquipmentType.FEET);

		double currentWeaponValue = weapon == null ? 0.0 : weapon.value;
		if (weaponOptions.stream().anyMatch(option -> option.value > currentWeaponValue)) return true;

		double currentArmorScore = armorScore(head, chest, feet, job.preferArmorSets);
		if (!job.preferArmorSets) {
			if (headOptions.stream().anyMatch(option -> option.value > (head == null ? 0.0 : head.value))) return true;
			if (chestOptions.stream().anyMatch(option -> option.value > (chest == null ? 0.0 : chest.value))) return true;
			return feetOptions.stream().anyMatch(option -> option.value > (feet == null ? 0.0 : feet.value));
		}

		ArrayList<EquipmentOption> allHeads = new ArrayList<>(headOptions);
		ArrayList<EquipmentOption> allChests = new ArrayList<>(chestOptions);
		ArrayList<EquipmentOption> allFeet = new ArrayList<>(feetOptions);
		allHeads.add(head);
		allChests.add(chest);
		allFeet.add(feet);
		for (EquipmentOption h : allHeads) {
			for (EquipmentOption c : allChests) {
				for (EquipmentOption f : allFeet) {
					if (armorScore(h, c, f, true) > currentArmorScore) return true;
				}
			}
		}
		return false;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static List<EquipmentOption> getStorageEquipmentOptions(EntityJobWorker worker, HumanMob human, ManageEquipmentLevelJob job, SettlementStorageEquipmentTypeIndex index, SettlementStorageEquipmentTypeIndex.EquipmentType type) {
		SettlementStorageRecordsRegionData data = index.getEquipmentType(type);
		if (data == null) return Collections.emptyList();
		Map<Integer, EquipmentOption> bestByItemID = new HashMap<>();
		GameLinkedList groups = data.getRecords(worker);
		Iterator groupIterator = groups.iterator();
		while (groupIterator.hasNext()) {
			Object groupObject = groupIterator.next();
			if (!(groupObject instanceof Map.Entry)) continue;
			Object value = ((Map.Entry)groupObject).getValue();
			if (!(value instanceof GameLinkedList)) continue;
			Iterator recordIterator = ((GameLinkedList)value).iterator();
			while (recordIterator.hasNext()) {
				Object recordObject = recordIterator.next();
				if (!(recordObject instanceof SettlementStorageRecord)) continue;
				SettlementStorageRecord record = (SettlementStorageRecord)recordObject;
				InventoryItem item = record.getItem();
				if (item == null || record.itemAmount <= 0) continue;
				if (job.equipmentFilter != null && !job.equipmentFilter.isItemAllowed(item.item)) continue;
				if (record.storage.getFutureReserve(record.inventorySlot, item, 1, accepted -> {}) == null) continue;

				EquipmentOption option = type == SettlementStorageEquipmentTypeIndex.EquipmentType.WEAPON
						? currentWeaponOption(item, human, job)
						: currentArmorOption(item, type.armorType, human, job);
				if (option == null) continue;
				bestByItemID.compute(item.item.getID(), (id, previous) -> previous == null || option.value > previous.value ? option : previous);
			}
		}
		return new ArrayList<>(bestByItemID.values());
	}

	private static EquipmentOption currentWeaponOption(InventoryItem item, HumanMob human, ManageEquipmentLevelJob job) {
		if (item == null || !(item.item instanceof ItemAttackerWeaponItem)) return null;
		if (job.equipmentFilter != null && !job.equipmentFilter.isItemAllowed(item.item)) return null;
		ItemAttackerWeaponItem weapon = (ItemAttackerWeaponItem)item.item;
		if (weapon.getItemAttackerCanUseError(human, item) != null) return null;
		double value = weapon.getItemAttackerWeaponValue(human, item);
		return value > 0.0 ? new EquipmentOption(item, value) : null;
	}

	private static EquipmentOption currentArmorOption(InventoryItem item, ArmorItem.ArmorType type, HumanMob human, ManageEquipmentLevelJob job) {
		if (item == null || !item.item.isArmorItem()) return null;
		if (job.equipmentFilter != null && !job.equipmentFilter.isItemAllowed(item.item)) return null;
		ArmorItem armor = (ArmorItem)item.item;
		if (armor.armorType != type || !armor.canMobEquip(human, item)) return null;
		double value = armor.getSettlerEquipmentValue(item, human);
		return value > 0.0 ? new EquipmentOption(item, value) : null;
	}

	private static double armorScore(EquipmentOption head, EquipmentOption chest, EquipmentOption feet, boolean preferSet) {
		double score = (head == null ? 0.0 : head.value) + (chest == null ? 0.0 : chest.value) + (feet == null ? 0.0 : feet.value);
		if (preferSet && head != null && chest != null && feet != null
				&& head.item != null && chest.item != null && feet.item != null
				&& ((ArmorItem)head.item.item).hasSet(head.item, chest.item, feet.item)) score *= 1.5;
		return score;
	}

	private static final class EquipmentOption {
		final InventoryItem item;
		final double value;

		EquipmentOption(InventoryItem item, double value) {
			this.item = item;
			this.value = value;
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static boolean canStartExpeditionWithoutMutating(EntityJobWorker worker, StartExpeditionLevelJob job) {
		Mob mob = worker.getMobWorker();
		if (!(mob instanceof HumanMob)) return false;
		HumanMob human = (HumanMob)mob;
		if (!human.isSettlerWithinSettlement()) return false;

		ServerSettlementData settlement = human.getSettlerSettlementServerData();
		if (settlement == null || settlement.getMissionBoardTile() == null) return false;

		for (Object next : settlement.missionBoardManager.getMissions()) {
			SettlementMissionBoardMission mission = (SettlementMissionBoardMission)next;
			if (!job.canDoExpedition.test(mission.expedition)) continue;
			if (!mission.allSettlersAssigned && !mission.assignedSettlers.contains(human.getUniqueID())) continue;
			if (!mission.condition.isConditionMet(worker, settlement)) continue;

			int currentCost = mission.expedition.getCurrentCost(settlement, job.shopSeed);
			if (currentCost <= 0) return true;

			int coinID = necesse.engine.registries.ItemRegistry.getItemID("coin");
			int foundCost = 0;
			Iterator<InventoryItem> workIterator = human.getWorkInventory().items().iterator();
			while (workIterator.hasNext()) {
				InventoryItem item = workIterator.next();
				if (item != null && item.item.getID() == coinID) {
					foundCost += item.getAmount();
					if (foundCost >= currentCost) return true;
				}
			}

			SettlementStorageRecords storageRecords = necesse.entity.mobs.job.activeJob.PickupSettlementStorageActiveJob.getStorageRecords(human);
			if (storageRecords == null) continue;
			SettlementStorageItemIDIndex itemIndex = (SettlementStorageItemIDIndex)storageRecords.getIndex(SettlementStorageItemIDIndex.class);
			if (itemIndex == null) continue;
			SettlementStorageRecordsRegionData regionData = itemIndex.getItem(coinID);
			if (regionData == null) continue;

			GameLinkedList accessibleRecords = regionData.getRecords(human);
			Iterator recordGroups = accessibleRecords.iterator();
			while (recordGroups.hasNext() && foundCost < currentCost) {
				Object groupObject = recordGroups.next();
				if (!(groupObject instanceof Map.Entry)) continue;
				Object value = ((Map.Entry)groupObject).getValue();
				if (!(value instanceof GameLinkedList)) continue;

				Iterator records = ((GameLinkedList)value).iterator();
				while (records.hasNext() && foundCost < currentCost) {
					Object recordObject = records.next();
					if (!(recordObject instanceof SettlementStorageRecord)) continue;
					SettlementStorageRecord record = (SettlementStorageRecord)recordObject;
					InventoryItem item = record.getItem();
					if (item != null && item.item.getID() == coinID) foundCost += Math.max(0, record.itemAmount);
				}
			}

			if (foundCost >= currentCost) return true;
		}

		return false;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void collectRemoteCandidates(HumanMob human, JobFinder localFinder, boolean ignoreRecreationJobs, ServerSettlementData settlement, SettlementLevelDomain domain, Level remoteLevel, SettlementLevelType remoteType, long now, List<Candidate> out) {
		LevelIdentifier remoteIdentifier = remoteLevel.getIdentifier();
		RemoteWorker worker = new RemoteWorker(human, settlement, domain, remoteLevel, getRemoteSearchOrigin(human, domain, remoteType));
		JobTypeHandler handler = worker.getJobTypeHandler();
		if (handler == null) {
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Remote worker has no JobTypeHandler settler=" + human.getUniqueID() + " level=" + remoteType);
			return;
		}

		// Do NOT call JobFinder.streamFoundJobs() here. JobTypeHandler.streamJobs() also
		// executes registered extra job streamers (chatting/recreation/etc.), which are
		// built for the real HumanMob and can cast the synthetic RemoteWorker to
		// specialized worker interfaces. This step only needs persistent jobs that actually
		// exist in the remote level's jobsLayer. Generate FoundJob objects for those jobs
		// manually so vanilla handler predicates, priorities and pre-sequence computes are
		// still preserved.
		ZoneTester restrictZone = worker.getJobRestrictZone();
		PreSequenceComputes preSequenceComputes = new PreSequenceComputes();
		Rectangle searchBounds = worker.getJobSearchBounds();
		Stream stream = worker.streamValidJobsWithinRange(searchBounds);
		Iterator iterator = stream.iterator();
		int scanned = 0;
		int accepted = 0;

		while (iterator.hasNext()) {
			Object next = iterator.next();
			if (!(next instanceof AbstractLevelJob)) continue;
			AbstractLevelJob job = (AbstractLevelJob)next;
			scanned++;

			if (job.getLevel() == null || !remoteIdentifier.equals(job.getLevel().getIdentifier())) {
				if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Ignoring remote jobsLayer entry with mismatched level settler=" + human.getUniqueID() + " expected=" + remoteIdentifier + " job=" + job.getStringID() + " actual=" + (job.getLevel() == null ? "null" : job.getLevel().getIdentifier()));
				continue;
			}
			if (!job.reservable.isAvailable(human)) continue;
			if (!job.isWithinRestrictZone(restrictZone)) continue;
			if (ignoreRecreationJobs && job.jobType.getID() == JobTypeRegistry.recreationID) continue;

			FoundJob found;
			try {
				found = new HandlerFoundJobGenerator(job, handler, preSequenceComputes).generateJob(worker);
			}
			catch (Throwable error) {
				if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Remote jobsLayer candidate generation failed settler=" + human.getUniqueID() + " level=" + remoteType + " job=" + job.getStringID() + " tile=" + job.getTileX() + "," + job.getTileY() + " error=" + error.getClass().getSimpleName() + ": " + error.getMessage());
				continue;
			}
			if (found == null || found.isDisabled()) continue;
			if (!passesNonMutatingSequencePreview(worker, found, "remote")) continue;

			Candidate candidate = createCandidate(found, domain, remoteType, now);
			if (candidate != null) {
				out.add(candidate);
				accepted++;
				if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Remote jobsLayer candidate accepted settler=" + human.getUniqueID() + " level=" + remoteType + " job=" + job.getStringID() + " tile=" + job.getTileX() + "," + job.getTileY() + " type=" + job.jobType.getStringID());
			}
		}

		if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Remote jobsLayer scan complete settler=" + human.getUniqueID() + " level=" + remoteType + " scanned=" + scanned + " accepted=" + accepted);
	}

	private static Point getRemoteSearchOrigin(HumanMob human, SettlementLevelDomain domain, SettlementLevelType remoteType) {
		SettlementCrossLevelRoute route = SettlementCrossLevelRouting.findBestTransitionRouteQuiet(human, domain, remoteType);
		if (route != null && route.target != null) return new Point(route.target.tileX, route.target.tileY);
		Rectangle bounds = domain.getTileBounds(remoteType);
		if (bounds != null) return new Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
		return new Point(human.getTileX(), human.getTileY());
	}

	private static Candidate createCandidate(FoundJob found, SettlementLevelDomain domain, SettlementLevelType levelType, long now) {
		if (found == null || found.job == null || found.job.getLevel() == null) return null;
		try {
			PriorityKey priorityKey = PriorityKey.from(found);
			JobKey jobKey = new JobKey(domain.getSettlementUniqueID(), found.job.getLevel().getIdentifier(), found.job.getID(), found.job.getTileX(), found.job.getTileY());
			long firstSeen = markSeen(jobKey, now);
			return new Candidate(found, levelType, priorityKey, jobKey, firstSeen);
		}
		catch (Exception e) {
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Could not rank candidate job=" + found.job.getStringID() + " tile=" + found.job.getTileX() + "," + found.job.getTileY() + " error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
			return null;
		}
	}

	private static List<Candidate> findFirstReachablePriorityGroup(HumanMob human, ServerSettlementData settlement, SettlementLevelDomain domain, List<Candidate> sorted) {
		if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Priority grouping begin settler=" + human.getUniqueID() + " candidates=" + sorted.size());

		int index = 0;
		int groupIndex = 0;
		while (index < sorted.size()) {
			Candidate first = sorted.get(index);
			PriorityKey key = first.priorityKey;

			// Vanilla recreation preference lives in sameTypePriority. Keep the normal
			// recreation kind 80% of the time, but 20% of the time choose uniformly from
			// the reachable recreation kinds before level preference is applied. This keeps
			// higher-priority job TYPES completely vanilla while preventing one recreation
			// kind from permanently excluding every other kind on another settlement level.
			if (first.found.job.jobType.getID() == JobTypeRegistry.recreationID) {
				if (Logging.logEnabled) {
					Candidate bestHaul = sorted.stream()
							.filter(candidate -> candidate.found != null && candidate.found.job instanceof necesse.level.maps.levelData.jobs.HaulFromLevelJob)
							.min(CANDIDATE_PRIORITY_COMPARATOR)
							.orElse(null);
					if (bestHaul != null) {
						Logging.logMessage("[CrossLevelHaulingDebug] Hauling deferred before reachability settler=" + human.getUniqueID()
								+ " recreationLevel=" + human.getRecreationLevel()
								+ " wantsRecreation=" + human.wantsToDoRecreation()
								+ " timeOfDay=" + human.getWorldEntity().getTimeOfDay()
								+ " winningRecreation=" + describe(first) + " key=" + first.priorityKey
								+ " bestHaul=" + describe(bestHaul) + " key=" + bestHaul.priorityKey);
					}
				}
				int baseEnd = index;
				ArrayList<Candidate> reachableRecreation = new ArrayList<>();
				while (baseEnd < sorted.size() && key.sameBeforeRecreationPreference(sorted.get(baseEnd).priorityKey)) {
					Candidate candidate = sorted.get(baseEnd++);
					if (evaluateReachability(human, settlement, domain, candidate)) reachableRecreation.add(candidate);
				}

				if (!reachableRecreation.isEmpty()) {
					boolean randomKind = GameRandom.globalRandom.nextDouble() < RECREATION_RANDOM_KIND_CHANCE;
					ArrayList<Candidate> selectedPool;
					String selectedKind;

					if (randomKind) {
						LinkedHashMap<String, ArrayList<Candidate>> byKind = new LinkedHashMap<>();
						for (Candidate candidate : reachableRecreation) {
							String kind = candidate.found.job.getStringID();
							byKind.computeIfAbsent(kind, ignored -> new ArrayList<>()).add(candidate);
						}
						ArrayList<String> kinds = new ArrayList<>(byKind.keySet());
						selectedKind = kinds.get(GameRandom.globalRandom.nextInt(kinds.size()));
						selectedPool = byKind.get(selectedKind);
					}
					else {
						Candidate vanillaBest = reachableRecreation.stream().min(CANDIDATE_PRIORITY_COMPARATOR).orElse(null);
						selectedKind = vanillaBest == null ? "none" : vanillaBest.found.job.getStringID();
						selectedPool = new ArrayList<>();
						if (vanillaBest != null) {
							for (Candidate candidate : reachableRecreation) {
								if (candidate.priorityKey.equals(vanillaBest.priorityKey)) selectedPool.add(candidate);
							}
						}
					}

					if (randomKind && !selectedPool.isEmpty()) {
						Candidate bestInKind = selectedPool.stream().min(CANDIDATE_PRIORITY_COMPARATOR).orElse(null);
						PriorityKey bestKey = bestInKind == null ? null : bestInKind.priorityKey;
						if (bestKey != null) selectedPool.removeIf(candidate -> !candidate.priorityKey.equals(bestKey));
					}

					if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Recreation choice settler=" + human.getUniqueID()
							+ " mode=" + (randomKind ? "RANDOM_20" : "VANILLA_80")
							+ " kind=" + selectedKind
							+ " reachableKinds=" + reachableRecreation.stream().map(candidate -> candidate.found.job.getStringID()).distinct().count()
							+ " candidates=" + selectedPool.size());
					if (!selectedPool.isEmpty()) return selectedPool;
				}

				index = baseEnd;
				groupIndex++;
				continue;
			}

			int groupStart = index;
			ArrayList<Candidate> reachable = new ArrayList<>();
			while (index < sorted.size() && key.equals(sorted.get(index).priorityKey)) {
				Candidate candidate = sorted.get(index++);
				if (evaluateReachability(human, settlement, domain, candidate)) reachable.add(candidate);
			}
			if (!reachable.isEmpty()) {
				if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Priority group selected settler=" + human.getUniqueID()
						+ " group=" + groupIndex + " key=" + key + " groupSize=" + (index - groupStart) + " reachable=" + reachable.size());
				return reachable;
			}
			groupIndex++;
		}

		if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Priority grouping found no reachable candidates settler=" + human.getUniqueID());
		return Collections.emptyList();
	}

	private static boolean evaluateReachability(HumanMob human, ServerSettlementData settlement, SettlementLevelDomain domain, Candidate candidate) {
		try {
			if (candidate.levelType == domain.getLevelType(human.getLevel().getIdentifier())) {
				if (!candidate.found.canMoveTo()) return false;
				candidate.travelCost = candidate.found.getDistanceFromWorker() / 32.0;
				return true;
			}

			AbstractLevelJob job = candidate.found.job;
			SettlementLevelPosition target = new SettlementLevelPosition(job.getLevel().getIdentifier(), job.getTileX(), job.getTileY());
			SettlementCrossLevelRoute route = SettlementCrossLevelRouting.findBestRouteQuiet(human, domain, target, true, SettlementLevelZoneSystem.getRouteRestriction(human));
			if (route == null) return false;
			candidate.route = route;
			candidate.travelCost = route.totalDistance;
			return true;
		}
		catch (Exception e) {
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Candidate reachability check failed settler=" + human.getUniqueID() + " job=" + describe(candidate) + " error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
			return false;
		}
	}

	private static Candidate selectCandidate(HumanMob human, SettlementLevelType currentType, List<Candidate> candidates, long now) {
		if (candidates.isEmpty()) return null;

		if (Logging.logEnabled) {
			Logging.logMessage("[CrossLevelJobs] Selection begin settler=" + human.getUniqueID() + " priorityGroupCandidates=" + candidates.size());
			for (Candidate candidate : candidates) {
				Logging.logMessage("[CrossLevelJobs] Selection candidate settler=" + human.getUniqueID()
						+ " job=" + describe(candidate)
						+ " type=" + candidate.found.job.jobType.getStringID()
						+ " key=" + candidate.priorityKey
						+ " travelCost=" + candidate.travelCost
						+ " waitingMS=" + candidate.waitingMs(now));
			}
		}

		Candidate forced = candidates.stream()
				.filter(c -> c.waitingMs(now) >= FORCE_OLD_JOB_MS)
				.max(Comparator.comparingLong(c -> c.waitingMs(now)))
				.orElse(null);
		if (forced != null) {
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Starvation override selected old job settler=" + human.getUniqueID() + " job=" + describe(forced) + " waitingMS=" + forced.waitingMs(now));
			return forced;
		}

		double bestCost = candidates.stream().mapToDouble(c -> c.travelCost).min().orElse(Double.MAX_VALUE);
		double bandLimit = bestCost + Math.max(COST_BAND_MIN_TILES, bestCost * COST_BAND_PERCENT);
		ArrayList<Candidate> band = new ArrayList<>();
		for (Candidate candidate : candidates) {
			boolean agedOverride = candidate.waitingMs(now) >= AGED_JOB_MS;
			boolean inBand = candidate.travelCost <= bandLimit || agedOverride;
			if (inBand) band.add(candidate);
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Cost band candidate settler=" + human.getUniqueID()
					+ " job=" + describe(candidate)
					+ " travelCost=" + candidate.travelCost
					+ " bestCost=" + bestCost
					+ " bandLimit=" + bandLimit
					+ " agedOverride=" + agedOverride
					+ " included=" + inBand);
		}
		if (band.isEmpty()) {
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Selection failed because cost band is empty settler=" + human.getUniqueID() + " bestCost=" + bestCost + " bandLimit=" + bandLimit);
			return null;
		}

		Candidate currentBest = nearestOnLevel(band, currentType);
		SettlementLevelType otherType = currentType == SettlementLevelType.SURFACE ? SettlementLevelType.CAVE : SettlementLevelType.SURFACE;
		Candidate otherBest = nearestOnLevel(band, otherType);
		if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Best candidate per level settler=" + human.getUniqueID()
				+ " current=" + describe(currentBest)
				+ " currentCost=" + (currentBest == null ? "n/a" : currentBest.travelCost)
				+ " other=" + describe(otherBest)
				+ " otherCost=" + (otherBest == null ? "n/a" : otherBest.travelCost));

		SettlementLevelPreference preference = SettlementLevelPreferenceSystem.getPreference(human);
		if (currentBest == null) {
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Preference roll not applicable because only remote candidates remain settler="
					+ human.getUniqueID()
					+ " preference=" + preference
					+ " selected=" + describe(otherBest));
			return otherBest;
		}
		if (otherBest == null) {
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Preference roll not applicable because only current-level candidates remain settler="
					+ human.getUniqueID()
					+ " preference=" + preference
					+ " selected=" + describe(currentBest));
			return currentBest;
		}

		double preferenceWeight;
		double currentWeight;
		double otherWeight;
		String levelBias;
		if (human instanceof GuardHumanMob) {
			SettlementLevelType assignedType = GuardLevelAssignmentSystem.getAssignedLevelType((GuardHumanMob)human);
			preferenceWeight = GUARD_ASSIGNED_LEVEL_WEIGHT;
			currentWeight = currentType == assignedType ? GUARD_ASSIGNED_LEVEL_WEIGHT : 1.0;
			otherWeight = otherType == assignedType ? GUARD_ASSIGNED_LEVEL_WEIGHT : 1.0;
			levelBias = "guard=" + assignedType;
		}
		else {
			preferenceWeight = preference == SettlementLevelPreference.AUTO
					? 1.0
					: PREFERENCE_WEIGHT_MIN + GameRandom.globalRandom.nextDouble() * (PREFERENCE_WEIGHT_MAX - PREFERENCE_WEIGHT_MIN);
			currentWeight = levelWeight(preference, currentType, currentType, preferenceWeight);
			otherWeight = levelWeight(preference, currentType, otherType, preferenceWeight);
			levelBias = "preference=" + preference;
		}
		double roll = GameRandom.globalRandom.nextDouble() * (currentWeight + otherWeight);
		Candidate selected = roll < currentWeight ? currentBest : otherBest;

		if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Level choice settler=" + human.getUniqueID()
				+ " " + levelBias
				+ " preferenceWeight=" + preferenceWeight
				+ " currentLevel=" + currentType
				+ " currentWeight=" + currentWeight
				+ " otherLevel=" + otherType
				+ " otherWeight=" + otherWeight
				+ " roll=" + roll
				+ " totalWeight=" + (currentWeight + otherWeight)
				+ " selected=" + describe(selected));
		return selected;
	}

	private static double levelWeight(SettlementLevelPreference preference, SettlementLevelType currentType, SettlementLevelType candidateType, double preferenceWeight) {
		double weight = candidateType == currentType ? CURRENT_LEVEL_WEIGHT : 1.0;
		if (preference != SettlementLevelPreference.AUTO && isPreferred(preference, candidateType)) weight *= preferenceWeight;
		return weight;
	}

	private static boolean isPreferred(SettlementLevelPreference preference, SettlementLevelType type) {
		return preference == SettlementLevelPreference.SURFACE && type == SettlementLevelType.SURFACE
				|| preference == SettlementLevelPreference.CAVE && type == SettlementLevelType.CAVE;
	}

	private static Candidate nearestOnLevel(List<Candidate> candidates, SettlementLevelType levelType) {
		return candidates.stream()
				.filter(candidate -> candidate.levelType == levelType)
				.min(Comparator.comparingDouble(candidate -> candidate.travelCost))
				.orElse(null);
	}

	private static synchronized long markSeen(JobKey key, long now) {
		SeenJob seen = seenJobs.get(key);
		if (seen == null) {
			seen = new SeenJob(now, now);
			seenJobs.put(key, seen);
		}
		else {
			seen.lastSeenTime = now;
		}
		return seen.firstSeenTime;
	}

	private static synchronized void cleanupSeenJobs(long now) {
		if (now < nextSeenJobCleanupTime) return;
		nextSeenJobCleanupTime = now + JOB_KEY_CLEANUP_INTERVAL_MS;
		seenJobs.entrySet().removeIf(entry -> now - entry.getValue().lastSeenTime > STALE_JOB_KEY_MS);
	}

	private static Level getLoadedLevel(SettlementLevelDomain domain, SettlementLevelType type) {
		Level level = domain.getLoadedLevel(type);
		if (level != null) return level;
		ServerSettlementData settlement = domain.getSettlement();
		if (settlement.getServer() == null) return null;
		LevelIdentifier identifier = domain.getLevelIdentifier(type);
		if (identifier == null || !settlement.getServer().world.levelExists(identifier)) {
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Cannot discover jobs because settlement level does not exist settlement=" + domain.getSettlementUniqueID() + " levelType=" + type + " identifier=" + identifier);
			return null;
		}
		level = settlement.getServer().world.getLevel(identifier);
		if (level == null && Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] FAILED to load settlement level for job discovery settlement=" + domain.getSettlementUniqueID() + " levelType=" + type + " identifier=" + identifier);
		return level;
	}

	private static String describe(Candidate candidate) {
		if (candidate == null || candidate.found == null || candidate.found.job == null) return "null";
		return candidate.found.job.getStringID() + "@" + candidate.levelType + ":" + candidate.found.job.getTileX() + "," + candidate.found.job.getTileY();
	}

	private static final Comparator<Candidate> CANDIDATE_PRIORITY_COMPARATOR = (a, b) -> a.priorityKey.compareTo(b.priorityKey);

	private static final class Candidate {
		final FoundJob found;
		final SettlementLevelType levelType;
		final PriorityKey priorityKey;
		final JobKey jobKey;
		final long firstSeenTime;
		double travelCost = Double.MAX_VALUE;
		SettlementCrossLevelRoute route;

		Candidate(FoundJob found, SettlementLevelType levelType, PriorityKey priorityKey, JobKey jobKey, long firstSeenTime) {
			this.found = found;
			this.levelType = levelType;
			this.priorityKey = priorityKey;
			this.jobKey = jobKey;
			this.firstSeenTime = firstSeenTime;
		}

		long waitingMs(long now) {
			return Math.max(0L, now - firstSeenTime);
		}
	}

	private static final class PriorityKey implements Comparable<PriorityKey> {
		final int firstPriority;
		final int prioritizeNextOrder;
		final int lastPerformedOrder;
		final int afterPriority;
		final int typePriorityOrder;
		final int typeIDOrder;
		final int sameTypePriority;
		final int sameJobPriority;

		PriorityKey(int firstPriority, int prioritizeNextOrder, int lastPerformedOrder, int afterPriority, int typePriorityOrder, int typeIDOrder, int sameTypePriority, int sameJobPriority) {
			this.firstPriority = firstPriority;
			this.prioritizeNextOrder = prioritizeNextOrder;
			this.lastPerformedOrder = lastPerformedOrder;
			this.afterPriority = afterPriority;
			this.typePriorityOrder = typePriorityOrder;
			this.typeIDOrder = typeIDOrder;
			this.sameTypePriority = sameTypePriority;
			this.sameJobPriority = sameJobPriority;
		}

		static PriorityKey from(FoundJob found) {
			JobTypeHandler handler = found.worker.getJobTypeHandler();
			JobTypeHandler.TypePriority priority = found.priority;
			int firstPriority = found.job.getFirstPriority(found);
			int prioritizeNextOrder = handler != null && handler.prioritizeNextJobID == found.job.getID() ? 0 : 1;
			int lastPerformedOrder = handler != null && handler.lastPerformedJobID == found.job.getID() ? 0 : 1;
			int afterPriority = found.job.getAfterPrioritizedPriority(found);
			int typePriorityOrder = priority == null ? 100000 : -priority.priority;
			int typeIDOrder = priority == null ? 100000 : -priority.type.getID();
			int sameTypePriority = found.job.getSameTypePriority(found);
			int sameJobPriority = found.job.getSameJobPriority();
			return new PriorityKey(firstPriority, prioritizeNextOrder, lastPerformedOrder, afterPriority, typePriorityOrder, typeIDOrder, sameTypePriority, sameJobPriority);
		}

		boolean sameBeforeRecreationPreference(PriorityKey other) {
			return other != null
					&& firstPriority == other.firstPriority
					&& prioritizeNextOrder == other.prioritizeNextOrder
					&& lastPerformedOrder == other.lastPerformedOrder
					&& afterPriority == other.afterPriority
					&& typePriorityOrder == other.typePriorityOrder
					&& typeIDOrder == other.typeIDOrder;
		}

		@Override
		public int compareTo(PriorityKey other) {
			int value = Integer.compare(-firstPriority, -other.firstPriority);
			if (value != 0) return value;
			value = Integer.compare(prioritizeNextOrder, other.prioritizeNextOrder);
			if (value != 0) return value;
			value = Integer.compare(lastPerformedOrder, other.lastPerformedOrder);
			if (value != 0) return value;
			value = Integer.compare(-afterPriority, -other.afterPriority);
			if (value != 0) return value;
			value = Integer.compare(typePriorityOrder, other.typePriorityOrder);
			if (value != 0) return value;
			value = Integer.compare(typeIDOrder, other.typeIDOrder);
			if (value != 0) return value;
			value = Integer.compare(-sameTypePriority, -other.sameTypePriority);
			if (value != 0) return value;
			return Integer.compare(-sameJobPriority, -other.sameJobPriority);
		}

		@Override
		public String toString() {
			return "{first=" + firstPriority
					+ ", next=" + prioritizeNextOrder
					+ ", last=" + lastPerformedOrder
					+ ", after=" + afterPriority
					+ ", typePriority=" + typePriorityOrder
					+ ", typeID=" + typeIDOrder
					+ ", sameType=" + sameTypePriority
					+ ", sameJob=" + sameJobPriority + "}";
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof PriorityKey && compareTo((PriorityKey)obj) == 0;
		}

		@Override
		public int hashCode() {
			return Objects.hash(firstPriority, prioritizeNextOrder, lastPerformedOrder, afterPriority, typePriorityOrder, typeIDOrder, sameTypePriority, sameJobPriority);
		}
	}

	private static final class JobKey {
		final int settlementUniqueID;
		final LevelIdentifier levelIdentifier;
		final int jobID;
		final int tileX;
		final int tileY;

		JobKey(int settlementUniqueID, LevelIdentifier levelIdentifier, int jobID, int tileX, int tileY) {
			this.settlementUniqueID = settlementUniqueID;
			this.levelIdentifier = levelIdentifier;
			this.jobID = jobID;
			this.tileX = tileX;
			this.tileY = tileY;
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof JobKey)) return false;
			JobKey other = (JobKey)obj;
			return settlementUniqueID == other.settlementUniqueID && jobID == other.jobID && tileX == other.tileX && tileY == other.tileY && Objects.equals(levelIdentifier, other.levelIdentifier);
		}

		@Override
		public int hashCode() {
			return Objects.hash(settlementUniqueID, levelIdentifier, jobID, tileX, tileY);
		}
	}

	private static final class SeenJob {
		final long firstSeenTime;
		long lastSeenTime;

		SeenJob(long firstSeenTime, long lastSeenTime) {
			this.firstSeenTime = firstSeenTime;
			this.lastSeenTime = lastSeenTime;
		}
	}


	/**
	 * Worker view used only for discovering jobs on another Level. It never executes a
	 * remote JobSequence; execution always happens after relocation and a fresh vanilla search.
	 */
	private static final class RemoteWorker implements EntityJobWorker {
		final HumanMob human;
		final ServerSettlementData settlement;
		final SettlementLevelDomain domain;
		final Level level;
		final Point searchOrigin;

		RemoteWorker(HumanMob human, ServerSettlementData settlement, SettlementLevelDomain domain, Level level, Point searchOrigin) {
			this.human = human;
			this.settlement = settlement;
			this.domain = domain;
			this.level = level;
			this.searchOrigin = searchOrigin;
		}

		@Override
		public Mob getMobWorker() {
			return human;
		}

		@Override
		public Level getLevel() {
			return level;
		}

		@Override
		public Point getJobSearchTile() {
			return new Point(searchOrigin);
		}

		@Override
		public Rectangle getJobSearchBounds() {
			Rectangle tileBounds = domain.getTileBounds(domain.getLevelType(level.getIdentifier()));
			if (tileBounds == null) return EntityJobWorker.super.getJobSearchBounds();

			// JobsLayerManager.streamValidJobsInRegionsShape expects level/pixel coordinates,
			// while SettlementLevelDomain exposes settlement bounds in tile coordinates.
			// Passing tile bounds directly makes remote discovery scan the wrong regions
			// and return zero jobs even when valid jobs exist on the remote level.
			return new Rectangle(tileBounds.x * 32, tileBounds.y * 32, tileBounds.width * 32, tileBounds.height * 32);
		}

		@SuppressWarnings("rawtypes")
		@Override
		public Stream streamValidJobsWithinRange(Rectangle searchBounds) {
			if (searchBounds == null) searchBounds = getJobSearchBounds();
			return level.jobsLayer.streamValidJobsInRegionsShape(searchBounds, 0);
		}

		@SuppressWarnings("rawtypes")
		@Override
		public Stream streamExtraJobs() {
			// Extra jobs are generated by the real HumanMob for its current level and can
			// include recreation/chat jobs that assume the worker itself is a
			// JobWorkerChatter. They must not be projected onto the remote level. Remote
			// discovery is intentionally limited to that level's jobsLayer; once the
			// settler relocates, vanilla can generate its normal extra jobs there.
			return Stream.empty();
		}

		@Override
		public boolean allowsFindingJobType(JobType jobType) {
			return human.allowsFindingJobType(jobType);
		}

		@Override
		public boolean estimateCanMoveTo(int tileX, int tileY, boolean acceptAdjacentTiles) {
			SettlementLevelPosition target = new SettlementLevelPosition(level.getIdentifier(), tileX, tileY);
			return SettlementCrossLevelRouting.findBestRouteQuiet(human, domain, target, acceptAdjacentTiles, SettlementLevelZoneSystem.getRouteRestriction(human)) != null;
		}

		@Override
		public ZoneTester getJobRestrictZone() {
			LevelSettler levelSettler = settlement.getSettler(human.getUniqueID());
			if (levelSettler == null) {
				if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Remote worker could not resolve LevelSettler settler=" + human.getUniqueID());
				return (tileX, tileY) -> false;
			}
			LevelIdentifier identifier = level.getIdentifier();
			return (tileX, tileY) -> SettlementLevelZoneSystem.isTileAllowed(levelSettler, domain, identifier, tileX, tileY);
		}

		@Override public JobTypeHandler getJobTypeHandler() { return human.getJobTypeHandler(); }
		@Override public Mob getJobFollowingMob() { return human.getJobFollowingMob(); }
		@Override public WorkInventory getWorkInventory() { return human.getWorkInventory(); }
		@Override public void showPickupAnimation(int x, int y, Item item, int time, boolean dir) { human.showPickupAnimation(x, y, item, time, dir); }
		@Override public void showPlaceAnimation(int x, int y, Item item, int time, boolean dir) { human.showPlaceAnimation(x, y, item, time, dir); }
		@Override public void showWorkAnimation(int x, int y, Item item, int time, boolean dir) { human.showWorkAnimation(x, y, item, time, dir); }
		@Override public void showAttackAnimation(int x, int y, Item item, int time, boolean dir) { human.showAttackAnimation(x, y, item, time, dir); }
		@Override public float getWorkSpeedModifier() { return human.getWorkSpeedModifier(); }
		@Override public void showHoldAnimation(Item item, int time) { human.showHoldAnimation(item, time); }
		@Override public void clearHoldAnimation() { human.clearHoldAnimation(); }
		@Override public boolean hasActiveJob() { return human.hasActiveJob(); }
		@Override public boolean isInWorkAnimation() { return human.isInWorkAnimation(); }
		@Override public boolean isJobCancelled() { return human.isJobCancelled(); }
		@Override public void resetJobCancelled() { human.resetJobCancelled(); }
		@Override public void onPerformedJob(AbstractLevelJob job, JobTypeHandler.TypePriority priority) { human.onPerformedJob(job, priority); }
		@Override public void useRecreation(float amount, float happiness) { human.useRecreation(amount, happiness); }
		@Override public void regenRecreation(float amount, float happiness, String identifier) { human.regenRecreation(amount, happiness, identifier); }
		@Override public float getRecreationRegenModifier(String identifier) { return human.getRecreationRegenModifier(identifier); }
		@Override public boolean wantsToDoRecreation() { return human.wantsToDoRecreation(); }
		@Override public boolean isOnStrike() { return human.isOnStrike(); }
		@Override public boolean attemptStartStrike(boolean activateOthers) { return human.attemptStartStrike(activateOthers); }
	}

	private static final class RelocationFoundJob extends FoundJob {
		final HumanMob human;
		final Candidate selected;

		RelocationFoundJob(HumanMob human, Candidate selected) {
			super(human, selected.found.job, selected.found.priority, selected.found.preSequenceCompute);
			this.human = human;
			this.selected = selected;
		}

		@Override
		public void startCooldown(long currentTime) {
			// The remote job has not been claimed or executed yet. Starting its vanilla
			// cooldown here would incorrectly hide it while the settler is only travelling.
		}

		@Override
		protected JobSequence getNewSequence() {
			JobTypeHandler handler = human.getJobTypeHandler();
			if (handler != null) {
				// Bias the immediate post-transition vanilla search back toward the job that
				// justified the relocation, without reserving/capturing its sequence early.
				handler.prioritizeNextJobID = selected.found.job.getID();
				handler.resetPrioritizeNextJobIfFound = true;
			}
			LinkedListJobSequence sequence = new LinkedListJobSequence(new StaticMessage("Travelling to work"), false);
			sequence.add(new RelocationActiveJob(human, selected.found.priority, selected.route, selected.levelType, selected.found.job.getStringID(), selected.found.job.getTileX(), selected.found.job.getTileY()));
			return sequence;
		}
	}

	private static final class RelocationActiveJob extends ActiveJob {
		final HumanMob human;
		final SettlementCrossLevelRoute route;
		final SettlementLevelType targetType;
		final String selectedJobStringID;
		final int selectedJobTileX;
		final int selectedJobTileY;

		RelocationActiveJob(HumanMob human, JobTypeHandler.TypePriority priority, SettlementCrossLevelRoute route, SettlementLevelType targetType, String selectedJobStringID, int selectedJobTileX, int selectedJobTileY) {
			super(human, priority);
			this.human = human;
			this.route = route;
			this.targetType = targetType;
			this.selectedJobStringID = selectedJobStringID;
			this.selectedJobTileX = selectedJobTileX;
			this.selectedJobTileY = selectedJobTileY;
		}

		@Override
		public JobMoveToTile getMoveToTile(JobMoveToTile last) {
			if (route == null || route.ladder == null || route.source == null) return null;
			ServerSettlementData settlement = human.getSettlerSettlementServerData();
			SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
			SettlementLevelType sourceType = domain == null || human.getLevel() == null ? null : domain.getLevelType(human.getLevel().getIdentifier());
			if (sourceType == null || sourceType == targetType) return null;
			return new JobMoveToTile(route.ladder.getTileX(sourceType), route.ladder.getTileY(sourceType), false);
		}

		@Override
		public boolean isAt(JobMoveToTile moveToTile) {
			return moveToTile != null && human.getTileX() == moveToTile.tileX && human.getTileY() == moveToTile.tileY && !human.hasCurrentMovement();
		}

		@Override
		public void tick(boolean isCurrent, boolean isMovingTo) {
		}

		@Override
		public boolean isValid(boolean isCurrent) {
			if (human.getLevel() == null || route == null || route.ladder == null) return false;
			ServerSettlementData settlement = human.getSettlerSettlementServerData();
			SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
			if (domain == null) {
				if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Relocation invalid because settlement domain disappeared settler=" + human.getUniqueID());
				return false;
			}
			SettlementLevelType currentType = domain.getLevelType(human.getLevel().getIdentifier());
			if (currentType == targetType) return true;
			for (SettlementLadderLink link : SettlementLadderSystem.getValidLinks(domain, true)) {
				if (link.settlementUniqueID == route.ladder.settlementUniqueID
						&& link.surfaceTileX == route.ladder.surfaceTileX && link.surfaceTileY == route.ladder.surfaceTileY
						&& link.caveTileX == route.ladder.caveTileX && link.caveTileY == route.ladder.caveTileY) return true;
			}
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Relocation invalid because designated ladder disappeared settler=" + human.getUniqueID() + " route=" + route);
			return false;
		}

		@Override
		public ActiveJobHitResult onHit(MobWasHitEvent event, boolean isMovingTo) {
			return ActiveJobHitResult.CLEAR_SEQUENCE;
		}

		@Override
		public ActiveJobTargetFoundResult onTargetFound(Mob target, boolean isCurrent, boolean isMovingTo) {
			return ActiveJobTargetFoundResult.CONTINUE;
		}

		@Override
		public ActiveJobResult perform() {
			if (human.getLevel() == null) {
				if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Relocation perform failed because settler has no level settler=" + human.getUniqueID());
				return ActiveJobResult.FAILED;
			}
			ServerSettlementData settlement = human.getSettlerSettlementServerData();
			SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
			if (domain == null) {
				if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Relocation perform failed because settlement domain is missing settler=" + human.getUniqueID());
				return ActiveJobResult.FAILED;
			}
			SettlementLevelType currentType = domain.getLevelType(human.getLevel().getIdentifier());
			if (currentType == targetType) return ActiveJobResult.FINISHED;
			if (currentType == null || route == null || route.ladder == null) {
				if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Relocation perform failed because current level/route is invalid settler=" + human.getUniqueID() + " current=" + currentType + " route=" + route);
				return ActiveJobResult.FAILED;
			}
			int ladderX = route.ladder.getTileX(currentType);
			int ladderY = route.ladder.getTileY(currentType);
			if (human.getTileX() != ladderX || human.getTileY() != ladderY) return ActiveJobResult.MOVE_TO;

			boolean transitioned = SettlementLadderSystem.transitionMob(human, route.ladder, targetType);
			if (!transitioned) {
				if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Relocation transition FAILED settler=" + human.getUniqueID() + " targetLevel=" + targetType + " ladder=" + ladderX + "," + ladderY + " selectedJob=" + selectedJobStringID + "@" + selectedJobTileX + "," + selectedJobTileY);
				return ActiveJobResult.FAILED;
			}
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Relocation transition completed settler=" + human.getUniqueID() + " targetLevel=" + targetType + " selectedJob=" + selectedJobStringID + "@" + selectedJobTileX + "," + selectedJobTileY);
			return ActiveJobResult.FINISHED;
		}

		@Override
		public void onCancelled(boolean becauseOfInvalid, boolean isCurrent, boolean isMovingTo) {
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelJobs] Relocation cancelled settler=" + human.getUniqueID() + " invalid=" + becauseOfInvalid + " moving=" + isMovingTo + " targetLevel=" + targetType + " selectedJob=" + selectedJobStringID + "@" + selectedJobTileX + "," + selectedJobTileY);
		}
	}
}
