package opus.breaching;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import necesse.engine.registries.ObjectLayerRegistry;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.engine.util.GameUtils;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.event.AIEvent;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.level.gameObject.DoorObject;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import opus.guard.GuardFatigueSystem;
import opus.guard.GuardNeedsSystem;
import opus.logging.Logging;
import opus.network.PacketWarningBellRing;
import opus.object.WarningBellObject;

public final class WarningBellSystem {
	private static final int fenceAlertRange = 50;
	private static final long bellCooldownMs = 10000L;
	private static final long alertLifetimeMs = 20000L;
	private static final long safeReleaseGraceMs = 1500L;
	private static final long rebalanceCooldownMs = 2000L;
	private static final Map<Level, LevelState> states = Collections.synchronizedMap(new WeakHashMap<>());

	private WarningBellSystem() {
	}

	public static void onZombieBarrierAttacked(Mob zombie, LevelObject lo) {
		if (zombie == null || lo == null || lo.object == null || zombie.getLevel() == null || !zombie.getLevel().isServer()) {
			Logging.logMessage("WarningBell: attack handler rejected invalid/non-server input");
			return;
		}

		Level level = zombie.getLevel();
		Logging.logMessage("WarningBell: zombie " + zombie.getStringID() + " attacked "
				+ lo.object.getStringID() + " at " + lo.tileX + "," + lo.tileY
				+ " fence=" + lo.object.isFence + " door=" + (lo.object instanceof DoorObject));
		Set<Point> bells = new HashSet<>();
		if (lo.object.isFence) {
			Logging.logMessage("WarningBell: searching connected fence network from " + lo.tileX + "," + lo.tileY);
			collectFenceNetworkBells(level, lo.tileX, lo.tileY, bells);
		}
		else if (lo.object instanceof DoorObject) {
			Logging.logMessage("WarningBell: searching walls adjacent to door at " + lo.tileX + "," + lo.tileY);
			collectDoorSideBells(level, lo.tileX, lo.tileY, bells);
		}

		Logging.logMessage("WarningBell: search found " + bells.size() + " bell(s): " + bells);
		if (bells.isEmpty()) {
			Logging.logMessage("WarningBell: no matching bell found, alert stops here");
			return;
		}

		long now = level.getTime();
		LevelState state = getState(level);
		synchronized (state) {
			pruneFinishedAlerts(level, state, now);
			Point alertTile = new Point(lo.tileX, lo.tileY);
			AlertState alert = state.alerts.get(alertTile);
			boolean newAlert = alert == null;
			if (alert == null) {
				alert = new AlertState(now + alertLifetimeMs, getSettlementUniqueID(level, lo.tileX, lo.tileY));
				state.alerts.put(alertTile, alert);
			}
			alert.expiresAt = now + alertLifetimeMs;
			alert.safeSince = -1L;
			boolean newAttacker = alert.attackers.add(zombie.getUniqueID());

			for (Point bell : bells) {
				Long lastRing = state.lastBellRing.get(bell);
				long elapsed = lastRing == null ? Long.MAX_VALUE : now - lastRing;
				if (lastRing == null || elapsed >= bellCooldownMs) {
					Logging.logMessage("WarningBell: ringing bell at " + bell.x + "," + bell.y
							+ " and sending PacketWarningBellRing");
					state.lastBellRing.put(new Point(bell), now);
					level.getServer().network.sendToClientsWithTile(
							new PacketWarningBellRing(bell.x, bell.y), level, bell.x, bell.y);
				}
				else {
					Logging.logMessage("WarningBell: bell at " + bell.x + "," + bell.y
							+ " is on cooldown (" + elapsed + " ms elapsed)");
				}
			}

			if (newAlert || newAttacker || state.guardAssignments.isEmpty() || now - state.lastRebalance >= rebalanceCooldownMs) {
				rebalanceGuards(level, state, now);
			}
		}
	}

