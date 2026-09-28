package opusliews.multilevelsettlement;

import necesse.engine.util.GameMath;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PathDoorOption;
import necesse.level.maps.Level;
import opusliews.logging.Logging;

import java.util.List;

public final class SettlementCrossLevelRouting {
	private SettlementCrossLevelRouting() {
	}

	public static SettlementCrossLevelRoute findBestRoute(Mob mob, SettlementLevelDomain domain, SettlementLevelPosition target, boolean acceptAdjacentTarget) {
		return findBestRouteInternal(mob, domain, target, acceptAdjacentTarget, SettlementRouteRestriction.ALLOW_ALL, true);
	}

	public static SettlementCrossLevelRoute findBestRoute(Mob mob, SettlementLevelDomain domain, SettlementLevelPosition target, boolean acceptAdjacentTarget, SettlementRouteRestriction restriction) {
		return findBestRouteInternal(mob, domain, target, acceptAdjacentTarget, restriction, true);
	}

	public static SettlementCrossLevelRoute findBestRouteQuiet(Mob mob, SettlementLevelDomain domain, SettlementLevelPosition target, boolean acceptAdjacentTarget) {
		return findBestRouteInternal(mob, domain, target, acceptAdjacentTarget, SettlementRouteRestriction.ALLOW_ALL, false);
	}

	public static SettlementCrossLevelRoute findBestRouteQuiet(Mob mob, SettlementLevelDomain domain, SettlementLevelPosition target, boolean acceptAdjacentTarget, SettlementRouteRestriction restriction) {
		return findBestRouteInternal(mob, domain, target, acceptAdjacentTarget, restriction, false);
	}

