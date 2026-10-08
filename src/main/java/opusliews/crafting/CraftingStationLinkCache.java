package opusliews.crafting;

import necesse.entity.objectEntity.ObjectEntity;
import necesse.level.maps.Level;
import opusliews.logging.Logging;
import opusliews.object.CraftingTaskBoardObjectEntity;
import opusliews.object.DynamicCraftingStationObjectEntity;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Transient reverse-link index for dynamic crafting stations and task boards.
 *
 * Authoritative link state continues to live on the object entities and in their
 * normal save/content packets. This cache exists only to turn "is this linked
 * object still valid?" from per-tick polling into event-driven revalidation.
 * Nothing in this class is saved or synchronized to clients.
 */
public final class CraftingStationLinkCache {
    private static final Object LOCK = new Object();
    private static final WeakHashMap<Level, LevelLinks> LEVELS = new WeakHashMap<>();

    private CraftingStationLinkCache() {
    }

    public static void refreshStation(DynamicCraftingStationObjectEntity station) {

        if (station == null || station.getLevel() == null || !station.getLevel().isServer()) return;
        Level level = station.getLevel();
        long stationKey = key(station.tileX, station.tileY);

        HashSet<Long> targets = new HashSet<>();
        addPoints(targets, station.getInputStorages());
        addPoints(targets, station.getOutputStorages());
        addPoints(targets, station.getLinkedForges());
        Point board = station.getTaskBoard();
        if (board != null) targets.add(key(board.x, board.y));

        synchronized (LOCK) {
            LevelLinks links = LEVELS.computeIfAbsent(level, ignored -> new LevelLinks());
            removeStationLocked(links, stationKey);
            if (!targets.isEmpty()) {
                links.stationTargets.put(stationKey, targets);
                for (Long target : targets) {
                    links.stationsByTarget.computeIfAbsent(target, ignored -> new HashSet<>()).add(stationKey);
                }
            }
        }

    }

    public static void removeStation(DynamicCraftingStationObjectEntity station) {
        if (station == null || station.getLevel() == null || !station.getLevel().isServer()) return;
        synchronized (LOCK) {
            LevelLinks links = LEVELS.get(station.getLevel());
            if (links != null) removeStationLocked(links, key(station.tileX, station.tileY));
        }
    }

    public static void refreshBoard(CraftingTaskBoardObjectEntity board) {

        if (board == null || board.getLevel() == null || !board.getLevel().isServer()) return;
        Level level = board.getLevel();
        long boardKey = key(board.tileX, board.tileY);
        Point station = board.getLinkedStation();

        synchronized (LOCK) {
            LevelLinks links = LEVELS.computeIfAbsent(level, ignored -> new LevelLinks());
            removeBoardLocked(links, boardKey);
            if (station != null) {
                long stationKey = key(station.x, station.y);
                links.boardStation.put(boardKey, stationKey);
                links.boardsByStation.computeIfAbsent(stationKey, ignored -> new HashSet<>()).add(boardKey);
            }
        }

    }

    public static void removeBoard(CraftingTaskBoardObjectEntity board) {
        if (board == null || board.getLevel() == null || !board.getLevel().isServer()) return;
        synchronized (LOCK) {
            LevelLinks links = LEVELS.get(board.getLevel());
            if (links != null) removeBoardLocked(links, key(board.tileX, board.tileY));
        }
    }