	public static void onZombieBarrierBroken(Mob zombie, GameObject object, int tileX, int tileY) {
		if (zombie == null || object == null || zombie.getLevel() == null || !zombie.getLevel().isServer()) {
			return;
		}

		Level level = zombie.getLevel();
		Set<Point> bells = new HashSet<>();
		if (object.isFence) {
			Logging.logMessage("WarningBell: checking destroyed fence/gate network from "
					+ tileX + "," + tileY + " before emergency wake-up");
			collectFenceNetworkBells(level, tileX, tileY, bells);
		}
		else if (object instanceof DoorObject) {
			Logging.logMessage("WarningBell: checking destroyed door at "
					+ tileX + "," + tileY + " for adjacent warning bell before emergency wake-up");
			collectDoorSideBells(level, tileX, tileY, bells);
		}

		if (bells.isEmpty()) {
			Logging.logMessage("WarningBell: destroyed barrier at " + tileX + "," + tileY
					+ " has no associated warning bell, emergency wake-up cancelled");
			return;
		}

		long now = level.getTime();
		LevelState state = getState(level);
		synchronized (state) {
			pruneFinishedAlerts(level, state, now);
			Point alertTile = new Point(tileX, tileY);
			AlertState alert = state.alerts.get(alertTile);
			if (alert == null) {
				alert = new AlertState(now + alertLifetimeMs, getSettlementUniqueID(level, tileX, tileY));
				state.alerts.put(alertTile, alert);
			}

			alert.expiresAt = now + alertLifetimeMs;
			alert.safeSince = -1L;
			alert.emergency = true;
			alert.attackers.add(zombie.getUniqueID());
			Logging.logMessage("WarningBell: zombie destroyed " + object.getStringID() + " at "
					+ tileX + "," + tileY + ", escalating to all available guards");
			rebalanceGuards(level, state, now);
		}
	}

	public static Mob getGuardTargetMob(GuardHumanMob guard) {
		if (guard == null || guard.getLevel() == null || !guard.getLevel().isServer()) {
			return null;
		}

		Level level = guard.getLevel();
		LevelState state = getState(level);
		synchronized (state) {
			long now = level.getTime();
			if (pruneFinishedAlerts(level, state, now)) {
				rebalanceGuards(level, state, now);
			}

			GuardAssignment assignment = state.guardAssignments.get(guard.getUniqueID());
			if (assignment == null || !state.alerts.containsKey(assignment.alertTile)) {
				return null;
			}

			Mob target = GameUtils.getLevelMob(assignment.attackerUniqueID, level);
			if (target == null || target.removed() || target.getHealth() <= 0) {
				state.guardAssignments.remove(guard.getUniqueID());
				rebalanceGuards(level, state, now);
				assignment = state.guardAssignments.get(guard.getUniqueID());
				if (assignment == null) {
					return null;
				}
				target = GameUtils.getLevelMob(assignment.attackerUniqueID, level);
			}

			return target != null && !target.removed() && target.getHealth() > 0 ? target : null;
		}
	}

	private static LevelState getState(Level level) {
		synchronized (states) {
			return states.computeIfAbsent(level, ignored -> new LevelState());
		}
	}

	private static boolean pruneFinishedAlerts(Level level, LevelState state, long now) {
		boolean stateChanged = false;
		boolean removedAny = false;
		List<Point> remove = new ArrayList<>();

		for (Map.Entry<Point, AlertState> entry : state.alerts.entrySet()) {
			AlertState alert = entry.getValue();
			int attackersBefore = alert.attackers.size();
			alert.attackers.removeIf(uniqueID -> {
				Mob attacker = GameUtils.getLevelMob(uniqueID, level);
				return attacker == null || attacker.removed() || attacker.getHealth() <= 0;
			});
			if (alert.attackers.size() != attackersBefore) {
				stateChanged = true;
			}

			if (!alert.attackers.isEmpty()) {
				alert.safeSince = -1L;
			}
			else if (alert.safeSince < 0L) {
				alert.safeSince = now;
				Logging.logMessage("WarningBell: alert at " + entry.getKey().x + "," + entry.getKey().y
						+ " has no living attackers, starting release grace");
			}

			boolean graceElapsed = alert.safeSince >= 0L && now - alert.safeSince >= safeReleaseGraceMs;
			if (graceElapsed || now >= alert.expiresAt) {
				remove.add(entry.getKey());
			}
		}

		for (Point alertTile : remove) {
			state.alerts.remove(alertTile);
			Logging.logMessage("WarningBell: releasing alert at " + alertTile.x + "," + alertTile.y);
			removedAny = true;
		}

		if (removedAny) {
			state.guardAssignments.entrySet().removeIf(entry -> !state.alerts.containsKey(entry.getValue().alertTile));
		}
		return stateChanged || removedAny;
	}

