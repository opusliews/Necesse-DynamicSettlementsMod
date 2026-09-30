package opusliews.raids;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.engine.network.packet.PacketChangeObject;
import necesse.engine.network.packet.PacketPlayObjectDamageSound;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.registries.TileRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.util.GameRandom;
import necesse.engine.util.LevelIdentifier;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.entity.levelEvent.LevelEvent;
import necesse.entity.levelEvent.settlementRaidEvent.SettlementRaidLevelEvent;
import necesse.entity.mobs.BasicPathDoorOption;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PathDoorOption;
import necesse.entity.mobs.RaiderMobPhase;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.leaves.MoveToAINode;
import necesse.entity.mobs.ai.path.SubRegionPathResult;
import necesse.entity.mobs.friendly.human.MoveToTile;
import necesse.entity.mobs.hostile.ItemAttackerRaiderMob;
import necesse.entity.mobs.itemAttacker.ItemAttackSlot;
import necesse.entity.objectEntity.LadderDownObjectEntity;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.entity.objectEntity.interfaces.OEInventory;
import necesse.inventory.Inventory;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.ItemAttackerWeaponItem;
import necesse.inventory.item.toolItem.ToolType;
import necesse.level.gameObject.DoorObject;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.regionSystem.RegionType;
import necesse.level.maps.regionSystem.SubRegion;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementLevelType;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;
import opusliews.object.HoleCaveLadderObject;
import opusliews.object.HoleCaveLadderUpObject;
import opusliews.tile.DeepHoleTile;

public final class MultiLevelRaidSystem {
	private static final double minLevelShare = 0.10;
	private static final int minimumBreachSpacing = 5;
	private static final int fastBreakCooldownMs = 1000;
	private static final long goalHoldDurationMs = 30000L;
	private static final Map<RaidKey, RaidState> raidStates = new HashMap<>();
	private static final Map<ItemAttackerRaiderMob, RaiderState> raiderStates = new WeakHashMap<>();
	private static final Map<ItemAttackerRaiderMob, Long> escapeRetaliationUntil = new WeakHashMap<>();
	private static final Set<BreachKey> protectedBreaches = new HashSet<>();

	private MultiLevelRaidSystem() {
	}

	public static synchronized void onRaidStarted(SettlementRaidLevelEvent event, LinkedList<ItemAttackerRaiderMob> raiders) {
		if (event == null || event.getLevel() == null || !event.getLevel().isServer() || raiders == null || raiders.isEmpty()) return;
		RaidState existing = getRaidState(event);
		if (existing != null && existing.splitInitialized) return;

		ServerSettlementData settlement = event.getServerSettlementData();
		if (settlement == null) return;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null) return;
		Level surface = event.getLevel();
		Level cave = getLevel(settlement, domain.getLevelIdentifier(SettlementLevelType.CAVE));

		WealthData surfaceWealth = scanStoredWealth(surface, domain.getTileBounds(SettlementLevelType.SURFACE));
		WealthData caveWealth = cave == null ? new WealthData() : scanStoredWealth(cave, domain.getTileBounds(SettlementLevelType.CAVE));
		double totalWealth = surfaceWealth.total + caveWealth.total;
		double caveShare = totalWealth <= 0.0 ? 0.0 : caveWealth.total / totalWealth;
		if (caveShare < minLevelShare) caveShare = 0.0;
		if (1.0 - caveShare < minLevelShare) caveShare = 1.0;

		RaidState state = existing == null ? new RaidState(settlement.uniqueID, event.getUniqueID()) : existing;
		state.splitInitialized = true;
		state.caveShare = caveShare;
		for (ItemAttackerRaiderMob raider : raiders) {
			state.remainingRaiderIDs.add(raider.getUniqueID());
			RaiderState rs = new RaiderState(settlement.uniqueID, event.getUniqueID());
			rs.surfaceTarget = chooseWeightedTarget(surfaceWealth.tiles);
			if (rs.surfaceTarget == null && raider.attackTile != null) rs.surfaceTarget = new Point(raider.attackTile);
			rs.stage = RaiderStage.SURFACE_APPROACH;
			raiderStates.put(raider, rs);
			if (rs.surfaceTarget != null) {
				raider.setAttackTile(rs.surfaceTarget);
				if (Logging.logEnabled) Logging.logMessage("[CaveRaid] Assigned surface raider " + raider.getUniqueID() + " target=" + rs.surfaceTarget.x + "," + rs.surfaceTarget.y);
			}
		}
		raidStates.put(new RaidKey(state.settlementUniqueID, state.raidEventUniqueID), state);

		List<ItemAttackerRaiderMob> caveRaiders = cave == null || caveShare <= 0.0 ? new ArrayList<>() : chooseCaveRaiders(raiders, caveShare);
		ArrayList<Point> usedBreachPoints = new ArrayList<>();
		int assignedCaveRaiders = 0;
		for (ItemAttackerRaiderMob raider : caveRaiders) {
			if (assignCaveRaider(event, settlement, domain, surface, caveWealth, raider, usedBreachPoints)) assignedCaveRaiders++;
		}