	private static SettlementCrossLevelRoute findBestRouteInternal(Mob mob, SettlementLevelDomain domain, SettlementLevelPosition target, boolean acceptAdjacentTarget, SettlementRouteRestriction restriction, boolean logDetails) {
		if (mob == null || domain == null || target == null) {
			if (logDetails && Logging.logEnabled) Logging.logMessage("[SettlementRouting] Route rejected due to null argument mob=" + mob + " domain=" + domain + " target=" + target);
			return null;
		}
		Level currentLevel = mob.getLevel();
		if (currentLevel == null || currentLevel.getServer() == null) {
			if (logDetails && Logging.logEnabled) Logging.logMessage("[SettlementRouting] Route rejected because mob has no server level mob=" + mob.getUniqueID());
			return null;
		}

		SettlementLevelType sourceType = domain.getLevelType(currentLevel.getIdentifier());
		SettlementLevelType targetType = domain.getLevelType(target.levelIdentifier);
		if (sourceType == null || targetType == null) {
			if (logDetails && Logging.logEnabled) Logging.logMessage("[SettlementRouting] Route rejected because source/target level is outside settlement domain mob=" + mob.getUniqueID() + " sourceLevel=" + currentLevel.getIdentifier() + " target=" + target + " domain=" + domain);
			return null;
		}
		if (!domain.isTileWithinBounds(target)) {
			if (logDetails && Logging.logEnabled) Logging.logMessage("[SettlementRouting] Route rejected because target is outside settlement bounds mob=" + mob.getUniqueID() + " target=" + target + " settlement=" + domain.getSettlementUniqueID());
			return null;
		}

		SettlementRouteRestriction effectiveRestriction = restriction == null ? SettlementRouteRestriction.ALLOW_ALL : restriction;
		if (!effectiveRestriction.isTileAllowed(target.levelIdentifier, target.tileX, target.tileY)) {
			if (logDetails && Logging.logEnabled) Logging.logMessage("[SettlementRouting] Route rejected because target is outside route restriction mob=" + mob.getUniqueID() + " target=" + target);
			return null;
		}

		SettlementLevelPosition source = new SettlementLevelPosition(currentLevel.getIdentifier(), mob.getTileX(), mob.getTileY());
		if (sourceType == targetType) {
			if (!canMoveOnLevel(mob, currentLevel, source.tileX, source.tileY, target.tileX, target.tileY, acceptAdjacentTarget)) {
				if (logDetails && Logging.logEnabled) Logging.logMessage("[SettlementRouting] Same-level route unreachable mob=" + mob.getUniqueID() + " source=" + source + " target=" + target);
				return null;
			}
			int distance = getTileDistance(source.tileX, source.tileY, target.tileX, target.tileY);
			return SettlementCrossLevelRoute.sameLevel(source, target, distance);
		}

		Level targetLevel = getOrLoadLevel(domain, targetType);
		if (targetLevel == null) {
			if (logDetails && Logging.logEnabled) Logging.logMessage("[SettlementRouting] Cross-level route rejected because target level could not be loaded mob=" + mob.getUniqueID() + " targetType=" + targetType + " settlement=" + domain.getSettlementUniqueID());
			return null;
		}
		targetLevel.regionManager.ensureTileIsLoaded(target.tileX, target.tileY);

		List<SettlementLadderLink> links = SettlementLadderSystem.getValidLinks(domain, true);
		if (links.isEmpty()) {
			if (logDetails && Logging.logEnabled) Logging.logMessage("[SettlementRouting] Cross-level route unavailable because settlement has no valid designated ladders mob=" + mob.getUniqueID() + " settlement=" + domain.getSettlementUniqueID() + " sourceType=" + sourceType + " targetType=" + targetType);
			return null;
		}

		SettlementCrossLevelRoute best = null;
		for (SettlementLadderLink link : links) {
			int sourceLadderX = link.getTileX(sourceType);
			int sourceLadderY = link.getTileY(sourceType);
			int targetLadderX = link.getTileX(targetType);
			int targetLadderY = link.getTileY(targetType);

			if (!canMoveOnLevel(mob, currentLevel, source.tileX, source.tileY, sourceLadderX, sourceLadderY, false)) {
				if (logDetails && Logging.logEnabled) Logging.logMessage("[SettlementRouting] Ladder candidate unreachable on source level mob=" + mob.getUniqueID() + " link=" + link + " source=" + source + " ladder=" + sourceLadderX + "," + sourceLadderY);
				continue;
			}
			if (!canMoveOnLevel(mob, targetLevel, targetLadderX, targetLadderY, target.tileX, target.tileY, acceptAdjacentTarget)) {
				if (logDetails && Logging.logEnabled) Logging.logMessage("[SettlementRouting] Ladder candidate cannot reach destination on target level mob=" + mob.getUniqueID() + " link=" + link + " ladder=" + targetLadderX + "," + targetLadderY + " target=" + target);
				continue;
			}

			int sourceDistance = getTileDistance(source.tileX, source.tileY, sourceLadderX, sourceLadderY);
			int targetDistance = getTileDistance(targetLadderX, targetLadderY, target.tileX, target.tileY);
			SettlementCrossLevelRoute candidate = SettlementCrossLevelRoute.crossLevel(source, target, link, sourceDistance, targetDistance);
			if (best == null || candidate.totalDistance < best.totalDistance) best = candidate;
		}

		if (best == null) {
			if (logDetails && Logging.logEnabled) Logging.logMessage("[SettlementRouting] No accessible designated ladder route found mob=" + mob.getUniqueID() + " source=" + source + " target=" + target + " candidateCount=" + links.size());
			return null;
		}

		if (logDetails && Logging.logEnabled) Logging.logMessage("[SettlementRouting] Selected cross-level route mob=" + mob.getStringID() + "#" + mob.getUniqueID() + " route=" + best);
		return best;
	}


	public static SettlementCrossLevelRoute findBestTransitionRoute(Mob mob, SettlementLevelDomain domain, SettlementLevelType targetType) {
		return findBestTransitionRouteInternal(mob, domain, targetType, SettlementRouteRestriction.ALLOW_ALL, true);
	}

	public static SettlementCrossLevelRoute findBestTransitionRoute(Mob mob, SettlementLevelDomain domain, SettlementLevelType targetType, SettlementRouteRestriction restriction) {
		return findBestTransitionRouteInternal(mob, domain, targetType, restriction, true);
	}

	public static SettlementCrossLevelRoute findBestTransitionRouteQuiet(Mob mob, SettlementLevelDomain domain, SettlementLevelType targetType) {
		return findBestTransitionRouteInternal(mob, domain, targetType, SettlementRouteRestriction.ALLOW_ALL, false);
	}