    /**
     * Called after a level object/object-entity at a tile changes. Only stations
     * and boards that actually reference that tile are touched.
     */
    public static void onObjectChanged(Level level, int tileX, int tileY) {
        try {
            if (level == null || !level.isServer() || !level.isLoadingComplete()) return;
            long changedKey = key(tileX, tileY);
            ArrayList<Long> stationKeys = new ArrayList<>();
            ArrayList<Long> boardKeys = new ArrayList<>();

            synchronized (LOCK) {
                LevelLinks links = LEVELS.get(level);
                if (links == null) return;

                Set<Long> stations = links.stationsByTarget.get(changedKey);
                if (stations != null) stationKeys.addAll(stations);
                Set<Long> boards = links.boardsByStation.get(changedKey);
                if (boards != null) boardKeys.addAll(boards);
            }

            for (Long stationKey : stationKeys) {
                int x = x(stationKey);
                int y = y(stationKey);
                ObjectEntity entity = level.entityManager.getObjectEntity(x, y);
                if (entity instanceof DynamicCraftingStationObjectEntity) {
                    ((DynamicCraftingStationObjectEntity)entity).onLinkedObjectChanged(tileX, tileY);
                } else {
                    removeStationAt(level, stationKey);
                }
            }

            for (Long boardKey : boardKeys) {
                int x = x(boardKey);
                int y = y(boardKey);
                ObjectEntity entity = level.entityManager.getObjectEntity(x, y);
                if (entity instanceof CraftingTaskBoardObjectEntity) {
                    ((CraftingTaskBoardObjectEntity)entity).onLinkedStationObjectChanged(tileX, tileY);
                } else {
                    removeBoardAt(level, boardKey);
                }
            }

            // If the changed tile itself used to be a registered station/board and
            // no longer is one, discard that stale reverse-index entry immediately.
            ObjectEntity changed = level.entityManager.getObjectEntity(tileX, tileY);
            if (!(changed instanceof DynamicCraftingStationObjectEntity)) removeStationAt(level, changedKey);
            if (!(changed instanceof CraftingTaskBoardObjectEntity)) removeBoardAt(level, changedKey);
        } catch (Throwable error) {
            // Cache failure must never break object placement/destruction. The
            // authoritative saved link state remains untouched and will be rebuilt
            // by the next station/board initialization or edit.
            if (Logging.logEnabled) {
                Logging.logMessage("[CraftingLinkCache] Object-change notification failed level="
                        + level.getIdentifier() + " pos=" + tileX + "," + tileY
                        + " error=" + error.getClass().getSimpleName() + ": " + error.getMessage());
            }
        }
    }

    private static void addPoints(Set<Long> out, Iterable<Point> points) {
        if (points == null) return;
        for (Point point : points) {
            if (point != null) out.add(key(point.x, point.y));
        }
    }

    private static void removeStationAt(Level level, long stationKey) {
        synchronized (LOCK) {
            LevelLinks links = LEVELS.get(level);
            if (links != null) removeStationLocked(links, stationKey);
        }
    }

    private static void removeBoardAt(Level level, long boardKey) {
        synchronized (LOCK) {
            LevelLinks links = LEVELS.get(level);
            if (links != null) removeBoardLocked(links, boardKey);
        }
    }

    private static void removeStationLocked(LevelLinks links, long stationKey) {
        Set<Long> oldTargets = links.stationTargets.remove(stationKey);
        if (oldTargets == null) return;
        for (Long target : oldTargets) {
            HashSet<Long> stations = links.stationsByTarget.get(target);
            if (stations == null) continue;
            stations.remove(stationKey);
            if (stations.isEmpty()) links.stationsByTarget.remove(target);
        }
    }

    private static void removeBoardLocked(LevelLinks links, long boardKey) {
        Long oldStation = links.boardStation.remove(boardKey);
        if (oldStation == null) return;
        HashSet<Long> boards = links.boardsByStation.get(oldStation);
        if (boards == null) return;
        boards.remove(boardKey);
        if (boards.isEmpty()) links.boardsByStation.remove(oldStation);
    }

    private static long key(int x, int y) {
        return ((long)x << 32) ^ (y & 0xffffffffL);
    }

    private static int x(long key) {
        return (int)(key >> 32);
    }

    private static int y(long key) {
        return (int)key;
    }

    private static final class LevelLinks {
        private final Map<Long, HashSet<Long>> stationsByTarget = new HashMap<>();
        private final Map<Long, HashSet<Long>> stationTargets = new HashMap<>();
        private final Map<Long, HashSet<Long>> boardsByStation = new HashMap<>();
        private final Map<Long, Long> boardStation = new HashMap<>();
    }
}