		if (Logging.logEnabled) Logging.logMessage("[CaveRaid] Custom raider AI initialized settlement=" + settlement.uniqueID + " surfaceWealth=" + Math.round(surfaceWealth.total) + " caveWealth=" + Math.round(caveWealth.total) + " caveShare=" + String.format("%.2f", caveShare) + " caveRaiders=" + assignedCaveRaiders + "/" + raiders.size());
	}

	public static synchronized void onRaiderAdded(SettlementRaidLevelEvent event, ItemAttackerRaiderMob raider, LinkedList<ItemAttackerRaiderMob> raiders) {
		if (event == null || raider == null || raiders == null) return;
		if (raider.isServer()) raider.ai = DynamicRaiderAI.create(raider);
		RaidState state = getRaidState(event);
		if (state == null || !state.splitInitialized || state.eventEnded || state.remainingRaiderIDs.contains(raider.getUniqueID())) return;
		ServerSettlementData settlement = event.getServerSettlementData();
		if (settlement == null) return;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null) return;

		state.remainingRaiderIDs.add(raider.getUniqueID());
		RaiderState newState = new RaiderState(settlement.uniqueID, event.getUniqueID());
		WealthData surfaceWealth = scanStoredWealth(event.getLevel(), domain.getTileBounds(SettlementLevelType.SURFACE));
		newState.surfaceTarget = chooseWeightedTarget(surfaceWealth.tiles);
		if (newState.surfaceTarget == null && raider.attackTile != null) newState.surfaceTarget = new Point(raider.attackTile);
		newState.stage = RaiderStage.SURFACE_APPROACH;
		raiderStates.put(raider, newState);
		if (newState.surfaceTarget != null) {
			raider.setAttackTile(newState.surfaceTarget);
			if (Logging.logEnabled) Logging.logMessage("[CaveRaid] Assigned late surface raider " + raider.getUniqueID() + " target=" + newState.surfaceTarget.x + "," + newState.surfaceTarget.y);
		}
		if (state.caveShare <= 0.0) return;
		if (state.caveShare < 1.0 && !shouldAssignNewRaiderToCave(raiders, raider, state.caveShare)) return;

		Level surface = event.getLevel();
		Level cave = getLevel(settlement, domain.getLevelIdentifier(SettlementLevelType.CAVE));
		if (surface == null || cave == null) return;
		WealthData caveWealth = scanStoredWealth(cave, domain.getTileBounds(SettlementLevelType.CAVE));
		ArrayList<Point> used = getPlannedBreachPoints(settlement.uniqueID, event.getUniqueID(), raider);
		if (assignCaveRaider(event, settlement, domain, surface, caveWealth, raider, used) && Logging.logEnabled) {
			Logging.logMessage("[CaveRaid] Late-spawned raider " + raider.getUniqueID() + " assigned to cave force");
		}
	}

	private static boolean shouldAssignNewRaiderToCave(List<ItemAttackerRaiderMob> raiders, ItemAttackerRaiderMob newRaider, double caveShare) {
		ArrayList<ItemAttackerRaiderMob> cave = new ArrayList<>();
		for (ItemAttackerRaiderMob existing : raiders) {
			if (existing == newRaider) continue;
			RaiderState rs = raiderStates.get(existing);
			if (rs != null && rs.caveAssigned) cave.add(existing);
		}
		GroupMetrics total = metrics(raiders);
		double without = score(cave, total, caveShare);
		double with = scoreWith(cave, newRaider, total, caveShare);
		if (with >= without) return false;
		if (caveShare < 1.0) {
			int surfaceCount = 0;
			for (ItemAttackerRaiderMob existing : raiders) {
				if (existing == newRaider) continue;
				RaiderState rs = raiderStates.get(existing);
				if (rs == null || !rs.caveAssigned) surfaceCount++;
			}
			if (surfaceCount == 0) return false;
		}
		return true;
	}

	private static boolean assignCaveRaider(SettlementRaidLevelEvent event, ServerSettlementData settlement, SettlementLevelDomain domain, Level surface, WealthData caveWealth, ItemAttackerRaiderMob raider, List<Point> usedBreachPoints) {
		Point target = chooseWeightedTarget(caveWealth.tiles);
		if (target == null) return false;
		Point breach = chooseBreachPoint(surface, domain.getTileBounds(SettlementLevelType.SURFACE), raider, target, usedBreachPoints);
		if (breach == null) return false;

		RaiderState rs = raiderStates.get(raider);
		if (rs == null) rs = new RaiderState(settlement.uniqueID, event.getUniqueID());
		rs.caveAssigned = true;
		rs.caveTarget = target;
		rs.breachTile = breach;
		rs.stage = RaiderStage.APPROACH_BREACH;
		raiderStates.put(raider, rs);
		raider.setAttackTile(breach);
		if (Logging.logEnabled) Logging.logMessage("[CaveRaid] Assigned cave raider " + raider.getUniqueID() + " breach=" + breach.x + "," + breach.y + " caveTarget=" + target.x + "," + target.y + " combatValue=" + Math.round(combatValue(raider)));
		return true;
	}

	private static ArrayList<Point> getPlannedBreachPoints(int settlementUniqueID, int raidEventUniqueID, ItemAttackerRaiderMob exclude) {
		ArrayList<Point> points = new ArrayList<>();
		for (Map.Entry<ItemAttackerRaiderMob, RaiderState> entry : raiderStates.entrySet()) {
			if (entry.getKey() == exclude) continue;
			RaiderState rs = entry.getValue();
			if (rs != null && rs.caveAssigned && rs.settlementUniqueID == settlementUniqueID && rs.raidEventUniqueID == raidEventUniqueID && rs.breachTile != null) points.add(new Point(rs.breachTile));
		}
		return points;
	}

	private static List<ItemAttackerRaiderMob> chooseCaveRaiders(List<ItemAttackerRaiderMob> raiders, double caveShare) {
		if (caveShare >= 1.0) return new ArrayList<>(raiders);
		if (raiders.size() < 2) return new ArrayList<>();

		ArrayList<ItemAttackerRaiderMob> sorted = new ArrayList<>(raiders);
		sorted.sort(Comparator.comparingDouble(MultiLevelRaidSystem::combatValue).reversed());
		GroupMetrics total = metrics(sorted);
		ArrayList<ItemAttackerRaiderMob> cave = new ArrayList<>();
		ArrayList<ItemAttackerRaiderMob> surface = new ArrayList<>();
		for (ItemAttackerRaiderMob raider : sorted) {
			double caveScore = scoreWith(cave, raider, total, caveShare);
			double surfaceScore = score(cave, total, caveShare);
			if (caveScore < surfaceScore) cave.add(raider); else surface.add(raider);
		}
		if (cave.isEmpty()) cave.add(surface.remove(surface.size() - 1));
		if (surface.isEmpty()) surface.add(cave.remove(cave.size() - 1));

		boolean improved = true;
		while (improved) {
			improved = false;
			double current = score(cave, total, caveShare);
			outer: for (ItemAttackerRaiderMob c : new ArrayList<>(cave)) {
				for (ItemAttackerRaiderMob s : new ArrayList<>(surface)) {
					ArrayList<ItemAttackerRaiderMob> test = new ArrayList<>(cave);
					test.remove(c);
					test.add(s);
					double candidate = score(test, total, caveShare);
					if (candidate + 0.0001 < current) {
						cave.remove(c); cave.add(s); surface.remove(s); surface.add(c); improved = true; break outer;
					}
				}
			}
		}
		return cave;
	}

	private static double scoreWith(List<ItemAttackerRaiderMob> cave, ItemAttackerRaiderMob add, GroupMetrics total, double share) {
		ArrayList<ItemAttackerRaiderMob> test = new ArrayList<>(cave);
		test.add(add);
		return score(test, total, share);
	}

	private static double score(List<ItemAttackerRaiderMob> cave, GroupMetrics total, double share) {
		GroupMetrics g = metrics(cave);
		double valueError = Math.abs(g.value - total.value * share) / Math.max(1.0, total.value);
		double meleeError = Math.abs(g.melee - total.melee * share) / Math.max(1.0, total.melee);
		double rangedError = Math.abs(g.ranged - total.ranged * share) / Math.max(1.0, total.ranged);
		double specialError = Math.abs(g.special - total.special * share) / Math.max(1.0, total.special);
		double countError = Math.abs(g.count - total.count * share) / Math.max(1.0, total.count);
		return valueError + 0.15 * meleeError + 0.15 * rangedError + 0.10 * specialError + 0.05 * countError;
	}

	private static GroupMetrics metrics(List<ItemAttackerRaiderMob> raiders) {
		GroupMetrics out = new GroupMetrics();
		for (ItemAttackerRaiderMob r : raiders) {
			out.value += combatValue(r);
			out.count++;
			Role role = role(r);
			if (role == Role.MELEE) out.melee++;
			else if (role == Role.RANGED) out.ranged++;
			else out.special++;
		}
		return out;
	}

	private static double combatValue(ItemAttackerRaiderMob raider) {
		return Math.max(1.0, raider.weaponValue + raider.armorValue);
	}

	private static Role role(ItemAttackerRaiderMob raider) {
		if (raider.weapon == null || !(raider.weapon.item instanceof ItemAttackerWeaponItem)) return Role.SPECIAL;
		int range = ((ItemAttackerWeaponItem)raider.weapon.item).getItemAttackerAttackRange(raider, raider.weapon);
		return range > 96 ? Role.RANGED : Role.MELEE;
	}

	public static Point adjustPreparingSpawn(SettlementRaidLevelEvent event, Point vanillaTile) {
		if (event == null || vanillaTile == null || event.getLevel() == null) return vanillaTile;
		ServerSettlementData settlement = event.getServerSettlementData();
		if (settlement == null || settlement.networkData == null) return vanillaTile;
		Rectangle rect = settlement.networkData.getLoadedTileRectangle();
		if (rect == null || rect.width <= 0 || rect.height <= 0) return vanillaTile;

		double centerX = rect.getCenterX();
		double centerY = rect.getCenterY();
		double nx = (vanillaTile.x - centerX) / Math.max(1.0, rect.width / 2.0);
		double ny = (vanillaTile.y - centerY) / Math.max(1.0, rect.height / 2.0);
		int dx = 0;
		int dy = 0;
		if (vanillaTile.x <= rect.x + 5) dx = -1;
		else if (vanillaTile.x >= rect.x + rect.width - 6) dx = 1;
		if (vanillaTile.y <= rect.y + 5) dy = -1;
		else if (vanillaTile.y >= rect.y + rect.height - 6) dy = 1;
		if (dx == 0 && dy == 0) {
			if (Math.abs(nx) >= Math.abs(ny)) dx = nx < 0.0 ? -1 : 1;
			else dy = ny < 0.0 ? -1 : 1;
		}

		Level level = event.getLevel();
		Point base = new Point(vanillaTile.x + dx * 8, vanillaTile.y + dy * 8);
		Point best = findOpenSpawnNear(level, base, dx, dy);
		return best == null ? vanillaTile : best;
	}

	private static Point findOpenSpawnNear(Level level, Point base, int dx, int dy) {
		for (int radius = 0; radius <= 4; radius++) {
			for (int ox = -radius; ox <= radius; ox++) {
				for (int oy = -radius; oy <= radius; oy++) {
					if (radius > 0 && Math.abs(ox) != radius && Math.abs(oy) != radius) continue;
					int x = base.x + (dy != 0 && dx == 0 ? ox : (dx != 0 && dy != 0 ? ox : 0));
					int y = base.y + (dx != 0 && dy == 0 ? oy : (dx != 0 && dy != 0 ? oy : 0));
					if (!level.isTileWithinBounds(x, y, 2)) continue;
					if (level.isSolidTile(x, y) || level.getTile(x, y).isLiquid) continue;
					return new Point(x, y);
				}
			}
		}
		return null;
	}

	public static synchronized void prepareCaveMoveTick(MoveToAINode node, Mob mob, Blackboard blackboard) {
		if (!(mob instanceof ItemAttackerRaiderMob) || node == null || blackboard == null || blackboard.mover == null) return;
		ItemAttackerRaiderMob raider = (ItemAttackerRaiderMob)mob;
		RaiderState state = raiderStates.get(raider);
		if (state == null || !state.caveAssigned || !isCave(raider, state)) return;

		boolean caveAttackMove = state.stage == RaiderStage.CAVE_ATTACK && state.caveTarget != null && raider.attackTile != null;
		boolean caveEscapeMove = state.stage == RaiderStage.CAVE_ESCAPE && state.breachTile != null;
		if (!caveAttackMove && !caveEscapeMove) return;
		if (node.hasTask() || blackboard.mover.isMoving()) return;

		// Vanilla MoveToAINode can keep a long pathfinding cooldown after a partial cave path has already ended.
		// Expire that stale cooldown so cave raiders immediately continue/re-path instead of standing idle for seconds.
		if (node.pathFindingCooldown >= raider.getTime()) node.pathFindingCooldown = 0L;
	}

	private static void setAttackTileIfChanged(ItemAttackerRaiderMob raider, Point target) {
		if (target == null) {
			if (raider.attackTile != null) raider.setAttackTile(null);
		} else if (raider.attackTile == null || !raider.attackTile.equals(target)) {
			raider.setAttackTile(target);
		}
	}

	public static synchronized MoveToTile getStrategicMoveToTile(ItemAttackerRaiderMob raider) {
		RaiderState state = raiderStates.get(raider);
		if (state == null || raider.phase != RaiderMobPhase.RAIDING) return null;

		if (!state.caveAssigned && state.stage == RaiderStage.SURFACE_APPROACH && state.surfaceTarget != null) {
			setAttackTileIfChanged(raider, state.surfaceTarget);
			return new MoveToTile(state.surfaceTarget.x, state.surfaceTarget.y, true) {
				@Override
				public boolean moveIfPathFailed(float tileDistance) { return true; }
				@Override
				public boolean isAtLocation(float tileDistance, boolean foundPath) { return tileDistance <= 5.0F; }
				@Override
				public void onArrivedAtLocation() { reachRaidGoal(raider, state, state.surfaceTarget); }
			};
		}

		if (state.caveAssigned && state.stage == RaiderStage.APPROACH_BREACH && isSurface(raider, state) && state.breachTile != null) {
			setAttackTileIfChanged(raider, state.breachTile);
			return new MoveToTile(state.breachTile.x, state.breachTile.y, true) {
				@Override
				public boolean moveIfPathFailed(float tileDistance) { return true; }
				@Override
				public boolean isAtLocation(float tileDistance, boolean foundPath) { return tileDistance <= 1.5F; }
				@Override
				public void onArrivedAtLocation() {
					if (!createRaidBreach(raider, state)) return;
					state.stage = RaiderStage.BREACH_READY;
					if (transitionRaider(raider, state, SettlementLevelType.CAVE)) {
						state.stage = RaiderStage.CAVE_ATTACK;
						raider.setAttackTile(state.caveTarget);
						if (Logging.logEnabled) Logging.logMessage("[CaveRaid] Raider " + raider.getUniqueID() + " descended raid breach at " + state.breachTile.x + "," + state.breachTile.y + " caveTarget=" + state.caveTarget.x + "," + state.caveTarget.y);
					}
				}
			};
		}

		if (state.caveAssigned && state.stage == RaiderStage.BREACH_READY && isSurface(raider, state) && state.breachTile != null) {
			setAttackTileIfChanged(raider, state.breachTile);
			return new MoveToTile(state.breachTile.x, state.breachTile.y, true) {
				@Override
				public boolean moveIfPathFailed(float tileDistance) { return true; }
				@Override
				public boolean isAtLocation(float tileDistance, boolean foundPath) { return tileDistance <= 1.5F; }
				@Override
				public void onArrivedAtLocation() {
					if (transitionRaider(raider, state, SettlementLevelType.CAVE)) {
						state.stage = RaiderStage.CAVE_ATTACK;
						raider.setAttackTile(state.caveTarget);
					}
				}
			};
		}

		if (state.caveAssigned && state.stage == RaiderStage.CAVE_ATTACK && isCave(raider, state) && state.caveTarget != null) {
			setAttackTileIfChanged(raider, state.caveTarget);
			return new MoveToTile(state.caveTarget.x, state.caveTarget.y, true) {
				@Override
				public boolean moveIfPathFailed(float tileDistance) { return false; }
				@Override
				public boolean isAtLocation(float tileDistance, boolean foundPath) { return tileDistance <= 5.0F; }
				@Override
				public void onArrivedAtLocation() { reachRaidGoal(raider, state, state.caveTarget); }
			};
		}
		return null;
	}

	public static synchronized MoveToTile getCaveEscapeMoveToTile(ItemAttackerRaiderMob raider) {
		RaiderState state = raiderStates.get(raider);
		if (state == null || !state.caveAssigned || state.stage != RaiderStage.CAVE_ESCAPE || !isCave(raider, state) || state.breachTile == null) return null;

		Point breach = state.breachTile;
		return new MoveToTile(breach.x, breach.y, true) {
			@Override
			public boolean moveIfPathFailed(float tileDistance) { return false; }

			@Override
			public boolean isAtLocation(float tileDistance, boolean foundPath) { return tileDistance <= 1.5F; }

			@Override
			public void onArrivedAtLocation() {
				if (transitionRaider(raider, state, SettlementLevelType.SURFACE)) {
					state.stage = RaiderStage.SURFACE_ESCAPE;
					if (raider.ai != null && raider.ai.blackboard != null) raider.ai.blackboard.mover.stopMoving(raider);
					if (Logging.logEnabled) Logging.logMessage("[CaveRaid] Raider " + raider.getUniqueID() + " returned through raid breach at " + breach.x + "," + breach.y + " and is escaping on surface");
				}
			}
		};
	}

	private static void reachRaidGoal(ItemAttackerRaiderMob raider, RaiderState state, Point goal) {
		state.stage = RaiderStage.WAITING_RETREAT;
		raider.setAttackTile(null);
		if (raider.ai != null && raider.ai.blackboard != null) raider.ai.blackboard.mover.stopMoving(raider);
		clearInvalidCombatTargets(raider);
		tryLootAtGoal(raider);
		if (Logging.logEnabled) Logging.logMessage("[CaveRaid] Raider " + raider.getUniqueID() + " reached raid goal at " + goal.x + "," + goal.y + " level=" + raider.getLevel().getIdentifier() + " and is waiting to retreat");
	}

	public static synchronized Point getWaitingBaseTile(ItemAttackerRaiderMob raider) {
		RaiderState state = raiderStates.get(raider);
		if (state == null || state.stage != RaiderStage.WAITING_RETREAT) return null;
		return state.caveAssigned ? state.caveTarget : state.surfaceTarget;
	}

	public static synchronized boolean shouldUseStrategicCombat(ItemAttackerRaiderMob raider) {
		RaiderState state = raiderStates.get(raider);
		return state != null && raider.phase == RaiderMobPhase.RAIDING && state.stage != RaiderStage.CAVE_ESCAPE && state.stage != RaiderStage.SURFACE_ESCAPE;
	}

	public static synchronized boolean shouldRetreat(ItemAttackerRaiderMob raider) {
		return raider != null && (raider.phase == RaiderMobPhase.LOOTING || raider.phase == RaiderMobPhase.ESCAPING || raider.phase == null);
	}

	public static synchronized void prepareRetreatState(ItemAttackerRaiderMob raider) {
		RaiderState state = raiderStates.get(raider);
		if (state == null || !shouldRetreat(raider)) return;

		RaiderStage nextStage = state.caveAssigned && isCave(raider, state) ? RaiderStage.CAVE_ESCAPE : RaiderStage.SURFACE_ESCAPE;
		if (state.stage != nextStage) {
			state.stage = nextStage;
			if (nextStage == RaiderStage.CAVE_ESCAPE && Logging.logEnabled && state.breachTile != null) {
				Logging.logMessage("[CaveRaid] Raider " + raider.getUniqueID() + " starting cave retreat toward breach " + state.breachTile.x + "," + state.breachTile.y);
			}
		}
		raider.setAttackTile(null);
	}

	public static synchronized void tryLootAtGoal(ItemAttackerRaiderMob raider) {
		RaiderState state = raiderStates.get(raider);
		if (state == null || state.stage != RaiderStage.WAITING_RETREAT || raider.carryingLoot != null || raider.getLevel() == null) return;
		long now = raider.getTime();
		if (now < state.nextLootAttemptTime) return;
		state.nextLootAttemptTime = now + 1000L;
		Point goal = state.caveAssigned ? state.caveTarget : state.surfaceTarget;
		if (goal == null || raider.getDistance(goal.x * 32 + 16, goal.y * 32 + 16) > 192.0F) return;
		LootCandidate candidate = findLootCandidate(raider, goal, 3);
		if (candidate == null) {
			if (Logging.logEnabled && now >= state.nextLootMissLogTime) {
				state.nextLootMissLogTime = now + 5000L;
				Logging.logMessage("[CaveRaid] Raider " + raider.getUniqueID() + " found no valid loot near goal " + goal.x + "," + goal.y + " level=" + raider.getLevel().getIdentifier());
			}
			return;
		}
		InventoryItem current = candidate.inventory.getItem(candidate.slot);
		if (current == null) return;
		InventoryItem picked = current.copy(Math.min(current.getAmount(), candidate.amount));
		candidate.inventory.setAmount(candidate.slot, current.getAmount() - picked.getAmount());
		necesse.entity.mobs.hostile.DynamicSettlementsRaiderAccess.setCarryingLoot(raider, picked);
		try {
			raider.showAttackAndSendAttacker(necesse.inventory.item.SwingSpriteAttackItem.setup(new InventoryItem("swingspriteattack"), picked.item, true), candidate.tileX * 32 + 16, candidate.tileY * 32 + 16, 0, Item.getRandomAttackSeed(GameRandom.globalRandom));
		} catch (Exception ignored) {
		}
		if (Logging.logEnabled) Logging.logMessage("[CaveRaid] Raider " + raider.getUniqueID() + " stole " + picked.getAmount() + "x " + picked.item.getStringID() + " at " + candidate.tileX + "," + candidate.tileY + " and remains at the raid goal");
	}

	private static LootCandidate findLootCandidate(ItemAttackerRaiderMob raider, Point goal, int radius) {
		LootCandidate best = null;
		float maxValue = Math.max(1.0F, (float)raider.getMaxHealth() / 5.0F);
		for (int x = goal.x - radius; x <= goal.x + radius; x++) {
			for (int y = goal.y - radius; y <= goal.y + radius; y++) {
				ObjectEntity entity = raider.getLevel().entityManager.getObjectEntity(x, y);
				if (!(entity instanceof OEInventory)) continue;
				Inventory inventory = ((OEInventory)entity).getInventory();
				if (inventory == null) continue;
				for (int slot = 0; slot < inventory.getSize(); slot++) {
					InventoryItem item = inventory.getItem(slot);
					if (item == null || !isValidRaidLoot(item)) continue;
					float single = item.item.getBrokerValue(item);
					if (single <= 0.0F || single > maxValue) continue;
					int amount = Math.max(1, Math.min(item.getAmount(), (int)(maxValue / single)));
					float value = single * amount;
					if (best == null || value > best.value) best = new LootCandidate(inventory, slot, x, y, amount, value);
				}
			}
		}
		return best;
	}

	private static boolean isValidRaidLoot(InventoryItem item) {
		if (item != null && item.item != null && item.item.getStringID().equals("coin")) return true;
		for (necesse.inventory.item.ItemCategory category = necesse.inventory.item.ItemCategory.getItemsCategory(item.item); category != null; category = category.parent) {
			String id = category.stringID;
			if (id.equals("materials") || id.equals("rawfood") || id.equals("commonfish") || id.equals("food") || id.equals("seeds") || id.equals("saplings")) return true;
		}
		return false;
	}

	private static boolean createRaidBreach(ItemAttackerRaiderMob raider, RaiderState state) {
		Level surface = raider.getLevel();
		if (surface == null || !surface.isServer()) return false;
		ServerSettlementData settlement = SettlementsWorldData.getSettlementsData(surface.getServer()).getServerData(state.settlementUniqueID);
		if (settlement == null) return false;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null) return false;
		Level cave = getLevel(settlement, domain.getLevelIdentifier(SettlementLevelType.CAVE));
		if (cave == null) return false;
		Point p = state.breachTile;
		if (surface.getObjectID(p.x, p.y) != 0 || surface.getTile(p.x, p.y).isLiquid) {
			ArrayList<Point> used = getPlannedBreachPoints(state.settlementUniqueID, state.raidEventUniqueID, raider);
			Point replacement = chooseBreachPoint(surface, domain.getTileBounds(SettlementLevelType.SURFACE), raider, state.caveTarget, used);
			if (replacement != null && !replacement.equals(p)) {
				state.breachTile = replacement;
				raider.setAttackTile(state.caveTarget);
				if (Logging.logEnabled) Logging.logMessage("[CaveRaid] Raider " + raider.getUniqueID() + " relocated blocked breach to " + replacement.x + "," + replacement.y);
			}
			return false;
		}

		try {
			raider.showAttackAndSendAttacker(new InventoryItem("ironpickaxe"), p.x * 32 + 16, p.y * 32 + 16, 0, Item.getRandomAttackSeed(GameRandom.globalRandom));
		} catch (Exception ignored) {
		}
		int rockID = ObjectRegistry.getObjectID("rock");
		if (rockID >= 0) surface.getServer().network.sendToClientsWithTile(new PacketPlayObjectDamageSound(p.x, p.y, rockID), surface, p.x, p.y);

		cave.regionManager.ensureTilesAreLoaded(p.x - 1, p.y - 1, p.x + 1, p.y + 1);
		LadderDownObjectEntity.clearAndPlaceLadder(surface.getServer(), cave, p.x, p.y, ObjectRegistry.getObjectID(HoleCaveLadderUpObject.stringID), true);
		surface.setTile(p.x, p.y, TileRegistry.getTileID(DeepHoleTile.stringID));
		surface.sendTileUpdatePacket(p.x, p.y);
		surface.setObject(p.x, p.y, ObjectRegistry.getObjectID(HoleCaveLadderObject.stringID));
		surface.replaceObjectEntity(p.x, p.y);
		surface.getServer().network.sendToClientsWithTile(new PacketChangeObject(surface, 0, p.x, p.y, ObjectRegistry.getObjectID(HoleCaveLadderObject.stringID)), surface, p.x, p.y);

		state.breachCreated = true;
		RaidState raid = findRaidState(state.raidEventUniqueID, state.settlementUniqueID);
		if (raid != null) raid.breaches.add(new Point(p));
		protectedBreaches.add(new BreachKey(state.settlementUniqueID, p.x, p.y));
		if (Logging.logEnabled) Logging.logMessage("[CaveRaid] Raider " + raider.getUniqueID() + " dug raid breach at " + p.x + "," + p.y);
		return true;
	}

	private static boolean transitionRaider(ItemAttackerRaiderMob raider, RaiderState state, SettlementLevelType destinationType) {
		Level current = raider.getLevel();
		if (current == null || current.getServer() == null || state.breachTile == null) return false;
		ServerSettlementData settlement = SettlementsWorldData.getSettlementsData(current.getServer()).getServerData(state.settlementUniqueID);
		if (settlement == null) return false;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null) return false;
		Level destination = getLevel(settlement, domain.getLevelIdentifier(destinationType));
		if (destination == null) return false;

		Point p = state.breachTile;
		current.entityManager.changeMobLevel(raider, destination, p.x * 32 + 16, p.y * 32 + 16, true);
		boolean changed = raider.getLevel() == destination;
		if (changed) clearInvalidCombatTargets(raider);
		return changed;
	}

	private static void clearInvalidCombatTargets(ItemAttackerRaiderMob raider) {
		if (raider == null || raider.ai == null || raider.ai.blackboard == null) return;
		Blackboard blackboard = raider.ai.blackboard;
		Mob current = (Mob)blackboard.getObject(Mob.class, "currentTarget");
		if (!isValidSameLevelCombatTarget(raider, current)) blackboard.put("currentTarget", null);
		Mob chaser = (Mob)blackboard.getObject(Mob.class, "chaserTarget");
		if (!isValidSameLevelCombatTarget(raider, chaser)) blackboard.put("chaserTarget", null);
	}

	private static boolean isValidSameLevelCombatTarget(ItemAttackerRaiderMob raider, Mob target) {
		return target != null && !target.removed() && target.getHealth() > 0 && target.isSamePlace(raider) && target.canTakeDamage() && target.canBeHit(raider);
	}

	public static synchronized double overrideActiveRaidStopBuffer(SettlementRaidLevelEvent event, double vanillaBuffer) {
		RaidState raid = getRaidState(event);
		if (raid == null || !raid.splitInitialized || raid.eventEnded || event == null || event.getLevel() == null) return vanillaBuffer;

		int alive = 0;
		int waiting = 0;
		for (Map.Entry<ItemAttackerRaiderMob, RaiderState> entry : raiderStates.entrySet()) {
			ItemAttackerRaiderMob raider = entry.getKey();
			RaiderState state = entry.getValue();
			if (raider == null || state == null || raider.removed() || raider.getHealth() <= 0) continue;
			if (state.settlementUniqueID != raid.settlementUniqueID || state.raidEventUniqueID != raid.raidEventUniqueID) continue;
			alive++;
			if (state.stage == RaiderStage.WAITING_RETREAT) waiting++;
		}

		if (alive == 0) return 1.0;
		long now = event.getLevel().getWorldEntity().getTime();
		if (waiting < alive) {
			raid.allGoalsReachedTime = 0L;
			raid.allGoalsReachedLogged = false;
			return 0.0;
		}

		if (raid.allGoalsReachedTime == 0L) {
			raid.allGoalsReachedTime = now;
			if (Logging.logEnabled) Logging.logMessage("[CaveRaid] All surviving raiders reached their goals; holding raid for " + (goalHoldDurationMs / 1000L) + " seconds before retreat settlement=" + raid.settlementUniqueID + " raid=" + raid.raidEventUniqueID + " alive=" + alive);
		}
		if (now - raid.allGoalsReachedTime < goalHoldDurationMs) return 0.0;

		if (!raid.allGoalsReachedLogged) {
			raid.allGoalsReachedLogged = true;
			if (Logging.logEnabled) Logging.logMessage("[CaveRaid] Goal hold complete; allowing raid retreat settlement=" + raid.settlementUniqueID + " raid=" + raid.raidEventUniqueID + " alive=" + alive);
		}
		return 1.0;
	}

	public static synchronized void ensureActiveCrossLevelRaiders(SettlementRaidLevelEvent event, LinkedList<ItemAttackerRaiderMob> raiders) {
		if (event == null || raiders == null || event.getServerSettlementData() == null) return;
		int settlementUniqueID = event.getServerSettlementData().uniqueID;
		int raidEventUniqueID = event.getUniqueID();
		for (Map.Entry<ItemAttackerRaiderMob, RaiderState> entry : raiderStates.entrySet()) {
			ItemAttackerRaiderMob raider = entry.getKey();
			RaiderState state = entry.getValue();
			if (raider == null || state == null || raider.removed()) continue;
			if (state.settlementUniqueID != settlementUniqueID || state.raidEventUniqueID != raidEventUniqueID) continue;
			if (!raiders.contains(raider)) {
				raiders.add(raider);
				if (Logging.logEnabled) Logging.logMessage("[CaveRaid] Restored cross-level raider " + raider.getUniqueID() + " to active raid list stage=" + state.stage);
			}
		}

	}

	public static synchronized PathDoorOption overridePathDoorOption(ItemAttackerRaiderMob raider, PathDoorOption vanilla) {
		RaiderState state = raiderStates.get(raider);
		if (state == null || !state.caveAssigned || !isCave(raider, state)) return vanilla;
		if (state.stage == RaiderStage.CAVE_ATTACK || state.stage == RaiderStage.CAVE_ESCAPE) return getCaveBreakOption(raider, state);
		if (state.stage == RaiderStage.WAITING_RETREAT) return getNoBreakOption(raider);
		return vanilla;
	}

	private static PathDoorOption getNoBreakOption(ItemAttackerRaiderMob raider) {
		RaiderState state = raiderStates.get(raider);
		if (state == null || raider.getLevel() == null) return null;
		if (state.noBreakOption == null || state.noBreakOption.level != raider.getLevel()) state.noBreakOption = new BasicPathDoorOption("CAVE_RAID_NO_BREAK", raider.getLevel(), false, false);
		return state.noBreakOption;
	}

	private static PathDoorOption getCaveBreakOption(ItemAttackerRaiderMob raider, RaiderState state) {
		if (state.caveBreakOption == null || state.caveBreakOption.level != raider.getLevel()) state.caveBreakOption = new CaveRaidPathDoorOption(raider, state);
		return state.caveBreakOption;
	}

	public static synchronized double overrideBreakCost(Mob mob, Level level, int tileX, int tileY, GameObject object, double vanilla) {
		if (!(mob instanceof ItemAttackerRaiderMob)) return vanilla;
		ItemAttackerRaiderMob raider = (ItemAttackerRaiderMob)mob;
		RaiderState state = raiderStates.get(raider);
		if (state == null || !state.caveAssigned || !isCave(raider, state) || object == null || object.getID() == 0 || object.toolType == ToolType.UNBREAKABLE) return vanilla;
		if (state.stage != RaiderStage.CAVE_ATTACK && state.stage != RaiderStage.CAVE_ESCAPE) return vanilla;

		LevelObject lo = level.getLevelObject(tileX, tileY);
		if (lo != null && !lo.isPlayerPlaced) return 1.0;

		// Player-built blockers remain valid routes, but keep their normal break cost so natural
		// cave terrain is strongly preferred whenever either route can make progress.
		return vanilla;
	}

	public static synchronized int overrideBreakDamage(Mob mob, LevelObject lo, int vanilla) {
		if (!(mob instanceof ItemAttackerRaiderMob) || lo == null || lo.object == null) return vanilla;
		ItemAttackerRaiderMob raider = (ItemAttackerRaiderMob)mob;
		RaiderState state = raiderStates.get(raider);
		if (state == null || !state.caveAssigned || !isCave(raider, state) || lo.object.toolType == ToolType.UNBREAKABLE) return vanilla;
		boolean fast = (state.stage == RaiderStage.CAVE_ATTACK || state.stage == RaiderStage.CAVE_ESCAPE) && !lo.isPlayerPlaced;
		if (!fast) return vanilla;

		// One visible pickaxe swing destroys the blocker. The one-second cost is the cooldown
		// before the raider can perform another path-break action, not a second swing.
		raider.pathBreakCooldown = fastBreakCooldownMs;
		showMiningSwing(raider, lo);
		int damage = Math.max(lo.object.objectHealth, 1);
		if (Logging.logEnabled) Logging.logMessage("[CaveRaid] Fast-breaking natural cave blocker raider=" + raider.getUniqueID() + " stage=" + state.stage + " object=" + lo.object.getStringID() + " tile=" + lo.tileX + "," + lo.tileY + " damage=" + damage + " vanilla=" + vanilla);
		return damage;
	}

	private static void showMiningSwing(ItemAttackerRaiderMob raider, LevelObject lo) {
		try {
			raider.showAttackAndSendAttacker(new InventoryItem("ironpickaxe"), lo.tileX * 32 + 16, lo.tileY * 32 + 16, 0, Item.getRandomAttackSeed(GameRandom.globalRandom));
		} catch (Exception ignored) {
		}
		if (raider.getLevel() != null && raider.getLevel().isServer()) raider.getLevel().getServer().network.sendToClientsWithTile(new PacketPlayObjectDamageSound(lo.tileX, lo.tileY, lo.object.getID()), raider.getLevel(), lo.tileX, lo.tileY);
	}

	public static void tryAttackWhileEscaping(ItemAttackerRaiderMob raider) {
		if (raider == null || !raider.isServer() || raider.weapon == null || !(raider.weapon.item instanceof ItemAttackerWeaponItem)) return;
		ItemAttackerWeaponItem weapon = (ItemAttackerWeaponItem)raider.weapon.item;
		int range = Math.max(32, weapon.getItemAttackerAttackRange(raider, raider.weapon));
		Mob target = (Mob)raider.getLevel().entityManager.streamAreaMobsAndPlayers(raider.x, raider.y, range + 32)
				.filter(m -> m != raider && !m.removed() && m.isVisible() && m.canTakeDamage() && m.canBeHit(raider) && m.canBeTargeted(raider, null) && (m.getTeam() == -100 || (m.isHuman && m.getTeam() != -1) || m.isPlayer) && raider.getDistance(m) <= range)
				.findBestDistance(1, Comparator.comparingDouble(raider::getDistance)).orElse(null);
		if (target == null || !weapon.canItemAttackerHitTarget(raider, raider.x, raider.y, target, raider.weapon)) return;
		ItemAttackSlot slot = raider.getCurrentSelectedAttackSlot();
		if (slot == null || !raider.canAttack() || raider.isItemOnCooldown(raider.weapon.item)) return;
		int seed = Item.getRandomAttackSeed(GameRandom.globalRandom);
		Point attackPos = weapon.getItemAttackerAttackPosition(raider.getLevel(), raider, target, seed, raider.weapon);
		if (attackPos == null || raider.weapon.item.canAttack(raider.getLevel(), attackPos.x, attackPos.y, raider, raider.weapon) != null) return;
		GNDItemMap map = raider.runItemAttack(raider.weapon, attackPos.x, attackPos.y, seed, 0, slot, null);
		raider.showItemAttackMobAbility.runAndSend(raider.weapon, attackPos.x, attackPos.y, 0, seed, map);
		escapeRetaliationUntil.put(raider, raider.getTime() + Math.max(50, raider.weapon.item.getAttackAnimTime(raider.weapon, raider)));
	}

	public static synchronized boolean isEscapeRetaliationAttack(ItemAttackerRaiderMob raider) {
		Long until = escapeRetaliationUntil.get(raider);
		if (until == null) return false;
		if (raider.getTime() <= until) return true;
		escapeRetaliationUntil.remove(raider);
		return false;
	}

	public static synchronized boolean isProtectedBreach(Level level, int tileX, int tileY) {
		if (level == null || !level.isServer() || level.getServer() == null) return false;

		BreachKey matchedKey = null;
		for (BreachKey key : protectedBreaches) {
			if (key.tileX != tileX || key.tileY != tileY) continue;
			ServerSettlementData settlement = SettlementsWorldData.getSettlementsData(level.getServer()).getServerData(key.settlementUniqueID);
			if (settlement == null) continue;
			SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
			if (domain != null && domain.getLevelType(level.getIdentifier()) != null) {
				matchedKey = key;
				break;
			}
		}

		if (matchedKey == null) return false;
		validateProtectedBreach(level, matchedKey);
		return protectedBreaches.contains(matchedKey);
	}

	private static void validateProtectedBreach(Level level, BreachKey key) {
		ArrayList<RaidState> matchingRaids = new ArrayList<>();
		Point breachPoint = new Point(key.tileX, key.tileY);
		for (RaidState raid : raidStates.values()) {
			if (raid != null && raid.settlementUniqueID == key.settlementUniqueID && raid.breaches.contains(breachPoint)) matchingRaids.add(raid);
		}

		if (matchingRaids.isEmpty()) {
			protectedBreaches.remove(key);
			if (Logging.logEnabled) Logging.logMessage("[CaveRaid] Released orphaned raid breach protection settlement=" + key.settlementUniqueID + " tile=" + key.tileX + "," + key.tileY);
			return;
		}

		ServerSettlementData settlement = SettlementsWorldData.getSettlementsData(level.getServer()).getServerData(key.settlementUniqueID);
		if (settlement == null) {
			for (RaidState raid : matchingRaids) releaseBreaches(raid);
			return;
		}

		Level surface = settlement.getLevel();
		for (RaidState raid : matchingRaids) {
			boolean eventEnded = raid.eventEnded;
			if (!eventEnded) {
				LevelEvent event = surface == null ? null : surface.entityManager.events.get(raid.raidEventUniqueID, false);
				if (!(event instanceof SettlementRaidLevelEvent) || event.isOver()) {
					raid.eventEnded = true;
					eventEnded = true;
					for (RaiderState rs : raiderStates.values()) {
						if (rs != null && rs.settlementUniqueID == raid.settlementUniqueID && rs.raidEventUniqueID == raid.raidEventUniqueID) rs.raidEventEnded = true;
					}
					if (Logging.logEnabled) Logging.logMessage("[CaveRaid] Protected breach check inferred missing/over raid event as ended settlement=" + raid.settlementUniqueID + " raid=" + raid.raidEventUniqueID);
				}
			}

			if (!eventEnded) {
				if (Logging.logEnabled) Logging.logMessage("[CaveRaid] Protected breach remains locked because raid is still active settlement=" + raid.settlementUniqueID + " tile=" + key.tileX + "," + key.tileY);
				continue;
			}

			int associatedLiving = 0;
			int associatedEscaping = 0;
			for (Map.Entry<ItemAttackerRaiderMob, RaiderState> entry : raiderStates.entrySet()) {
				ItemAttackerRaiderMob raider = entry.getKey();
				RaiderState rs = entry.getValue();
				if (raider == null || rs == null || !rs.caveAssigned || rs.breachTile == null) continue;
				if (rs.settlementUniqueID != raid.settlementUniqueID || rs.raidEventUniqueID != raid.raidEventUniqueID) continue;
				if (!rs.breachTile.equals(breachPoint)) continue;
				if (raider.removed() || raider.getHealth() <= 0) continue;
				associatedLiving++;
				if (raider.phase == RaiderMobPhase.LOOTING || raider.phase == RaiderMobPhase.ESCAPING) associatedEscaping++;
			}

			if (associatedEscaping == 0) {
				protectedBreaches.remove(key);
				if (Logging.logEnabled) Logging.logMessage("[CaveRaid] Released ended-raid breach protection settlement=" + raid.settlementUniqueID + " raid=" + raid.raidEventUniqueID + " tile=" + key.tileX + "," + key.tileY + " associatedLiving=" + associatedLiving + " associatedLootingOrEscaping=0");
				continue;
			}

			if (Logging.logEnabled) {
				StringBuilder livingDetails = new StringBuilder();
				for (Map.Entry<ItemAttackerRaiderMob, RaiderState> entry : raiderStates.entrySet()) {
					ItemAttackerRaiderMob raider = entry.getKey();
					RaiderState rs = entry.getValue();
					if (raider == null || rs == null || !rs.caveAssigned || rs.breachTile == null) continue;
					if (rs.settlementUniqueID != raid.settlementUniqueID || rs.raidEventUniqueID != raid.raidEventUniqueID) continue;
					if (!rs.breachTile.equals(breachPoint)) continue;
					if (raider.removed() || raider.getHealth() <= 0) continue;
					if (livingDetails.length() > 0) livingDetails.append("; ");
					livingDetails.append("raider=").append(raider.getUniqueID())
							.append(" phase=").append(raider.phase)
							.append(" level=").append(raider.getLevel() == null ? "null" : raider.getLevel().getIdentifier())
							.append(" tile=").append(raider.getTileX()).append(",").append(raider.getTileY())
							.append(" health=").append(raider.getHealth())
							.append(" stage=").append(rs.stage);
				}

				Logging.logMessage("[CaveRaid] Protected breach remains locked for associated looting/escaping raider settlement=" + raid.settlementUniqueID + " raid=" + raid.raidEventUniqueID + " tile=" + key.tileX + "," + key.tileY + " associatedLiving=" + associatedLiving + " associatedLootingOrEscaping=" + associatedEscaping + " details=[" + livingDetails + "]");
			}
		}
	}

	public static synchronized void onRaiderRemoved(ItemAttackerRaiderMob raider) {
		if (raider == null) return;
		escapeRetaliationUntil.remove(raider);
		RaiderState rs = raiderStates.remove(raider);
		if (rs == null) return;
		RaidState raid = findRaidState(rs.raidEventUniqueID, rs.settlementUniqueID);
		if (raid != null) {
			raid.remainingRaiderIDs.remove(raider.getUniqueID());
			if (raid.eventEnded && raid.remainingRaiderIDs.isEmpty()) releaseBreaches(raid);
		}
	}

	public static synchronized void onRaidOver(SettlementRaidLevelEvent event) {
		RaidState state = getRaidState(event);
		if (state != null) {
			state.eventEnded = true;
			for (RaiderState rs : raiderStates.values()) {
				if (rs != null && rs.settlementUniqueID == state.settlementUniqueID && rs.raidEventUniqueID == state.raidEventUniqueID) rs.raidEventEnded = true;
			}
			if (state.remainingRaiderIDs.isEmpty()) releaseBreaches(state);
		}
	}

	private static void releaseBreaches(RaidState state) {
		for (Point p : state.breaches) protectedBreaches.remove(new BreachKey(state.settlementUniqueID, p.x, p.y));
		raidStates.remove(new RaidKey(state.settlementUniqueID, state.raidEventUniqueID));
		if (Logging.logEnabled && !state.breaches.isEmpty()) Logging.logMessage("[CaveRaid] Raid breach protection released settlement=" + state.settlementUniqueID + " ladders=" + state.breaches.size());
	}

	public static synchronized SettlementRaidLevelEvent resolveRaidEvent(ItemAttackerRaiderMob raider, SettlementRaidLevelEvent vanilla) {
		if (vanilla != null) return vanilla;
		RaiderState state = raiderStates.get(raider);
		if (state == null || raider.getLevel() == null || raider.getLevel().getServer() == null) return null;
		ServerSettlementData settlement = SettlementsWorldData.getSettlementsData(raider.getLevel().getServer()).getServerData(state.settlementUniqueID);
		if (settlement == null) return null;
		Level surface = settlement.getLevel();
		if (surface == null) return null;
		LevelEvent e = surface.entityManager.events.get(state.raidEventUniqueID, false);
		return e instanceof SettlementRaidLevelEvent ? (SettlementRaidLevelEvent)e : null;
	}

	public static synchronized void recoverLoadedRaiders(SettlementRaidLevelEvent event, LinkedList<ItemAttackerRaiderMob> raiders, int[] ids) {
		if (event == null || ids == null || ids.length == 0 || event.getServerSettlementData() == null) return;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(event.getServerSettlementData());
		if (domain == null) return;
		Level cave = getLevel(event.getServerSettlementData(), domain.getLevelIdentifier(SettlementLevelType.CAVE));
		if (cave == null) return;
		for (int id : ids) {
			Mob m = cave.entityManager.mobs.get(id, false);
			if (m instanceof ItemAttackerRaiderMob && !raiders.contains(m)) raiders.add((ItemAttackerRaiderMob)m);
		}
	}

	public static synchronized void addRaidSave(SettlementRaidLevelEvent event, SaveData save) {
		RaidState state = getRaidState(event);
		if (state == null || !state.splitInitialized) return;
		SaveData d = new SaveData("dynamicSettlementsMultiLevelRaid");
		d.addInt("settlementUniqueID", state.settlementUniqueID);
		d.addBoolean("splitInitialized", state.splitInitialized);
		d.addBoolean("eventEnded", state.eventEnded);
		d.addDouble("caveShare", state.caveShare);
		d.addLong("allGoalsReachedTime", state.allGoalsReachedTime);
		int[] remaining = state.remainingRaiderIDs.stream().mapToInt(Integer::intValue).toArray();
		d.addIntArray("remainingRaiderIDs", remaining);
		if (!state.breaches.isEmpty()) {
			int[] points = new int[state.breaches.size() * 2];
			for (int i = 0; i < state.breaches.size(); i++) { points[i * 2] = state.breaches.get(i).x; points[i * 2 + 1] = state.breaches.get(i).y; }
			d.addIntArray("breaches", points);
		}
		save.addSaveData(d);
	}

	public static synchronized void applyRaidLoad(SettlementRaidLevelEvent event, LoadData save) {
		LoadData d = save.getFirstLoadDataByName("dynamicSettlementsMultiLevelRaid");
		if (d == null) return;
		int settlementUniqueID = d.getInt("settlementUniqueID", 0, false);
		RaidState state = findRaidState(event.getUniqueID(), settlementUniqueID);
		if (state == null) state = new RaidState(settlementUniqueID, event.getUniqueID());
		state.splitInitialized = d.getBoolean("splitInitialized", false);
		state.eventEnded = d.getBoolean("eventEnded", state.eventEnded);
		state.caveShare = d.getDouble("caveShare", 0.0, false);
		state.allGoalsReachedTime = d.getLong("allGoalsReachedTime", 0L, false);
		for (int id : d.getIntArray("remainingRaiderIDs", new int[0])) state.remainingRaiderIDs.add(id);
		int[] points = d.getIntArray("breaches", new int[0]);
		for (int i = 0; i + 1 < points.length; i += 2) {
			Point p = new Point(points[i], points[i + 1]);
			if (!state.breaches.contains(p)) state.breaches.add(p);
			protectedBreaches.add(new BreachKey(settlementUniqueID, p.x, p.y));
		}
		raidStates.put(new RaidKey(state.settlementUniqueID, state.raidEventUniqueID), state);
	}

	public static synchronized void addRaiderSave(ItemAttackerRaiderMob raider, SaveData save) {
		RaiderState s = raiderStates.get(raider);
		if (s == null) return;
		SaveData d = new SaveData("dynamicSettlementsCaveRaid");
		d.addInt("settlementUniqueID", s.settlementUniqueID);
		d.addInt("raidEventUniqueID", s.raidEventUniqueID);
		d.addBoolean("caveAssigned", s.caveAssigned);
		if (s.surfaceTarget != null) d.addPoint("surfaceTarget", s.surfaceTarget);
		if (s.caveTarget != null) d.addPoint("caveTarget", s.caveTarget);
		if (s.breachTile != null) d.addPoint("breachTile", s.breachTile);
		d.addEnum("stage", s.stage);
		d.addBoolean("breachCreated", s.breachCreated);
		d.addBoolean("raidEventEnded", s.raidEventEnded);
		save.addSaveData(d);
	}

	public static synchronized void applyRaiderLoad(ItemAttackerRaiderMob raider, LoadData save) {
		LoadData d = save.getFirstLoadDataByName("dynamicSettlementsCaveRaid");
		if (d == null) return;
		RaiderState s = new RaiderState(d.getInt("settlementUniqueID"), d.getInt("raidEventUniqueID"));
		s.caveAssigned = d.getBoolean("caveAssigned", false);
		s.surfaceTarget = d.getPoint("surfaceTarget", null, false);
		s.caveTarget = d.getPoint("caveTarget", null, false);
		s.breachTile = d.getPoint("breachTile", null, false);
		s.stage = d.getEnum(RaiderStage.class, "stage", s.caveAssigned ? RaiderStage.CAVE_ATTACK : RaiderStage.SURFACE_APPROACH, false);
		s.breachCreated = d.getBoolean("breachCreated", false);
		s.raidEventEnded = d.getBoolean("raidEventEnded", false);
		raiderStates.put(raider, s);

		RaidState raid = findRaidState(s.raidEventUniqueID, s.settlementUniqueID);
		if (raid == null) {
			raid = new RaidState(s.settlementUniqueID, s.raidEventUniqueID);
			raid.splitInitialized = true;
			raidStates.put(new RaidKey(s.settlementUniqueID, s.raidEventUniqueID), raid);
		}
		raid.remainingRaiderIDs.add(raider.getUniqueID());
		raid.eventEnded |= s.raidEventEnded;
		if (s.breachCreated && s.breachTile != null) {
			if (!raid.breaches.contains(s.breachTile)) raid.breaches.add(new Point(s.breachTile));
			protectedBreaches.add(new BreachKey(s.settlementUniqueID, s.breachTile.x, s.breachTile.y));
		}
	}

	private static WealthData scanStoredWealth(Level level, Rectangle bounds) {
		WealthData data = new WealthData();
		if (level == null || bounds == null) return data;
		for (int x = bounds.x; x < bounds.x + bounds.width; x++) {
			for (int y = bounds.y; y < bounds.y + bounds.height; y++) {
				ObjectEntity entity = level.entityManager.getObjectEntity(x, y);
				if (!(entity instanceof OEInventory)) continue;
				Inventory inventory = ((OEInventory)entity).getInventory();
				if (inventory == null) continue;
				double tileValue = 0.0;
				for (int slot = 0; slot < inventory.getSize(); slot++) {
					InventoryItem item = inventory.getItem(slot);
					if (item != null) tileValue += Math.max(0.0F, item.getBrokerValue());
				}
				if (tileValue > 0.0) {
					data.total += tileValue;
					data.tiles.put(new Point(x, y), tileValue);
				}
			}
		}
		return data;
	}

	private static Point chooseWeightedTarget(Map<Point, Double> weights) {
		if (weights.isEmpty()) return null;
		double total = weights.values().stream().mapToDouble(v -> Math.sqrt(Math.max(1.0, v))).sum();
		double roll = GameRandom.globalRandom.nextDouble() * total;
		for (Map.Entry<Point, Double> e : weights.entrySet()) {
			roll -= Math.sqrt(Math.max(1.0, e.getValue()));
			if (roll <= 0.0) return new Point(e.getKey());
		}
		return new Point(weights.keySet().iterator().next());
	}

	private static Point chooseBreachPoint(Level surface, Rectangle bounds, ItemAttackerRaiderMob raider, Point caveTarget, List<Point> used) {
		if (bounds == null) return null;
		ArrayList<Point> candidates = new ArrayList<>();
		int left = bounds.x - 1;
		int right = bounds.x + bounds.width;
		int top = bounds.y - 1;
		int bottom = bounds.y + bounds.height;
		for (int x = bounds.x; x < bounds.x + bounds.width; x++) { candidates.add(new Point(x, top)); candidates.add(new Point(x, bottom)); }
		for (int y = bounds.y; y < bounds.y + bounds.height; y++) { candidates.add(new Point(left, y)); candidates.add(new Point(right, y)); }
		candidates.removeIf(p -> !surface.isTileWithinBounds(p.x, p.y) || surface.getObjectID(p.x, p.y) != 0 || surface.getTile(p.x, p.y).isLiquid);
		candidates.sort(Comparator.comparingDouble(p -> p.distance(raider.getTileX(), raider.getTileY()) * 0.65 + p.distance(caveTarget) * 0.35));
		for (int spacing = minimumBreachSpacing; spacing >= 1; spacing--) {
			for (Point candidate : candidates) {
				boolean okay = true;
				for (Point other : used) if (candidate.distance(other) < spacing) { okay = false; break; }
				if (okay) return candidate;
			}
		}
		return null;
	}

	private static Level getLevel(ServerSettlementData settlement, LevelIdentifier identifier) {
		if (settlement == null || identifier == null || settlement.getServer() == null || !settlement.getServer().world.levelExists(identifier)) return null;
		return settlement.getServer().world.getLevel(identifier);
	}

	private static boolean isSurface(ItemAttackerRaiderMob r, RaiderState s) { return isLevelType(r, s, SettlementLevelType.SURFACE); }
	private static boolean isCave(ItemAttackerRaiderMob r, RaiderState s) { return isLevelType(r, s, SettlementLevelType.CAVE); }
	private static boolean isLevelType(ItemAttackerRaiderMob r, RaiderState s, SettlementLevelType type) {
		if (r.getLevel() == null || r.getLevel().getServer() == null) return false;
		ServerSettlementData settlement = SettlementsWorldData.getSettlementsData(r.getLevel().getServer()).getServerData(s.settlementUniqueID);
		SettlementLevelDomain domain = settlement == null ? null : SettlementMultiLevelSystem.get(settlement);
		return domain != null && type == domain.getLevelType(r.getLevel().getIdentifier());
	}

	private static RaidState getRaidState(SettlementRaidLevelEvent event) {
		if (event == null) return null;
		ServerSettlementData settlement = event.getServerSettlementData();
		if (settlement == null) return null;
		return findRaidState(event.getUniqueID(), settlement.uniqueID);
	}

	private static RaidState findRaidState(int eventUniqueID, int settlementUniqueID) {
		return raidStates.get(new RaidKey(settlementUniqueID, eventUniqueID));
	}

	private static boolean isBreakable(GameObject object) { return object != null && object.getID() != 0 && object.toolType != ToolType.UNBREAKABLE; }

	private static final class CaveRaidPathDoorOption extends PathDoorOption {
		private final ItemAttackerRaiderMob raider;
		private final RaiderState state;
		private CaveRaidPathDoorOption(ItemAttackerRaiderMob raider, RaiderState state) { super("CAVE_RAID_BREAK", raider.getLevel()); this.raider = raider; this.state = state; }
		@Override public SubRegionPathResult canPathThrough(SubRegion subregion) { return subregion.getType() == RegionType.OPEN ? SubRegionPathResult.VALID : SubRegionPathResult.CHECK_EACH_TILE; }
		@Override public boolean canPathThroughCheckTile(SubRegion subregion, int tileX, int tileY) { GameObject object = level.getObject(tileX, tileY); return object.getID() == 0 || canBreakDown(tileX, tileY) || object instanceof DoorObject && ((DoorObject)object).isOpen(level, tileX, tileY, level.getObjectRotation(tileX, tileY)); }
		@Override
		public boolean canBreakDown(int tileX, int tileY) {
			if (state.stage != RaiderStage.CAVE_ATTACK && state.stage != RaiderStage.CAVE_ESCAPE) return false;
			LevelObject lo = level.getLevelObject(tileX, tileY);
			return lo != null && lo.object != null && isBreakable(lo.object);
		}
		@Override public boolean canOpen(int tileX, int tileY) { return false; }
		@Override public boolean canClose(int tileX, int tileY) { return false; }
		@Override public boolean doorChangeInvalidatesCache(DoorObject lastDoor, DoorObject newDoor, int tileX, int tileY) { return true; }
	}

	private static final class LootCandidate {
		final Inventory inventory; final int slot; final int tileX; final int tileY; final int amount; final float value;
		LootCandidate(Inventory inventory, int slot, int tileX, int tileY, int amount, float value) { this.inventory = inventory; this.slot = slot; this.tileX = tileX; this.tileY = tileY; this.amount = amount; this.value = value; }
	}
	private enum Role { MELEE, RANGED, SPECIAL }
	private enum RaiderStage { SURFACE_APPROACH, APPROACH_BREACH, BREACH_READY, CAVE_ATTACK, WAITING_RETREAT, CAVE_ESCAPE, SURFACE_ESCAPE }
	private static final class GroupMetrics { double value; int melee; int ranged; int special; int count; }
	private static final class WealthData { double total; final Map<Point, Double> tiles = new LinkedHashMap<>(); }
	private static final class RaidState {
		final int settlementUniqueID; final int raidEventUniqueID; boolean splitInitialized; boolean eventEnded; boolean allGoalsReachedLogged; long allGoalsReachedTime; double caveShare; final Set<Integer> remainingRaiderIDs = new HashSet<>(); final List<Point> breaches = new ArrayList<>();
		RaidState(int settlementUniqueID, int raidEventUniqueID) { this.settlementUniqueID = settlementUniqueID; this.raidEventUniqueID = raidEventUniqueID; }
	}
	private static final class RaiderState {
		final int settlementUniqueID; final int raidEventUniqueID; boolean caveAssigned; Point surfaceTarget; Point caveTarget; Point breachTile; RaiderStage stage = RaiderStage.SURFACE_APPROACH; boolean breachCreated; boolean raidEventEnded; long nextLootAttemptTime; long nextLootMissLogTime; PathDoorOption caveBreakOption; PathDoorOption noBreakOption;
		RaiderState(int settlementUniqueID, int raidEventUniqueID) { this.settlementUniqueID = settlementUniqueID; this.raidEventUniqueID = raidEventUniqueID; }
	}
	private static final class RaidKey {
		final int settlementUniqueID; final int raidEventUniqueID;
		RaidKey(int settlementUniqueID, int raidEventUniqueID) { this.settlementUniqueID = settlementUniqueID; this.raidEventUniqueID = raidEventUniqueID; }
		@Override public boolean equals(Object o) { if (!(o instanceof RaidKey)) return false; RaidKey r = (RaidKey)o; return settlementUniqueID == r.settlementUniqueID && raidEventUniqueID == r.raidEventUniqueID; }
		@Override public int hashCode() { return 31 * settlementUniqueID + raidEventUniqueID; }
	}
	private static final class BreachKey {
		final int settlementUniqueID; final int tileX; final int tileY;
		BreachKey(int settlementUniqueID, int tileX, int tileY) { this.settlementUniqueID = settlementUniqueID; this.tileX = tileX; this.tileY = tileY; }
		@Override public boolean equals(Object o) { if (!(o instanceof BreachKey)) return false; BreachKey b = (BreachKey)o; return settlementUniqueID == b.settlementUniqueID && tileX == b.tileX && tileY == b.tileY; }
		@Override public int hashCode() { return 31 * (31 * settlementUniqueID + tileX) + tileY; }
	}
}
