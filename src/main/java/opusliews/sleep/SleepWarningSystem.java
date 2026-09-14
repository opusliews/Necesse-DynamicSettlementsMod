package opusliews.sleep;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import necesse.engine.localization.message.LocalMessage;
import necesse.engine.network.packet.PacketMobChat;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.util.GameUtils;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.ai.behaviourTree.leaves.ChaserAINode;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.inventory.container.BedContainer;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.NetworkSettlementData;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opusliews.guard.GuardEmergencySystem;
import opusliews.guard.GuardFatigueSystem;
import opusliews.guard.GuardNeedsSystem;
import opusliews.logging.Logging;

public final class SleepWarningSystem {
	public enum WakeReason {
		RAID(1, "sleepwarningraid"),
		BARRIER_ATTACK(2, "sleepwarningbarrierattack"),
		BARRIER_BREACH(3, "sleepwarningbarrierbreach");

		private final int priority;
		private final String messageKey;

		WakeReason(int priority, String messageKey) {
			this.priority = priority;
			this.messageKey = messageKey;
		}
	}

	private static final int witnessRangePixels = 20 * 32;
	private static final long assignmentRetryMs = 500L;
	private static final long raidCheckIntervalMs = 1000L;
	private static final long soleDefenderGraceMs = 5000L;
	private static final Map<Level, LevelState> states = Collections.synchronizedMap(new WeakHashMap<>());
	private static final ThreadLocal<Integer> raidPreventSleepDepth = ThreadLocal.withInitial(() -> 0);

	private SleepWarningSystem() {
	}

	public static void beginRaidPreventSleepScope() {
		raidPreventSleepDepth.set(raidPreventSleepDepth.get() + 1);
	}

	public static void endRaidPreventSleepScope() {
		int depth = raidPreventSleepDepth.get() - 1;
		if (depth <= 0) {
			raidPreventSleepDepth.remove();
		}
		else {
			raidPreventSleepDepth.set(depth);
		}
	}

	public static boolean shouldSuppressRaidPreventSleep() {
		return raidPreventSleepDepth.get() > 0;
	}

	public static void onRaidTick(Level level, int settlementUniqueID) {
		if (level == null || !level.isServer() || settlementUniqueID == 0) {
			return;
		}

		LevelState state = getState(level);
		long now = level.getTime();
		synchronized (state) {
			long nextCheck = state.nextRaidChecks.getOrDefault(settlementUniqueID, 0L);
			if (now < nextCheck) {
				return;
			}
			state.nextRaidChecks.put(settlementUniqueID, now + raidCheckIntervalMs);
		}

		requestWake(level, settlementUniqueID, WakeReason.RAID);
	}

	public static void onRaidStarted(Level level, int settlementUniqueID) {
		if (level == null || !level.isServer() || settlementUniqueID == 0) {
			return;
		}

		level.entityManager.mobs.stream()
				.filter(mob -> mob instanceof GuardHumanMob)
				.map(mob -> (GuardHumanMob)mob)
				.filter(guard -> isSettlementGuard(guard, settlementUniqueID))
				.forEach(guard -> {
					if (GuardFatigueSystem.isScheduledRestPeriod(guard)) {
						GuardFatigueSystem.markRestCombat(guard, "raid arrival");
					}

					GuardEmergencySystem.preempt(guard);
				});
	}

	public static void onBarrierAttacked(Level level, int settlementUniqueID) {
		requestWake(level, settlementUniqueID, WakeReason.BARRIER_ATTACK);
	}

	public static void onBarrierBreached(Level level, int settlementUniqueID) {
		requestWake(level, settlementUniqueID, WakeReason.BARRIER_BREACH);
	}

	public static boolean isBarrierWitnessed(Level level, int settlementUniqueID, Mob attacker) {
		if (level == null || attacker == null || settlementUniqueID == 0) {
			return false;
		}

		return level.entityManager.mobs.stream()
				.filter(mob -> mob instanceof GuardHumanMob)
				.map(mob -> (GuardHumanMob)mob)
				.filter(guard -> isSettlementGuard(guard, settlementUniqueID))
				.filter(guard -> !guard.isHiding && !guard.hasCommandOrders())
				.filter(guard -> !GuardFatigueSystem.isScheduledRestPeriod(guard) || GuardNeedsSystem.isInCombat(guard))
				.anyMatch(guard -> guard.getDistance(attacker) <= witnessRangePixels
						&& ChaserAINode.hasLineOfSightToTarget(guard, attacker));
	}

	public static boolean hasWakeAssignment(GuardHumanMob guard) {
		return findAssignedRequest(guard) != null;
	}