	private static void rebalanceGuards(Level level, LevelState state, long now) {
		state.lastRebalance = now;
		if (state.alerts.isEmpty()) {
			state.guardAssignments.clear();
			return;
		}

		List<GuardHumanMob> guards = new ArrayList<>();
		level.entityManager.mobs.stream()
				.filter(mob -> mob instanceof GuardHumanMob)
				.map(mob -> (GuardHumanMob)mob)
				.filter(guard -> guard.isSettlerOnCurrentLevel() && !guard.hasCommandOrders())
				.forEach(guard -> {
					int settlementUniqueID = guard.getSettlementUniqueID();
					boolean hasAlert = state.alerts.values().stream()
							.anyMatch(alert -> alert.settlementUniqueID == settlementUniqueID);
					if (!hasAlert) {
						return;
					}

					boolean emergency = state.alerts.values().stream()
							.anyMatch(alert -> alert.settlementUniqueID == settlementUniqueID && alert.emergency);
					if (emergency) {
						if (!GuardFatigueSystem.canRespondToEmergency(guard)) {
							return;
						}
						GuardFatigueSystem.markRestCombat(guard, "breach emergency");
					}
					else if (GuardFatigueSystem.isScheduledRestPeriod(guard)) {
						return;
					}

					guards.add(guard);
				});
		guards.sort(Comparator.comparingInt(Mob::getUniqueID));

		List<GuardHumanMob> nonBreakGuards = new ArrayList<>();
		List<GuardHumanMob> breakGuards = new ArrayList<>();
		for (GuardHumanMob guard : guards) {
			boolean emergencyForGuard = state.alerts.values().stream().anyMatch(
					alert -> alert.settlementUniqueID == guard.getSettlementUniqueID() && alert.emergency
			);
			if (GuardNeedsSystem.isOnBreak(guard)) {
				if (emergencyForGuard) {
					GuardNeedsSystem.interruptBreak(guard);
					nonBreakGuards.add(guard);
				}
				else {
					breakGuards.add(guard);
				}
			}
			else {
				nonBreakGuards.add(guard);
			}
		}

		boolean allNonBreakGuardsInCombat = nonBreakGuards.isEmpty();
		if (!nonBreakGuards.isEmpty()) {
			allNonBreakGuardsInCombat = true;
			for (GuardHumanMob guard : nonBreakGuards) {
				if (!GuardNeedsSystem.isInCombat(guard)) {
					allNonBreakGuardsInCombat = false;
					break;
				}
			}
		}

		List<GuardHumanMob> respondingGuards = new ArrayList<>(nonBreakGuards);
		if (allNonBreakGuardsInCombat && !breakGuards.isEmpty()) {
			breakGuards.sort((a, b) -> {
				int aPriority = GuardNeedsSystem.getBreakType(a) == GuardNeedsSystem.BreakType.RECREATION ? 0 : 1;
				int bPriority = GuardNeedsSystem.getBreakType(b) == GuardNeedsSystem.BreakType.RECREATION ? 0 : 1;
				int priorityCompare = Integer.compare(aPriority, bPriority);
				return priorityCompare != 0 ? priorityCompare : Integer.compare(a.getUniqueID(), b.getUniqueID());
			});

			GuardHumanMob interruptedGuard = breakGuards.get(0);
			GuardNeedsSystem.interruptBreak(interruptedGuard);
			respondingGuards.add(interruptedGuard);
			Logging.logMessage("WarningBell: interrupted guard " + interruptedGuard.getUniqueID()
					+ " break because all other available guards are already in combat");
		}

		List<ThreatCandidate> threats = new ArrayList<>();
		for (Map.Entry<Point, AlertState> entry : state.alerts.entrySet()) {
			for (int attackerUniqueID : entry.getValue().attackers) {
				Mob attacker = GameUtils.getLevelMob(attackerUniqueID, level);
				if (attacker != null && !attacker.removed() && attacker.getHealth() > 0) {
					threats.add(new ThreatCandidate(entry.getKey(), entry.getValue().settlementUniqueID, attacker));
				}
			}
		}

		if (threats.isEmpty() || respondingGuards.isEmpty()) {
			state.guardAssignments.clear();
			return;
		}

		Map<Integer, Integer> attackerLoad = new HashMap<>();
		Map<Point, Integer> alertLoad = new HashMap<>();
		for (ThreatCandidate threat : threats) {
			attackerLoad.put(threat.attacker.getUniqueID(), 0);
			alertLoad.putIfAbsent(threat.alertTile, 0);
		}

		Map<Integer, GuardAssignment> assignments = new HashMap<>();
		for (GuardHumanMob guard : respondingGuards) {
			List<ThreatCandidate> candidates = new ArrayList<>();
			for (ThreatCandidate threat : threats) {
				if (threat.settlementUniqueID == guard.getSettlementUniqueID()) {
					candidates.add(threat);
				}
			}
			if (candidates.isEmpty()) {
				continue;
			}
			candidates.sort((a, b) -> {
				int alertCompare = Integer.compare(alertLoad.get(a.alertTile), alertLoad.get(b.alertTile));
				if (alertCompare != 0) return alertCompare;

				int attackerCompare = Integer.compare(
						attackerLoad.get(a.attacker.getUniqueID()),
						attackerLoad.get(b.attacker.getUniqueID()));
				if (attackerCompare != 0) return attackerCompare;

				long distA = distanceSquared(guard.getTileX(), guard.getTileY(), a.attacker.getTileX(), a.attacker.getTileY());
				long distB = distanceSquared(guard.getTileX(), guard.getTileY(), b.attacker.getTileX(), b.attacker.getTileY());
				return Long.compare(distA, distB);
			});

			ThreatCandidate chosen = candidates.get(0);
			assignments.put(guard.getUniqueID(), new GuardAssignment(chosen.alertTile, chosen.attacker.getUniqueID()));
			alertLoad.put(chosen.alertTile, alertLoad.get(chosen.alertTile) + 1);
			attackerLoad.put(chosen.attacker.getUniqueID(), attackerLoad.get(chosen.attacker.getUniqueID()) + 1);
			Logging.logMessage("WarningBell: assigned guard " + guard.getUniqueID()
					+ " to attacker " + chosen.attacker.getStringID() + "#" + chosen.attacker.getUniqueID()
					+ " at " + chosen.attacker.getTileX() + "," + chosen.attacker.getTileY()
					+ " for alert " + chosen.alertTile.x + "," + chosen.alertTile.y);
		}

		state.guardAssignments.clear();
		state.guardAssignments.putAll(assignments);

		for (GuardHumanMob guard : respondingGuards) {
			GuardAssignment assignment = assignments.get(guard.getUniqueID());
			if (assignment == null || guard.ai == null) continue;

			Mob attacker = GameUtils.getLevelMob(assignment.attackerUniqueID, level);
			if (attacker == null || attacker.removed() || attacker.getHealth() <= 0) continue;

			guard.ai.blackboard.put("currentTarget", attacker);
			guard.ai.blackboard.submitEvent("resetPathTime", new AIEvent());
			Logging.logMessage("WarningBell: forced guard " + guard.getUniqueID()
					+ " currentTarget to breach attacker " + attacker.getStringID() + "#" + attacker.getUniqueID());
		}
	}

