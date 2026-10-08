package opusliews.patches;

import java.awt.Rectangle;
import java.util.List;
import java.util.stream.Collectors;

import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.AINode;
import necesse.entity.mobs.ai.behaviourTree.AINodeResult;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.decorators.MoveTaskAINode;
import necesse.entity.mobs.ai.behaviourTree.util.MoveToTileAITask;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.CachedSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementBoundsManager;
import opusliews.breaching.ZombieBreaching;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;

/** A last-resort walking goal: never competes with combat, smell, or door breaching. */
public class ZombieSettlementAttractionPatch {
    public static class AttractionNode extends MoveTaskAINode {
        private long nextSearch;
        private long retryAfter;
        private Rectangle destinationBounds;
        private int destinationX;
        private int destinationY;

        @Override protected void onRootSet(AINode root, Mob mob, Blackboard blackboard) { }
        @Override public void init(Mob mob, Blackboard blackboard) {
            nextSearch = mob.getTime() + Math.floorMod(mob.getUniqueID(), 40) * 50L;
        }
        @Override protected void onInterruptRunning(Mob mob, Blackboard blackboard) {
            super.onInterruptRunning(mob, blackboard);
            reset(mob, blackboard);
        }
        private void reset(Mob mob, Blackboard blackboard) {
            clearTask();
            destinationBounds = null;
            if (blackboard.mover.isCurrentlyMovingFor(this)) blackboard.mover.stopMoving(mob);
        }
        @Override public AINodeResult tick(Mob mob, Blackboard blackboard) {
            Level level = mob.getLevel();
            if (!ZombieBreaching.isZombie(mob) || level == null || !level.isServer() || level.getServer() == null) {
                reset(mob, blackboard);
                return AINodeResult.FAILURE;
            }
            if (blackboard.getObject(Mob.class, "currentTarget") != null || blackboard.getObject(Mob.class, "chaserTarget") != null
                    || blackboard.getObject(Mob.class, ZombieBreaching.passiveTargetKey) != null) {
                reset(mob, blackboard);
                return AINodeResult.FAILURE;
            }
            ZombieBreaching.State state = ZombieBreaching.getState(mob);
            if (state.currentTarget != null || state.rememberedDoor != null || state.activeBreachTile != null) {
                reset(mob, blackboard);
                return AINodeResult.FAILURE;
            }
            long now = mob.getTime();
            if (now < retryAfter) return AINodeResult.FAILURE;
            if (destinationBounds != null && destinationBounds.contains(mob.getTileX(), mob.getTileY())) {
                reset(mob, blackboard);
                return AINodeResult.FAILURE;
            }
            if (now >= nextSearch) {
                nextSearch = now + 2000L;
                Rectangle found = findNearbySettlement(level, mob.getTileX(), mob.getTileY());
                if (found == null) {
                    reset(mob, blackboard);
                    return AINodeResult.FAILURE;
                }
                if (destinationBounds == null || !destinationBounds.equals(found)) {
                    destinationBounds = found;
                    clearTask();
                    // Aim toward a random position inside the bounds, not necessarily the center.
                    chooseDestination(mob);
                }
            }
            return destinationBounds == null ? AINodeResult.FAILURE : super.tick(mob, blackboard);
        }
        private void chooseDestination(Mob mob) {
            if (destinationBounds == null) return;
            java.util.Random random = new java.util.Random((long) mob.getUniqueID() ^ mob.getTime());
            destinationX = destinationBounds.x + random.nextInt(destinationBounds.width);
            destinationY = destinationBounds.y + random.nextInt(destinationBounds.height);
            if (Logging.logEnabled) Logging.logMessage("[ZombieAttraction] Moving zombie=" + mob.getUniqueID() + " toward=" + destinationX + "," + destinationY);
        }
        @Override public AINodeResult tickNode(Mob mob, Blackboard blackboard) {
            if (destinationBounds == null) return AINodeResult.FAILURE;
            if (blackboard.mover.isCurrentlyMovingFor(this)) return AINodeResult.RUNNING;
            return moveToTileTask(destinationX, destinationY, null, pathObject -> {
                MoveToTileAITask.AIPathResult path = (MoveToTileAITask.AIPathResult) pathObject;
                boolean moving = path.moveIfWithin(-1, 0, null);
                return moving ? AINodeResult.RUNNING : AINodeResult.FAILURE;
            });
        }
        @Override public AINodeResult onTaskFailed(Mob mob, Blackboard blackboard) {
            if (Logging.logEnabled) Logging.logMessage("[ZombieAttraction] Could not path zombie=" + mob.getUniqueID() + " toward=" + destinationX + "," + destinationY);
            reset(mob, blackboard);
            retryAfter = mob.getTime() + 5000L;
            nextSearch = retryAfter;
            return AINodeResult.FAILURE;
        }
    }

    public static Rectangle findNearbySettlement(Level level, int x, int y) {
        try {
            if (level == null || level.getServer() == null) return null;
            necesse.engine.util.LevelIdentifier surface = SettlementMultiLevelSystem.getSurfaceIdentifier(level.getIdentifier());
            if (surface == null) return null;
            SettlementsWorldData worldData = SettlementsWorldData.getSettlementsData(level.getServer());
            List<CachedSettlementData> candidates = (List<CachedSettlementData>) worldData.streamSettlements()
                    .filter(data -> data != null && data.getOwnerAuth() != -1L && surface.equals(data.levelIdentifier))
                    .collect(Collectors.toList());
            Rectangle closest = null;
            long closestDistance = Long.MAX_VALUE;
            for (CachedSettlementData candidate : candidates) {
                Rectangle bounds = SettlementBoundsManager.getTileRectangleFromTier(candidate.getTileX(), candidate.getTileY(), candidate.getFlagTier());
                if (bounds.contains(x, y)) continue;
                int margin = (Math.max(0, candidate.getFlagTier()) + 1) * 16;
                Rectangle expanded = new Rectangle(bounds.x - margin, bounds.y - margin, bounds.width + 2 * margin, bounds.height + 2 * margin);
                if (!expanded.contains(x, y)) continue;
                long dx = (long) x - (bounds.x + bounds.width / 2);
                long dy = (long) y - (bounds.y + bounds.height / 2);
                long distance = dx * dx + dy * dy;
                if (distance < closestDistance) { closestDistance = distance; closest = bounds; }
            }
            return closest;
        } catch (Exception e) {
            if (Logging.logEnabled) Logging.logMessage("[ZombieAttraction] Settlement discovery failed level=" + (level == null ? "null" : level.getIdentifier()) + " tile=" + x + "," + y + " error=" + e);
            return null;
        }
    }
}