	public static SettlementCrossLevelRoute findBestTransitionRouteQuiet(Mob mob, SettlementLevelDomain domain, SettlementLevelType targetType, SettlementRouteRestriction restriction) {
		return findBestTransitionRouteInternal(mob, domain, targetType, restriction, false);
	}

	private static SettlementCrossLevelRoute findBestTransitionRouteInternal(Mob mob, SettlementLevelDomain domain, SettlementLevelType targetType, SettlementRouteRestriction restriction, boolean logDetails) {
		if (mob == null || domain == null || targetType == null) {
			if (logDetails && Logging.logEnabled) Logging.logMessage("[SettlementRouting] Transition route rejected due to null argument mob=" + mob + " domain=" + domain + " targetType=" + targetType);
			return null;
		}
		Level currentLevel = mob.getLevel();
		if (currentLevel == null || currentLevel.getServer() == null) {
			if (logDetails && Logging.logEnabled) Logging.logMessage("[SettlementRouting] Transition route rejected because mob has no server level mob=" + mob.getUniqueID());
			return null;
		}

		SettlementLevelType sourceType = domain.getLevelType(currentLevel.getIdentifier());
		if (sourceType == null || sourceType == targetType || domain.getLevelIdentifier(targetType) == null) return null;
		Level targetLevel = getOrLoadLevel(domain, targetType);
		if (targetLevel == null) {
			if (logDetails && Logging.logEnabled) Logging.logMessage("[SettlementRouting] Transition route rejected because target level could not be loaded mob=" + mob.getUniqueID() + " targetType=" + targetType);
			return null;
		}

		SettlementRouteRestriction effectiveRestriction = restriction == null ? SettlementRouteRestriction.ALLOW_ALL : restriction;
		SettlementLevelPosition source = new SettlementLevelPosition(currentLevel.getIdentifier(), mob.getTileX(), mob.getTileY());
		List<SettlementLadderLink> links = SettlementLadderSystem.getValidLinks(domain, true);
		SettlementCrossLevelRoute best = null;

		for (SettlementLadderLink link : links) {
			int sourceLadderX = link.getTileX(sourceType);
			int sourceLadderY = link.getTileY(sourceType);
			int targetLadderX = link.getTileX(targetType);
			int targetLadderY = link.getTileY(targetType);
			if (!effectiveRestriction.isTileAllowed(currentLevel.getIdentifier(), sourceLadderX, sourceLadderY)) {
				if (logDetails && Logging.logEnabled) Logging.logMessage("[SettlementRouting] Transition ladder rejected by source restriction mob=" + mob.getUniqueID() + " link=" + link + " sourceType=" + sourceType);
				continue;
			}
			if (!effectiveRestriction.isTileAllowed(targetLevel.getIdentifier(), targetLadderX, targetLadderY)) {
				if (logDetails && Logging.logEnabled) Logging.logMessage("[SettlementRouting] Transition ladder rejected by destination restriction mob=" + mob.getUniqueID() + " link=" + link + " targetType=" + targetType);
				continue;
			}
			if (!canMoveOnLevel(mob, currentLevel, source.tileX, source.tileY, sourceLadderX, sourceLadderY, false)) {
				if (logDetails && Logging.logEnabled) Logging.logMessage("[SettlementRouting] Transition ladder unreachable on source level mob=" + mob.getUniqueID() + " source=" + source + " link=" + link);
				continue;
			}

			SettlementLevelPosition target = new SettlementLevelPosition(targetLevel.getIdentifier(), targetLadderX, targetLadderY);
			int sourceDistance = getTileDistance(source.tileX, source.tileY, sourceLadderX, sourceLadderY);
			SettlementCrossLevelRoute candidate = SettlementCrossLevelRoute.crossLevel(source, target, link, sourceDistance, 0);
			if (best == null || candidate.totalDistance < best.totalDistance) best = candidate;
		}

		if (best == null) {
			if (logDetails && Logging.logEnabled) Logging.logMessage("[SettlementRouting] No accessible transition ladder found mob=" + mob.getUniqueID() + " sourceType=" + sourceType + " targetType=" + targetType + " candidateCount=" + links.size());
			return null;
		}
		if (logDetails && Logging.logEnabled) Logging.logMessage("[SettlementRouting] Selected preferred-level transition route mob=" + mob.getStringID() + "#" + mob.getUniqueID() + " route=" + best);
		return best;
	}

