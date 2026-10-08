package opusliews.path;

import java.awt.Point;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import necesse.entity.mobs.PathDoorOption;
import necesse.entity.mobs.ai.path.SubRegionPathResult;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.regionSystem.Region;
import necesse.level.maps.regionSystem.SubRegion;
import opusliews.logging.Logging;

/**
 * Narrow fix for a vanilla PathDoorOption adjacent-tile cache inefficiency.
 *
 * Vanilla canMoveToTile(..., true) requires every adjacent region to have a
 * cached result before it will trust an already-cached negative result for the
 * target region. Solid/otherwise INVALID adjacent regions can never be entered
 * by RegionPathfinding, so they never receive a useful cache entry. Their
 * permanent cache MISS can therefore force the same failed region search to be
 * repeated for every nearby job target (for example crops beside a fence).
 *
 * This helper does not add any cache of its own. It only allows the original
 * method to be skipped with false when vanilla already has negative cache
 * entries for the target and every adjacent region that the current
 * PathDoorOption could actually traverse. INVALID adjacent regions are ignored.
 * Any missing/unknown/pathable cache state falls back to the untouched vanilla
 * method.
 */
public final class PathDoorAdjacentRegionCacheFix {
    private static final Field PATH_CACHE_FIELD = findField(PathDoorOption.class, "canRegionPathTo");
    private static volatile Field cacheCanPathField;
    private static volatile boolean reflectionFailureLogged;

    private PathDoorAdjacentRegionCacheFix() {
    }

    /**
     * @return true only when it is safe to skip vanilla canMoveToTile and return false.
     */
    public static boolean canSkipAsUnreachable(
            PathDoorOption option,
            int fromTileX,
            int fromTileY,
            int toTileX,
            int toTileY,
            boolean acceptAdjacentTiles
    ) {
        if (!acceptAdjacentTiles || option == null || option.level == null || PATH_CACHE_FIELD == null) {
            if (PATH_CACHE_FIELD == null) {
                logReflectionFailureOnce("PathDoorOption.canRegionPathTo field unavailable", null);
            }
            return false;
        }

        try {
            Level level = option.level;
            int fromRegionID = level.regionManager.getRegionIDByTile(fromTileX, fromTileY);
            int toRegionID = level.regionManager.getRegionIDByTile(toTileX, toTileY);

            // Preserve all vanilla special cases exactly.
            if (fromRegionID == 0 || toRegionID == 0 || fromRegionID == toRegionID) {
                return false;
            }

            Object outerValue = PATH_CACHE_FIELD.get(option);
            if (!(outerValue instanceof Map)) {
                return false;
            }

            Map outer = (Map)outerValue;
            Object fromValue = outer.get(Integer.valueOf(fromRegionID));
            if (!(fromValue instanceof Map)) {
                return false;
            }

            Map fromCache = (Map)fromValue;

            // The optimization is only valid after vanilla has already proven
            // the actual target region unreachable.
            int targetState = getCacheState(fromCache.get(Integer.valueOf(toRegionID)));
            if (targetState != CacheState.FALSE) {
                return false;
            }

            GameObject targetObject = level.getObject(toTileX, toTileY);
            if (targetObject == null) {
                return false;
            }

            Collection adjacentTiles = targetObject
                    .getMultiTile(level, 0, toTileX, toTileY)
                    .getAdjacentTiles(toTileX, toTileY, true);
            if (adjacentTiles == null) {
                return false;
            }

            int ignoredInvalidRegions = 0;
            Iterator iterator = adjacentTiles.iterator();
            while (iterator.hasNext()) {
                Object next = iterator.next();
                if (!(next instanceof Point)) {
                    return false;
                }

                Point tile = (Point)next;
                Region region = level.regionManager.getRegionByTile(tile.x, tile.y, false);
                if (region == null) {
                    return false;
                }

                SubRegion subRegion = region.subRegionData.getSubRegionByRegion(
                        tile.x - region.tileXOffset,
                        tile.y - region.tileYOffset);
                if (subRegion == null) {
                    return false;
                }

                int adjacentRegionID = subRegion.getRegionID();
                if (adjacentRegionID == 0) {
                    return false;
                }

                // Vanilla immediately accepts this case before consulting path
                // validity, so never override it.
                if (adjacentRegionID == fromRegionID) {
                    return false;
                }

                SubRegionPathResult pathResult = option.canPathThrough(subRegion);
                if (pathResult == null) {
                    return false;
                }

                // This is the vanilla inefficiency: an INVALID region can never
                // be reached by RegionPathfinding, therefore a missing path-cache
                // entry for it cannot represent an untested valid destination.
                if (pathResult == SubRegionPathResult.INVALID) {
                    ignoredInvalidRegions++;
                    continue;
                }

                // VALID and CHECK_EACH_TILE remain conservative. Unless vanilla
                // already has a negative entry for them, let the original method
                // perform its normal search/checks.
                int adjacentState = getCacheState(fromCache.get(Integer.valueOf(adjacentRegionID)));
                if (adjacentState != CacheState.FALSE) {
                    return false;
                }
            }

            // If there were no INVALID adjacent regions, vanilla's normal
            // foundAllCaches logic should already return false without searching.
            // Avoid changing behavior where there is nothing to fix.
            if (ignoredInvalidRegions <= 0) {
                return false;
            }

            return true;
        }
        catch (Throwable failure) {
            logReflectionFailureOnce("PathDoor adjacent-cache fix failed open", failure);
            return false;
        }
    }

    private static int getCacheState(Object cache) {
        if (cache == null) {
            return CacheState.MISSING;
        }

        try {
            Field field = cacheCanPathField;
            if (field == null || field.getDeclaringClass() != cache.getClass()) {
                field = findField(cache.getClass(), "canPath");
                if (field == null) {
                    return CacheState.UNKNOWN;
                }
                cacheCanPathField = field;
            }
            return field.getBoolean(cache) ? CacheState.TRUE : CacheState.FALSE;
        }
        catch (Throwable failure) {
            logReflectionFailureOnce("PathDoor cache canPath field unavailable", failure);
            return CacheState.UNKNOWN;
        }
    }

    private static Field findField(Class<?> type, String name) {
        if (type == null || name == null) {
            return null;
        }
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private static void logReflectionFailureOnce(String message, Throwable failure) {
        if (reflectionFailureLogged) {
            return;
        }
        synchronized (PathDoorAdjacentRegionCacheFix.class) {
            if (reflectionFailureLogged) {
                return;
            }
            reflectionFailureLogged = true;

            if (Logging.logEnabled) {
                String type = failure == null ? "none" : failure.getClass().getName();
                String detail = failure == null ? "none" : String.valueOf(failure.getMessage());
                Logging.logMessage("PathDoorAdjacentRegionCacheFix: " + message
                        + " type=" + type + " message=" + detail
                        + " action=fallback-to-vanilla");
            }
        }
    }

    private static final class CacheState {
        private static final int MISSING = -1;
        private static final int FALSE = 0;
        private static final int TRUE = 1;
        private static final int UNKNOWN = 2;

        private CacheState() {
        }
    }
}