	public static PlayerMob getWakeTarget(GuardHumanMob guard) {
		WakeRequest request = findAssignedRequest(guard);
		if (request == null || guard == null || guard.getLevel() == null) {
			return null;
		}

		Mob target = GameUtils.getLevelMob(request.targetPlayerUniqueID, guard.getLevel());
		if (!(target instanceof PlayerMob)) {
			return null;
		}

		PlayerMob player = (PlayerMob)target;
		return player.isServerClient() && player.getServerClient().getContainer() instanceof BedContainer ? player : null;
	}

	public static void completeWake(GuardHumanMob guard) {
		if (guard == null || guard.getLevel() == null || !guard.isServer()) {
			return;
		}

		Level level = guard.getLevel();
		LevelState state = getState(level);
		WakeRequest completed = null;
		synchronized (state) {
			for (WakeRequest request : state.requests.values()) {
				if (request.messengerGuardUniqueID == guard.getUniqueID()) {
					completed = request;
					break;
				}
			}
		}

		if (completed == null) {
			if (Logging.logEnabled) {
				Logging.logMessage("SleepWarningDebug: completeWake ignored, no request for guard=" + guard.getUniqueID());
			}
			return;
		}

		Server server = level.getServer();
		if (server == null) {
			return;
		}

		final int targetPlayerUniqueID = completed.targetPlayerUniqueID;
		ServerClient targetClient = server.streamClients()
				.filter(client -> client.playerMob != null && client.playerMob.getUniqueID() == targetPlayerUniqueID)
				.findFirst()
				.orElse(null);
		if (targetClient == null || !(targetClient.getContainer() instanceof BedContainer)) {
			synchronized (state) {
				state.requests.remove(completed.settlementUniqueID);
				state.nextRaidChecks.remove(completed.settlementUniqueID);
			}
			return;
		}

		Mob targetMob = targetClient.playerMob;
		if (Logging.logEnabled) {
			double distance = guard.getDistance(targetMob);
			Logging.logMessage("SleepWarningDebug: completing wake"
					+ " reason=" + completed.reason
					+ " settlement=" + completed.settlementUniqueID
					+ " guard=" + guard.getUniqueID()
					+ " guardTile=" + guard.getTileX() + "," + guard.getTileY()
					+ " player=" + completed.targetPlayerUniqueID
					+ " playerTile=" + targetMob.getTileX() + "," + targetMob.getTileY()
					+ " distance=" + distance);
		}

		List<ServerClient> sleepingClients = server.streamClients()
				.filter(client -> client.getContainer() instanceof BedContainer)
				.collect(java.util.stream.Collectors.toList());

		clearAllWakeRequests();
		for (ServerClient client : sleepingClients) {
			client.closeContainer(true);
		}

		SleepSimulationSystem.stopServer(server);
		server.network.sendToClientsWithEntity(
				new PacketMobChat(guard.getUniqueID(), new LocalMessage("misc", completed.reason.messageKey)),
				guard
		);
	}

	public static boolean isRaidActiveForGuard(GuardHumanMob guard) {
		if (guard == null) {
			return false;
		}

		NetworkSettlementData settlement = guard.getSettlerSettlementNetworkData();
		return settlement != null && settlement.isRaidActive();
	}

	public static void tick(Level level) {
		if (level == null || !level.isServer()) {
			return;
		}

		LevelState state = getState(level);
		List<WakeRequest> requests;
		synchronized (state) {
			if (state.requests.isEmpty()) {
				return;
			}
			requests = new ArrayList<>(state.requests.values());
		}

		long now = level.getTime();
		for (WakeRequest request : requests) {
			ServerClient targetClient = findSleepingClient(level, request.settlementUniqueID);
			if (targetClient == null) {
				if (Logging.logEnabled) {
					Logging.logMessage("SleepWarningDebug: removing wake request because no sleeping client remains"
							+ " settlement=" + request.settlementUniqueID
							+ " reason=" + request.reason
							+ " messenger=" + request.messengerGuardUniqueID);
				}
				removeRequest(level, request.settlementUniqueID);
				continue;
			}

			request.targetPlayerUniqueID = targetClient.playerMob.getUniqueID();
			GuardHumanMob messenger = getGuard(level, request.messengerGuardUniqueID);
			if (messenger != null && isValidMessenger(messenger, request.settlementUniqueID)) {
				continue;
			}

			request.messengerGuardUniqueID = -1;
			if (now >= request.nextAssignmentTime) {
				request.nextAssignmentTime = now + assignmentRetryMs;
				tryAssignMessenger(level, state, request, targetClient.playerMob);
			}
		}
	}