	private static int getSettlementUniqueID(Level level, int tileX, int tileY) {
		if (level == null || !level.isServer() || level.getServer() == null) {
			return 0;
		}

		return SettlementsWorldData.getSettlementsData(level.getServer())
				.getSettlementUniqueIDAtTile(level.getIdentifier(), tileX, tileY);
	}

	private static long distanceSquared(int x1, int y1, int x2, int y2) {
		long dx = x1 - x2;
		long dy = y1 - y2;
		return dx * dx + dy * dy;
	}

	private static void collectDoorSideBells(Level level, int doorX, int doorY, Set<Point> out) {
		for (Point offset : Level.adjacentGettersNotDiagonal) {
			int wallX = doorX + offset.x;
			int wallY = doorY + offset.y;
			GameObject object = level.getObject(wallX, wallY);
			Logging.logMessage("WarningBell: door neighbor " + wallX + "," + wallY + " is "
					+ object.getStringID() + " wall=" + object.isWall + " door=" + object.isDoor);
			if (object.isWall && !object.isDoor) {
				collectBellsAdjacentToSupport(level, wallX, wallY, out);
			}
		}
	}

	private static void collectFenceNetworkBells(Level level, int startX, int startY, Set<Point> out) {
		Point start = new Point(startX, startY);
		ArrayDeque<FenceNode> queue = new ArrayDeque<>();
		Set<Point> visited = new HashSet<>();
		queue.add(new FenceNode(start, 0));
		visited.add(start);

		while (!queue.isEmpty()) {
			FenceNode node = queue.removeFirst();
			collectBellsAdjacentToSupport(level, node.tile.x, node.tile.y, out);
			if (node.distance >= fenceAlertRange) continue;

			for (Point offset : Level.adjacentGettersNotDiagonal) {
				Point next = new Point(node.tile.x + offset.x, node.tile.y + offset.y);
				if (visited.contains(next)) continue;

				GameObject object = level.getObject(next.x, next.y);
				if (object.isFence) {
					visited.add(next);
					queue.addLast(new FenceNode(next, node.distance + 1));
				}
			}
		}
		Logging.logMessage("WarningBell: fence search visited " + visited.size() + " connected fence/gate tile(s)");
	}

