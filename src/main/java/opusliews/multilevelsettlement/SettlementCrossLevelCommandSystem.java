package opusliews.multilevelsettlement;

import java.awt.Point;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import necesse.engine.network.server.ServerClient;
import necesse.engine.util.LevelIdentifier;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.ai.behaviourTree.AINode;
import necesse.entity.mobs.ai.behaviourTree.AINodeResult;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.event.AIEvent;
import necesse.entity.mobs.ai.behaviourTree.leaves.HumanCommandFollowMobAINode;
import necesse.entity.mobs.ai.behaviourTree.util.MoveToTileAITask;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.mobs.networkField.BooleanNetworkField;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opusliews.logging.Logging;
import opusliews.progression.GuideProgressionSystem;

public final class SettlementCrossLevelCommandSystem {
	private static final Field clientHasCommandOrdersField = getClientHasCommandOrdersField();
	private static final Map<HumanMob, FollowState> followStates = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<HumanMob, GuardState> guardStates = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<HumanMob, AttackState> attackStates = Collections.synchronizedMap(new WeakHashMap<>());
	private static final ThreadLocal<ServerClient> commandResolveClient = new ThreadLocal<>();

	private SettlementCrossLevelCommandSystem() {
	}


	public static void beginCommandResolution(ServerClient client) {
		if (client != null) commandResolveClient.set(client);
	}

	public static void endCommandResolution() {
		commandResolveClient.remove();
	}

	public static HumanMob resolveCommandSettler(int uniqueID) {
		ServerClient client = commandResolveClient.get();
		if (client == null || client.getServer() == null) return null;

		for (Object levelObject : client.getServer().world.levelManager.getLoadedLevels()) {
			if (!(levelObject instanceof necesse.level.maps.Level)) continue;
			necesse.level.maps.Level level = (necesse.level.maps.Level)levelObject;
			Mob mob = level.entityManager.mobs.get(uniqueID, false);
			if (!(mob instanceof HumanMob)) continue;

			HumanMob human = (HumanMob)mob;
			if (Logging.logEnabled) {
				Logging.logMessage("[CrossLevelCommands] Resolved command target across loaded levels settler="
						+ uniqueID
						+ " playerLevel=" + (client.playerMob == null || client.playerMob.getLevel() == null ? "null" : client.playerMob.getLevel().getIdentifier())
						+ " settlerLevel=" + level.getIdentifier());
			}
			return human;
		}

		if (Logging.logEnabled) Logging.logMessage("[CrossLevelCommands] Could not resolve command target across loaded levels settler=" + uniqueID);
		return null;
	}

	public static void onFollowCommand(HumanMob human, Mob target) {
		if (human == null) return;
		guardStates.remove(human);
		attackStates.remove(human);
		if (!isSupportedCrossLevelTarget(human, target)) {
			FollowState old = followStates.remove(human);
			if (old != null) old.pathTask = null;
			return;
		}

		FollowState state = new FollowState(target);
		followStates.put(human, state);
		if (Logging.logEnabled) Logging.logMessage("[CrossLevelCommands] Follow target is on another settlement level settler=" + human.getUniqueID() + " target=" + target.getStringID() + "#" + target.getUniqueID() + " targetLevel=" + target.getLevel().getIdentifier());
		if (target instanceof PlayerMob && ((PlayerMob)target).isServerClient()) {
			GuideProgressionSystem.onCrossLevelCommand(((PlayerMob)target).getServerClient());
		}

		// Do not wait for vanilla's follow AI to start. When the order is issued from another
		// level, vanilla may store commandFollowMob without ever ticking its follow node.
		// Arm and start the ladder trip directly from command assignment instead.
		tickFollowTravel(human, state);
	}

	public static boolean commandGuardCrossLevel(HumanMob human, ServerClient commander, int x, int y) {
		if (human == null || commander == null || commander.playerMob == null || human.getLevel() == null || commander.playerMob.getLevel() == null) return false;
		if (!human.isSettler() || human.isSamePlace(commander.playerMob)) return false;
		ServerSettlementData settlement = human.getSettlerSettlementServerData();
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null) return false;
		SettlementLevelType sourceType = domain.getLevelType(human.getLevel().getIdentifier());
		SettlementLevelType targetType = domain.getLevelType(commander.playerMob.getLevel().getIdentifier());
		if (sourceType == null || targetType == null || sourceType == targetType) return false;