	private static void requestWake(Level level, int settlementUniqueID, WakeReason reason) {
		if (level == null || !level.isServer() || settlementUniqueID == 0 || reason == null) {
			return;
		}

		SettlementSleepSettings settings = getSettings(level, settlementUniqueID);
		if (!isEnabled(settings, reason)) {
			return;
		}

		ServerClient targetClient = findSleepingClient(level, settlementUniqueID);
		if (targetClient == null) {
			return;
		}

		SettlementSleepSettingsLevelData.get(level, true);
		LevelState state = getState(level);
		WakeRequest request;
		synchronized (state) {
			request = state.requests.get(settlementUniqueID);
			if (request == null) {
				request = new WakeRequest(
						settlementUniqueID,
						reason,
						targetClient.playerMob.getUniqueID(),
						level.getTime()
				);
				state.requests.put(settlementUniqueID, request);
				if (Logging.logEnabled) {
					Logging.logMessage("SleepWarningDebug: created wake request"
							+ " settlement=" + settlementUniqueID
							+ " reason=" + reason
							+ " player=" + targetClient.playerMob.getUniqueID()
							+ " playerTile=" + targetClient.playerMob.getTileX() + "," + targetClient.playerMob.getTileY());
				}
			}
			else {
				if (reason.priority > request.reason.priority) {
					if (Logging.logEnabled) {
						Logging.logMessage("SleepWarningDebug: upgraded wake request"
								+ " settlement=" + settlementUniqueID
								+ " from=" + request.reason
								+ " to=" + reason);
					}
					request.reason = reason;
				}
				request.targetPlayerUniqueID = targetClient.playerMob.getUniqueID();
			}
		}

		tryAssignMessenger(level, state, request, targetClient.playerMob);
	}

	private static void tryAssignMessenger(Level level, LevelState state, WakeRequest request, PlayerMob targetPlayer) {
		if (request.messengerGuardUniqueID != -1 || targetPlayer == null) {
			return;
		}

		List<GuardHumanMob> active = new ArrayList<>();
		List<GuardHumanMob> resting = new ArrayList<>();
		boolean anyCombat = false;

		for (Iterator iterator = level.entityManager.mobs.stream().iterator(); iterator.hasNext();) {
			Mob mob = (Mob)iterator.next();
			if (!(mob instanceof GuardHumanMob)) {
				continue;
			}

			GuardHumanMob guard = (GuardHumanMob)mob;
			if (!isValidMessenger(guard, request.settlementUniqueID)
					|| isGuardAssigned(state, guard.getUniqueID())) {
				continue;
			}

			if (GuardNeedsSystem.isInCombat(guard)) {
				anyCombat = true;
				continue;
			}

			if (GuardFatigueSystem.isScheduledRestPeriod(guard)) {
				resting.add(guard);
			}
			else {
				active.add(guard);
			}
		}

		if (anyCombat) {
			request.sawGuardCombat = true;
		}

		Comparator<GuardHumanMob> closest = Comparator
				.comparingDouble((GuardHumanMob guard) -> guard.getDistance(targetPlayer))
				.thenComparingInt(Mob::getUniqueID);
		active.sort(closest);
		resting.sort(closest);

		GuardHumanMob chosen = null;
		if (!active.isEmpty()) {
			if (request.reason == WakeReason.RAID
					|| anyCombat
					|| request.sawGuardCombat
					|| active.size() > 1
					|| level.getTime() - request.createdAt >= soleDefenderGraceMs) {
				chosen = active.get(0);
			}
		}

		if (chosen == null && !resting.isEmpty()) {
			chosen = resting.get(0);
		}

		if (chosen == null) {
			return;
		}

		request.messengerGuardUniqueID = chosen.getUniqueID();
		if (Logging.logEnabled) {
			Logging.logMessage("SleepWarningDebug: assigned messenger"
					+ " settlement=" + request.settlementUniqueID
					+ " reason=" + request.reason
					+ " guard=" + chosen.getUniqueID()
					+ " guardTile=" + chosen.getTileX() + "," + chosen.getTileY()
					+ " player=" + targetPlayer.getUniqueID()
					+ " playerTile=" + targetPlayer.getTileX() + "," + targetPlayer.getTileY()
					+ " distance=" + chosen.getDistance(targetPlayer)
					+ " resting=" + GuardFatigueSystem.isScheduledRestPeriod(chosen));
		}
		if (GuardFatigueSystem.isScheduledRestPeriod(chosen)) {
			GuardFatigueSystem.markRestCombat(chosen, "waking sleeping player");
		}
		GuardEmergencySystem.preempt(chosen);
	}