	private static void collectBellsAdjacentToSupport(Level level, int supportX, int supportY, Set<Point> out) {
		for (Point offset : Level.adjacentGettersNotDiagonal) {
			int bellX = supportX + offset.x;
			int bellY = supportY + offset.y;
			checkBellAt(level, bellX, bellY, supportX, supportY, out);
		}
	}

	private static void checkBellAt(Level level, int bellX, int bellY, int supportX, int supportY, Set<Point> out) {
		for (int layerID : ObjectLayerRegistry.getLayerIDs()) {
			GameObject object = level.getObject(layerID, bellX, bellY);
			if (!WarningBellObject.stringID.equals(object.getStringID())) continue;

			int rotation = level.getObjectRotation(layerID, bellX, bellY);
			Point attached = WarningBellObject.getAttachedTile(bellX, bellY, rotation);
			if (attached.x != supportX || attached.y != supportY) continue;

			Logging.logMessage("WarningBell: found bell at " + bellX + "," + bellY
					+ " on layer " + layerID + " (" + ObjectLayerRegistry.getLayerStringID(layerID) + ")"
					+ " for support " + supportX + "," + supportY);
			out.add(new Point(bellX, bellY));
			return;
		}
	}

	private static final class LevelState {
		private final Map<Point, Long> lastBellRing = new HashMap<>();
		private final Map<Point, AlertState> alerts = new HashMap<>();
		private final Map<Integer, GuardAssignment> guardAssignments = new HashMap<>();
		private long lastRebalance;
	}

	private static final class AlertState {
		private long expiresAt;
		private long safeSince = -1L;
		private boolean emergency;
		private final int settlementUniqueID;
		private final Set<Integer> attackers = new HashSet<>();

		private AlertState(long expiresAt, int settlementUniqueID) {
			this.expiresAt = expiresAt;
			this.settlementUniqueID = settlementUniqueID;
		}
	}

	private static final class GuardAssignment {
		private final Point alertTile;
		private final int attackerUniqueID;

		private GuardAssignment(Point alertTile, int attackerUniqueID) {
			this.alertTile = new Point(alertTile);
			this.attackerUniqueID = attackerUniqueID;
		}
	}

	private static final class ThreatCandidate {
		private final Point alertTile;
		private final int settlementUniqueID;
		private final Mob attacker;

		private ThreatCandidate(Point alertTile, int settlementUniqueID, Mob attacker) {
			this.alertTile = new Point(alertTile);
			this.settlementUniqueID = settlementUniqueID;
			this.attacker = attacker;
		}
	}

	private static final class FenceNode {
		private final Point tile;
		private final int distance;

		private FenceNode(Point tile, int distance) {
			this.tile = tile;
			this.distance = distance;
		}
	}
}