		human.cancelJob();
		human.commandFollowMob = null;
		human.commandGuardPoint = new Point(x, y);
		human.commandMoveToGuardPoint = true;
		human.commandMoveToFollowPoint = false;
		human.commandAttackMob = null;
		human.stopMovingIn();
		if (human.isSettlerWithinSettlement()) human.adventureParty.clear(true);
		human.ai.blackboard.submitEvent("resetTarget", new AIEvent());
		human.ai.blackboard.submitEvent("newCommandSet", new AIEvent());
		human.resetCommandsBuffer = 600000;

		followStates.remove(human);
		attackStates.remove(human);
		guardStates.put(human, new GuardState(commander.playerMob.getLevel().getIdentifier()));
		if (Logging.logEnabled) Logging.logMessage("[CrossLevelCommands] Guard command will travel through settlement ladder settler=" + human.getUniqueID() + " from=" + sourceType + " to=" + targetType + " target=" + x + "," + y);
		GuideProgressionSystem.onCrossLevelCommand(commander);
		return true;
	}

	public static void onVanillaGuardCommand(HumanMob human) {
		if (human == null) return;
		guardStates.remove(human);
		followStates.remove(human);
		attackStates.remove(human);
	}

	public static void clear(HumanMob human) {
		if (human == null) return;
		FollowState follow = followStates.remove(human);
		if (follow != null) follow.pathTask = null;
		guardStates.remove(human);
		AttackState attack = attackStates.remove(human);
		if (attack != null) attack.pathTask = null;
		if (Logging.logEnabled) Logging.logMessage("[CrossLevelCommands] Cleared cross-level command state settler=" + human.getUniqueID());
	}

	public static boolean commandAttackCrossLevel(HumanMob human, ServerClient commander, Mob target) {
		if (!isSupportedCrossLevelTarget(human, target)) return false;
		if (!target.canBeTargeted(human, null)) return false;

		human.commandMoveToGuardPoint = false;
		human.commandMoveToFollowPoint = false;
		human.stopMovingIn();
		human.commandAttackMob = target;
		human.ai.blackboard.submitEvent("resetTarget", new AIEvent());
		human.ai.blackboard.submitEvent("newCommandSet", new AIEvent());
		human.resetCommandsBuffer = 600000;

		followStates.remove(human);
		guardStates.remove(human);
		AttackState state = new AttackState(target);
		attackStates.put(human, state);
		if (Logging.logEnabled) Logging.logMessage("[CrossLevelCommands] Attack command will travel through settlement ladder settler=" + human.getUniqueID() + " target=" + target.getStringID() + "#" + target.getUniqueID() + " targetLevel=" + target.getLevel().getIdentifier());
		GuideProgressionSystem.onCrossLevelCommand(commander);
		tickAttackTravel(human, state);
		return true;
	}

	public static void onVanillaAttackCommand(HumanMob human) {
		if (human == null) return;
		attackStates.remove(human);
	}

	public static boolean shouldSuppressVanillaAttackTargeting(HumanMob human) {
		return human != null && human.commandAttackMob != null && isSupportedCrossLevelTarget(human, human.commandAttackMob);
	}

	public static void restoreAttackAfterServerTick(HumanMob human) {
		if (human == null || human.getLevel() == null || !human.getLevel().isServer()) return;
		AttackState state = attackStates.get(human);
		if (state == null) return;
		if (state.target == null || state.target.removed() || state.target.getHealth() <= 0 || state.target.getLevel() == null) {
			human.commandAttackMob = null;
			attackStates.remove(human);
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelCommands] Attack target disappeared while travelling settler=" + human.getUniqueID());
			return;
		}
		if (human.isSamePlace(state.target)) {
			state.pathTask = null;
			attackStates.remove(human);
			human.ai.blackboard.submitEvent("resetPathTime", new AIEvent());
			human.ai.blackboard.submitEvent("resetTarget", new AIEvent());
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelCommands] Attack target reached same level settler=" + human.getUniqueID() + " target=" + state.target.getStringID() + "#" + state.target.getUniqueID());
			return;
		}
		if (!isSupportedCrossLevelTarget(human, state.target)) {
			human.commandAttackMob = null;
			attackStates.remove(human);
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelCommands] Attack target left settlement domain while travelling settler=" + human.getUniqueID() + " target=" + state.target.getStringID() + "#" + state.target.getUniqueID());
			return;
		}
		if (human.commandAttackMob != state.target) human.commandAttackMob = state.target;
		tickAttackTravel(human, state);
	}

	public static void restoreFollowAfterServerTick(HumanMob human) {
		if (human == null || human.getLevel() == null || !human.getLevel().isServer()) return;
		FollowState state = followStates.get(human);
		if (state == null || state.target == null || state.target.removed()) {
			followStates.remove(human);
			return;
		}
		if (state.target.isSamePlace(human)) {
			state.pathTask = null;
			followStates.remove(human);
			return;
		}
		if (!isSupportedCrossLevelTarget(human, state.target)) {
			state.pathTask = null;
			followStates.remove(human);
			return;
		}

		if (human.commandFollowMob == null) human.commandFollowMob = state.target;
		setClientHasCommandOrders(human, true);
		tickFollowTravel(human, state);
	}

	public static boolean shouldSuppressAdventurePartyTeleport(HumanMob human) {
		if (human == null) return false;
		FollowState state = followStates.get(human);
		return state != null && state.target != null && !state.target.removed() && isSupportedCrossLevelTarget(human, state.target);
	}

	public static AINodeResult tickCrossLevelFollow(HumanCommandFollowMobAINode node, Mob target, HumanMob human, Blackboard<?> blackboard) {
		if (target == null || human == null || human.getLevel() == null || target.getLevel() == null) return null;
		if (target.isSamePlace(human)) return null;
		if (!isSupportedCrossLevelTarget(human, target)) return null;

		FollowState state = followStates.get(human);
		if (state == null || state.target != target) {
			state = new FollowState(target);
			followStates.put(human, state);
		}

		// Cross-level movement is owned by the server-tick travel node. Keeping vanilla's
		// follow node in RUNNING state prevents it from trying to path directly to a mob on
		// another level or overriding our ladder path.
		return AINodeResult.RUNNING;
	}

	private static void tickFollowTravel(HumanMob human, FollowState state) {
		if (human == null || state == null || state.target == null || state.target.removed()) return;
		if (human.getLevel() == null || state.target.getLevel() == null || state.target.isSamePlace(human)) return;

		ServerSettlementData settlement = human.getSettlerSettlementServerData();
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null) return;
		SettlementLevelType currentType = domain.getLevelType(human.getLevel().getIdentifier());
		SettlementLevelType targetType = domain.getLevelType(state.target.getLevel().getIdentifier());
		if (currentType == null || targetType == null || currentType == targetType) return;

		Blackboard<?> blackboard = human.ai.blackboard;
		if (blackboard == null || blackboard.mover == null) return;
		state.ensureTravelNode(human, blackboard);

		if (state.route == null || state.targetType != targetType) {
			if (human.getTime() < state.nextRouteSearchTime) return;
			state.nextRouteSearchTime = human.getTime() + 3000L;
			state.route = SettlementCrossLevelRouting.findBestTransitionRoute(human, domain, targetType);
			state.targetType = targetType;
			state.pathTask = null;
			if (state.route == null || state.route.ladder == null) {
				state.route = null;
				if (Logging.logEnabled) Logging.logMessage("[CrossLevelCommands] Follow has no designated ladder route settler=" + human.getUniqueID() + " current=" + currentType + " targetLevel=" + targetType);
				return;
			}
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelCommands] Follow routing to ladder settler=" + human.getUniqueID() + " current=" + currentType + " targetLevel=" + targetType + " route=" + state.route);
		}

		if (SettlementLadderSystem.isMobAtOrAdjacentLadder(human, state.route.ladder, currentType)) {
			if (blackboard.mover.isCurrentlyMovingFor(state.travelNode)) blackboard.mover.stopMoving(human);
			boolean transitioned = SettlementLadderSystem.transitionMob(human, state.route.ladder, targetType);
			state.pathTask = null;
			state.route = null;
			if (transitioned) {
				human.commandFollowMob = state.target;
				human.commandMoveToFollowPoint = true;
				blackboard.submitEvent("resetPathTime", new AIEvent());
				blackboard.submitEvent("resetTarget", new AIEvent());
				if (Logging.logEnabled) Logging.logMessage("[CrossLevelCommands] Follow ladder transition completed settler=" + human.getUniqueID() + " targetLevel=" + targetType);
			} else if (Logging.logEnabled) {
				Logging.logMessage("[CrossLevelCommands] Follow ladder transition FAILED settler=" + human.getUniqueID() + " targetLevel=" + targetType);
			}
			return;
		}

		if (state.pathTask != null && state.pathTask.isComplete()) {
			try {
				AINodeResult result = state.pathTask.runComplete();
				state.pathTask = null;
				if (result == AINodeResult.FAILURE) {
					state.route = null;
					state.nextRouteSearchTime = human.getTime() + 3000L;
				}
			} catch (Exception e) {
				state.pathTask = null;
				state.route = null;
				state.nextRouteSearchTime = human.getTime() + 3000L;
				if (Logging.logEnabled) Logging.logMessage("[CrossLevelCommands] Follow ladder path exception settler=" + human.getUniqueID() + " error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
			}
		}

		if (blackboard.mover.isCurrentlyMovingFor(state.travelNode)) return;
		if (blackboard.mover.isMoving()) blackboard.mover.stopMoving(human);
		if (state.pathTask == null && state.route != null) {
			int ladderX = state.route.ladder.getTileX(currentType);
			int ladderY = state.route.ladder.getTileY(currentType);
			state.pathTask = blackboard.mover.moveToTileTask(state.travelNode, ladderX, ladderY, null, path -> {
				boolean moving = path.moveIfWithin(-1, 0, null);
				return moving ? AINodeResult.RUNNING : AINodeResult.FAILURE;
			});
		}
	}

	private static void tickAttackTravel(HumanMob human, AttackState state) {
		if (human == null || state == null || state.target == null || state.target.removed()) return;
		if (human.getLevel() == null || state.target.getLevel() == null || state.target.isSamePlace(human)) return;

		ServerSettlementData settlement = human.getSettlerSettlementServerData();
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null) return;
		SettlementLevelType currentType = domain.getLevelType(human.getLevel().getIdentifier());
		SettlementLevelType targetType = domain.getLevelType(state.target.getLevel().getIdentifier());
		if (currentType == null || targetType == null || currentType == targetType) return;

		Blackboard blackboard = human.ai.blackboard;
		if (blackboard == null || blackboard.mover == null) return;
		state.ensureTravelNode(human, blackboard);

		if (state.route == null || state.targetType != targetType) {
			if (human.getTime() < state.nextRouteSearchTime) return;
			state.nextRouteSearchTime = human.getTime() + 3000L;
			state.route = SettlementCrossLevelRouting.findBestTransitionRoute(human, domain, targetType);
			state.targetType = targetType;
			state.pathTask = null;
			if (state.route == null || state.route.ladder == null) {
				state.route = null;
				if (Logging.logEnabled) Logging.logMessage("[CrossLevelCommands] Attack has no designated ladder route settler=" + human.getUniqueID() + " current=" + currentType + " targetLevel=" + targetType);
				return;
			}
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelCommands] Attack routing to ladder settler=" + human.getUniqueID() + " current=" + currentType + " targetLevel=" + targetType + " route=" + state.route);
		}

		if (SettlementLadderSystem.isMobAtOrAdjacentLadder(human, state.route.ladder, currentType)) {
			if (blackboard.mover.isCurrentlyMovingFor(state.travelNode)) blackboard.mover.stopMoving(human);
			boolean transitioned = SettlementLadderSystem.transitionMob(human, state.route.ladder, targetType);
			state.pathTask = null;
			state.route = null;
			if (transitioned) {
				human.commandAttackMob = state.target;
				blackboard.submitEvent("resetPathTime", new AIEvent());
				blackboard.submitEvent("resetTarget", new AIEvent());
				if (Logging.logEnabled) Logging.logMessage("[CrossLevelCommands] Attack ladder transition completed settler=" + human.getUniqueID() + " targetLevel=" + targetType);
			} else {
				state.nextRouteSearchTime = human.getTime() + 3000L;
				if (Logging.logEnabled) Logging.logMessage("[CrossLevelCommands] Attack ladder transition FAILED settler=" + human.getUniqueID() + " targetLevel=" + targetType + "; route will be re-evaluated");
			}
			return;
		}

		if (state.pathTask != null && state.pathTask.isComplete()) {
			try {
				AINodeResult result = state.pathTask.runComplete();
				state.pathTask = null;
				if (result == AINodeResult.FAILURE) {
					state.route = null;
					state.nextRouteSearchTime = human.getTime() + 3000L;
				}
			} catch (Exception e) {
				state.pathTask = null;
				state.route = null;
				state.nextRouteSearchTime = human.getTime() + 3000L;
				if (Logging.logEnabled) Logging.logMessage("[CrossLevelCommands] Attack ladder path exception settler=" + human.getUniqueID() + " error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
			}
		}

		if (blackboard.mover.isCurrentlyMovingFor(state.travelNode)) return;
		if (blackboard.mover.isMoving()) blackboard.mover.stopMoving(human);
		if (state.pathTask == null && state.route != null) {
			int ladderX = state.route.ladder.getTileX(currentType);
			int ladderY = state.route.ladder.getTileY(currentType);
			state.pathTask = blackboard.mover.moveToTileTask(state.travelNode, ladderX, ladderY, null, path -> {
				boolean moving = path.moveIfWithin(-1, 0, null);
				return moving ? AINodeResult.RUNNING : AINodeResult.FAILURE;
			});
		}
	}

	public static Point getCrossLevelGuardMoveTarget(HumanMob human) {
		if (human == null || human.getLevel() == null) return null;
		GuardState state = guardStates.get(human);
		if (state == null || state.targetLevel == null) return null;
		ServerSettlementData settlement = human.getSettlerSettlementServerData();
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null) {
			guardStates.remove(human);
			return null;
		}
		SettlementLevelType currentType = domain.getLevelType(human.getLevel().getIdentifier());
		SettlementLevelType targetType = domain.getLevelType(state.targetLevel);
		if (currentType == null || targetType == null || currentType == targetType) {
			if (currentType == targetType) guardStates.remove(human);
			return null;
		}
		if (state.targetType != targetType) {
			state.route = null;
			state.targetType = targetType;
			state.nextRouteSearchTime = 0L;
		}
		if (state.route == null && human.getTime() >= state.nextRouteSearchTime) {
			state.nextRouteSearchTime = human.getTime() + 3000L;
			state.route = SettlementCrossLevelRouting.findBestTransitionRoute(human, domain, targetType);
			if (state.route == null || state.route.ladder == null) {
				state.route = null;
				if (Logging.logEnabled) Logging.logMessage("[CrossLevelCommands] Guard has no designated ladder route settler=" + human.getUniqueID() + " current=" + currentType + " targetLevel=" + targetType);
			}
		}
		if (state.route == null || state.route.ladder == null) {
			return new Point(human.getX(), human.getY());
		}
		return new Point(state.route.ladder.getTileX(currentType) * 32 + 16, state.route.ladder.getTileY(currentType) * 32 + 16);
	}

	public static boolean onCrossLevelGuardArrived(HumanMob human) {
		if (human == null || human.getLevel() == null) return false;
		GuardState state = guardStates.get(human);
		if (state == null || state.targetLevel == null) return false;
		ServerSettlementData settlement = human.getSettlerSettlementServerData();
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null) {
			guardStates.remove(human);
			return false;
		}
		SettlementLevelType currentType = domain.getLevelType(human.getLevel().getIdentifier());
		SettlementLevelType targetType = domain.getLevelType(state.targetLevel);
		if (currentType == null || targetType == null || currentType == targetType) {
			guardStates.remove(human);
			return false;
		}
		if (state.route == null || state.route.ladder == null) return true;
		if (!SettlementLadderSystem.isMobAtOrAdjacentLadder(human, state.route.ladder, currentType)) return true;
		boolean transitioned = SettlementLadderSystem.transitionMob(human, state.route.ladder, targetType);
		if (transitioned) {
			state.route = null;
			human.commandMoveToGuardPoint = true;
			human.ai.blackboard.submitEvent("resetPathTime", new AIEvent());
			human.ai.blackboard.submitEvent("resetTarget", new AIEvent());
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelCommands] Guard ladder transition completed settler=" + human.getUniqueID() + " targetLevel=" + targetType + " finalTarget=" + human.commandGuardPoint);
		} else {
			state.route = null;
			state.nextRouteSearchTime = human.getTime() + 3000L;
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelCommands] Guard ladder transition FAILED settler=" + human.getUniqueID() + " targetLevel=" + targetType + "; route will be re-evaluated");
		}
		return true;
	}

	private static Field getClientHasCommandOrdersField() {
		try {
			Field field = HumanMob.class.getDeclaredField("clientHasCommandOrders");
			field.setAccessible(true);
			return field;
		} catch (ReflectiveOperationException e) {
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelCommands] Could not access client command-order network field: " + e.getMessage());
			return null;
		}
	}

	private static void setClientHasCommandOrders(HumanMob human, boolean value) {
		if (clientHasCommandOrdersField == null || human == null) return;
		try {
			Object fieldValue = clientHasCommandOrdersField.get(human);
			if (fieldValue instanceof BooleanNetworkField) ((BooleanNetworkField)fieldValue).set(value);
		} catch (IllegalAccessException e) {
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelCommands] Could not synchronize command-order state settler=" + human.getUniqueID() + " error=" + e.getMessage());
		}
	}

	private static boolean isSupportedCrossLevelTarget(HumanMob human, Mob target) {
		if (human == null || target == null || human.getLevel() == null || target.getLevel() == null || target.isSamePlace(human)) return false;
		if (!human.isSettler() || !human.getLevel().isServer()) return false;
		ServerSettlementData settlement = human.getSettlerSettlementServerData();
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null) return false;
		SettlementLevelType sourceType = domain.getLevelType(human.getLevel().getIdentifier());
		SettlementLevelType targetType = domain.getLevelType(target.getLevel().getIdentifier());
		return sourceType != null && targetType != null && sourceType != targetType;
	}

	private static final class FollowState {
		final Mob target;
		SettlementLevelType targetType;
		SettlementCrossLevelRoute route;
		MoveToTileAITask pathTask;
		FollowTravelNode travelNode;
		long nextRouteSearchTime;

		FollowState(Mob target) {
			this.target = target;
		}

		@SuppressWarnings({"rawtypes", "unchecked"})
		void ensureTravelNode(HumanMob human, Blackboard<?> blackboard) {
			if (travelNode != null) return;
			travelNode = new FollowTravelNode();
			travelNode.makeRoot(human, (Blackboard)blackboard);
		}
	}

	private static final class FollowTravelNode extends AINode<HumanMob> {
		@Override
		protected void onRootSet(AINode<HumanMob> root, HumanMob mob, Blackboard<HumanMob> blackboard) {
		}

		@Override
		public void init(HumanMob mob, Blackboard<HumanMob> blackboard) {
		}

		@Override
		public AINodeResult tick(HumanMob mob, Blackboard<HumanMob> blackboard) {
			return AINodeResult.RUNNING;
		}
	}

	private static final class AttackState {
		final Mob target;
		SettlementLevelType targetType;
		SettlementCrossLevelRoute route;
		MoveToTileAITask pathTask;
		AttackTravelNode travelNode;
		long nextRouteSearchTime;

		AttackState(Mob target) {
			this.target = target;
		}

		void ensureTravelNode(HumanMob human, Blackboard blackboard) {
			if (travelNode != null) return;
			travelNode = new AttackTravelNode();
			travelNode.makeRoot(human, blackboard);
		}
	}

	private static final class AttackTravelNode extends AINode {
		@Override
		protected void onRootSet(AINode root, Mob mob, Blackboard blackboard) {
		}

		@Override
		public void init(Mob mob, Blackboard blackboard) {
		}

		@Override
		public AINodeResult tick(Mob mob, Blackboard blackboard) {
			return AINodeResult.RUNNING;
		}
	}

	private static final class GuardState {
		final LevelIdentifier targetLevel;
		SettlementLevelType targetType;
		SettlementCrossLevelRoute route;
		long nextRouteSearchTime;

		GuardState(LevelIdentifier targetLevel) {
			this.targetLevel = targetLevel;
		}
	}
}