	private static boolean isGuardAssigned(LevelState state, int guardUniqueID) {
		for (WakeRequest request : state.requests.values()) {
			if (request.messengerGuardUniqueID == guardUniqueID) {
				return true;
			}
		}
		return false;
	}

	private static WakeRequest findAssignedRequest(GuardHumanMob guard) {
		if (guard == null || guard.getLevel() == null) {
			return null;
		}

		LevelState state = getState(guard.getLevel());
		synchronized (state) {
			for (WakeRequest request : state.requests.values()) {
				if (request.messengerGuardUniqueID == guard.getUniqueID()) {
					return request;
				}
			}
		}
		return null;
	}

	private static boolean isValidMessenger(GuardHumanMob guard, int settlementUniqueID) {
		return guard != null
				&& !guard.removed()
				&& guard.isServer()
				&& guard.isSettlerOnCurrentLevel()
				&& guard.getSettlementUniqueID() == settlementUniqueID
				&& !guard.adventureParty.isInAdventureParty()
				&& !guard.hasCommandOrders()
				&& !guard.isHiding
				&& !guard.isBeingInteractedWith()
				&& !GuardFatigueSystem.isFullyExhausted(guard);
	}

	private static boolean isSettlementGuard(GuardHumanMob guard, int settlementUniqueID) {
		return guard != null
				&& !guard.removed()
				&& guard.isServer()
				&& guard.isSettlerOnCurrentLevel()
				&& guard.getSettlementUniqueID() == settlementUniqueID
				&& !guard.adventureParty.isInAdventureParty();
	}

	private static GuardHumanMob getGuard(Level level, int uniqueID) {
		if (uniqueID == -1) {
			return null;
		}
		Mob mob = GameUtils.getLevelMob(uniqueID, level);
		return mob instanceof GuardHumanMob ? (GuardHumanMob)mob : null;
	}

	private static ServerClient findSleepingClient(Level level, int settlementUniqueID) {
		if (level == null || level.getServer() == null) {
			return null;
		}

		ServerSettlementData settlement = SettlementsWorldData.getSettlementsData(level)
				.getServerData(settlementUniqueID);
		if (settlement == null) {
			return null;
		}

		Rectangle bounds = settlement.boundsManager.getTileRectangle();
		return level.getServer().streamClients()
				.filter(client -> client.getLevel() == level)
				.filter(client -> client.getContainer() instanceof BedContainer)
				.filter(client -> bounds.contains(client.playerMob.getTileX(), client.playerMob.getTileY()))
				.findFirst()
				.orElse(null);
	}

	private static SettlementSleepSettings getSettings(Level level, int settlementUniqueID) {
		SettlementSleepSettingsLevelData data = SettlementSleepSettingsLevelData.get(level, false);
		return data == null ? SettlementSleepSettings.defaults : data.getSettings(settlementUniqueID);
	}

	private static boolean isEnabled(SettlementSleepSettings settings, WakeReason reason) {
		switch (reason) {
			case RAID:
				return settings.wakeOnRaid;
			case BARRIER_ATTACK:
				return settings.wakeOnBarrierAttack;
			case BARRIER_BREACH:
				return settings.wakeOnBarrierBreach;
			default:
				return false;
		}
	}

	private static void removeRequest(Level level, int settlementUniqueID) {
		LevelState state = getState(level);
		synchronized (state) {
			state.requests.remove(settlementUniqueID);
		}
	}

	private static void clearAllWakeRequests() {
		synchronized (states) {
			for (LevelState state : states.values()) {
				synchronized (state) {
					state.requests.clear();
					state.nextRaidChecks.clear();
				}
			}
		}
	}

	private static LevelState getState(Level level) {
		synchronized (states) {
			return states.computeIfAbsent(level, ignored -> new LevelState());
		}
	}

	private static final class LevelState {
		private final Map<Integer, WakeRequest> requests = new java.util.HashMap<>();
		private final Map<Integer, Long> nextRaidChecks = new java.util.HashMap<>();
	}

	private static final class WakeRequest {
		private final int settlementUniqueID;
		private WakeReason reason;
		private int targetPlayerUniqueID;
		private int messengerGuardUniqueID = -1;
		private final long createdAt;
		private long nextAssignmentTime;
		private boolean sawGuardCombat;

		private WakeRequest(int settlementUniqueID, WakeReason reason, int targetPlayerUniqueID, long createdAt) {
			this.settlementUniqueID = settlementUniqueID;
			this.reason = reason;
			this.targetPlayerUniqueID = targetPlayerUniqueID;
			this.createdAt = createdAt;
		}
	}
}