	public static boolean canReach(Mob mob, SettlementLevelDomain domain, SettlementLevelPosition target, boolean acceptAdjacentTarget) {
		return findBestRoute(mob, domain, target, acceptAdjacentTarget) != null;
	}

	public static int getTravelDistance(Mob mob, SettlementLevelDomain domain, SettlementLevelPosition target, boolean acceptAdjacentTarget) {
		SettlementCrossLevelRoute route = findBestRoute(mob, domain, target, acceptAdjacentTarget);
		return route == null ? Integer.MAX_VALUE : route.totalDistance;
	}

	private static Level getOrLoadLevel(SettlementLevelDomain domain, SettlementLevelType levelType) {
		Level level = domain.getLoadedLevel(levelType);
		if (level != null) return level;
		if (domain.getSettlement().getServer() == null) return null;
		if (Logging.logEnabled) Logging.logMessage("[SettlementRouting] Loading settlement level for route evaluation settlement=" + domain.getSettlementUniqueID() + " levelType=" + levelType + " identifier=" + domain.getLevelIdentifier(levelType));
		return domain.getSettlement().getServer().world.getLevel(domain.getLevelIdentifier(levelType));
	}

	private static boolean canMoveOnLevel(Mob mob, Level level, int fromTileX, int fromTileY, int toTileX, int toTileY, boolean acceptAdjacentTarget) {
		if (mob == null || level == null) return false;
		level.regionManager.ensureTileIsLoaded(fromTileX, fromTileY);
		level.regionManager.ensureTileIsLoaded(toTileX, toTileY);
		if (mob.getLevelCollisionFilter() == null || !mob.getLevelCollisionFilter().hasAdders()) return true;

		// On the mob's current level, use vanilla's own reachability estimate directly.
		// This preserves the mob's actual door/breaking movement rules and cached region paths.
		if (mob.getLevel() == level) {
			return mob.estimateCanMoveTo(toTileX, toTileY, acceptAdjacentTarget);
		}

		// For a remote level the mob cannot use estimateCanMoveTo directly because its
		// PathDoorOption belongs to the current level. Mirror the vanilla option onto
		// the remote level's RegionManager so we still use vanilla region reachability.
		PathDoorOption doorOption = getEquivalentDoorOption(mob.getPathDoorOption(), level);
		if (doorOption == null) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementRouting] Could not mirror path door option mob=" + mob.getUniqueID() + " level=" + level.getIdentifier());
			return false;
		}
		return doorOption.canMoveToTile(fromTileX, fromTileY, toTileX, toTileY, acceptAdjacentTarget);
	}

	private static PathDoorOption getEquivalentDoorOption(PathDoorOption source, Level targetLevel) {
		if (source == null || targetLevel == null) return null;
		if (source.level == targetLevel) return source;

		if ("CAN_OPEN_DOORS".equals(source.debugName)) return targetLevel.regionManager.CAN_OPEN_DOORS_OPTIONS;
		if ("CANNOT_OPEN_CAN_CLOSE_DOORS".equals(source.debugName)) return targetLevel.regionManager.CANNOT_OPEN_CAN_CLOSE_DOORS_OPTIONS;
		if ("CANNOT_PASS_DOORS".equals(source.debugName)) return targetLevel.regionManager.CANNOT_PASS_DOORS_OPTIONS;
		if ("CAN_PASS_DOORS".equals(source.debugName)) return targetLevel.regionManager.SUMMONED_MOB_OPTIONS;
		if ("CAN_BREAK_OBJECTS".equals(source.debugName)) return targetLevel.regionManager.CAN_BREAK_OBJECTS_OPTIONS;
		if ("BASIC".equals(source.debugName)) return targetLevel.regionManager.BASIC_DOOR_OPTIONS;
		return null;
	}

	private static int getTileDistance(int fromTileX, int fromTileY, int toTileX, int toTileY) {
		return (int)Math.ceil(GameMath.diagonalMoveDistance(fromTileX, fromTileY, toTileX, toTileY));
	}
}
