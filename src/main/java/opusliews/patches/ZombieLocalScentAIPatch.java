package opusliews.patches;

import java.awt.Point;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.AINode;
import necesse.entity.mobs.ai.behaviourTree.AINodeResult;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.decorators.MoveTaskAINode;
import necesse.entity.mobs.ai.behaviourTree.leaves.TargetFinderAINode;
import necesse.entity.mobs.ai.behaviourTree.util.MoveToTileAITask;
import necesse.level.gameObject.DoorObject;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import opusliews.breaching.ZombieBreaching;
import opusliews.logging.Logging;

/** Low-priority investigation of a remembered tile, not an aggro target. */
public final class ZombieLocalScentAIPatch {
    private ZombieLocalScentAIPatch() { }

    public static final class ScentNode extends MoveTaskAINode {
        private final TargetFinderAINode vanillaFinder;
        private long nextScan;
        private long retryAfter;
        private Point lastGoal;

        public ScentNode(TargetFinderAINode vanillaFinder) { this.vanillaFinder = vanillaFinder; }
        @Override protected void onRootSet(AINode root, Mob mob, Blackboard blackboard) { }
        @Override public void init(Mob mob, Blackboard blackboard) {
            nextScan = mob.getTime() + Math.floorMod(mob.getUniqueID(), 40) * 50L;
        }
        @Override protected void onInterruptRunning(Mob mob, Blackboard blackboard) {
            super.onInterruptRunning(mob, blackboard);
            forget(mob, blackboard, "interrupted");
        }

        private void forget(Mob mob, Blackboard board, String reason) {
            ZombieBreaching.State state = ZombieBreaching.getState(mob);
            if (state.scentLocation != null && Logging.logEnabled)
                Logging.logMessage("[ZombieScent] Forget mob=" + mob.getUniqueID() + " reason=" + reason);
            state.clearScent();
            clearTask();
            lastGoal = null;
            if (board.mover.isCurrentlyMovingFor(this)) board.mover.stopMoving(mob);
        }

        @Override public AINodeResult tick(Mob mob, Blackboard board) {
            if (!ZombieBreaching.isZombie(mob) || mob.getLevel() == null || !mob.getLevel().isServer()) {
                forget(mob, board, "invalid");
                return AINodeResult.FAILURE;
            }
            ZombieBreaching.State state = ZombieBreaching.getState(mob);
            try {
                if (board.getLastHits().iterator().hasNext()) {
                    forget(mob, board, "attacked");
                    retryAfter = mob.getTime() + 2000L;
                    return AINodeResult.FAILURE;
                }
                // Once actual damage to the barrier begins, scent stays committed until breach or retaliation.
                if (!state.scentCommitted) {
                    if (board.getObject(Mob.class, vanillaFinder.currentTargetKey) == null)
                        vanillaFinder.tickTargetFinder(mob, board);
                    if (board.getObject(Mob.class, vanillaFinder.currentTargetKey) != null
                            || board.getObject(Mob.class, "chaserTarget") != null
                            || board.getObject(Mob.class, ZombieBreaching.passiveTargetKey) != null
                            || state.currentTarget != null || state.rememberedDoor != null || state.activeBreachTile != null
                            || hasNearbyNormalPassiveTarget(mob)) {
                        forget(mob, board, "normal-target");
                        return AINodeResult.FAILURE;
                    }
                }
                Point destination = state.scentLocation;
                if (destination == null) {
                    if (mob.getTime() < nextScan || mob.getTime() < retryAfter) return AINodeResult.FAILURE;
                    nextScan = mob.getTime() + 2000L;
                    destination = findHumanTile(mob);
                    if (destination == null) return AINodeResult.FAILURE;
                    if (state.normalPathDoorOption.canMoveToTile(mob.getTileX(), mob.getTileY(), destination.x, destination.y, true)) {
                        state.scentBarrier = null;
                    } else {
                        Point barrier = findBreachableBarrier(mob, destination, state);
                        if (barrier == null) {
                            retryAfter = mob.getTime() + 4000L;
                            return AINodeResult.FAILURE;
                        }
                        state.scentBarrier = barrier;
                        state.pathDoorOption.invalidateCache();
                    }
                    state.scentLocation = destination;
                    if (Logging.logEnabled) Logging.logMessage("[ZombieScent] Acquired mob=" + mob.getUniqueID()
                            + " tile=" + destination + " barrier=" + state.scentBarrier);
                }
                if (state.scentBarrier != null) {
                    Point barrier = state.scentBarrier;
                    GameObject object = mob.getLevel().getObject(barrier.x, barrier.y);
                    if ((!object.isFence && !(object instanceof DoorObject)) || !ZombieBreaching.isBreakableTier(object)) {
                        state.scentBarrier = null;
                        state.scentCommitted = false;
                        state.pathDoorOption.invalidateCache();
                        clearTask();
                        lastGoal = null;
                        if (Logging.logEnabled) Logging.logMessage("[ZombieScent] Barrier removed mob=" + mob.getUniqueID() + " tile=" + barrier);
                    }
                }
                if (Math.abs(mob.getTileX() - destination.x) <= 1 && Math.abs(mob.getTileY() - destination.y) <= 1
                        && state.scentBarrier == null) {
                    forget(mob, board, "reached-location");
                    retryAfter = mob.getTime() + 2500L;
                    return AINodeResult.FAILURE;
                }
                return super.tick(mob, board);
            } catch (Exception error) {
                if (Logging.logEnabled) Logging.logMessage("[ZombieScent] Tick failed mob=" + mob.getUniqueID() + " error=" + error);
                forget(mob, board, "exception");
                retryAfter = mob.getTime() + 5000L;
                return AINodeResult.FAILURE;
            }
        }

        @Override public AINodeResult tickNode(Mob mob, Blackboard board) {
            ZombieBreaching.State state = ZombieBreaching.getState(mob);
            Point destination = state.scentLocation;
            if (destination == null) return AINodeResult.FAILURE;
            if (board.mover.isCurrentlyMovingFor(this)) return AINodeResult.RUNNING;
            if (lastGoal == null || !lastGoal.equals(destination)) {
                clearTask();
                lastGoal = new Point(destination);
            }
            return moveToTileTask(destination.x, destination.y, null, pathObject -> {
                MoveToTileAITask.AIPathResult path = (MoveToTileAITask.AIPathResult)pathObject;
                return path.moveIfWithin(-1, 1, null) ? AINodeResult.RUNNING : AINodeResult.FAILURE;
            });
        }
        @Override public AINodeResult onTaskFailed(Mob mob, Blackboard board) {
            forget(mob, board, "no-path");
            retryAfter = mob.getTime() + 5000L;
            if (Logging.logEnabled) Logging.logMessage("[ZombieScent] Path failed mob=" + mob.getUniqueID());
            return AINodeResult.FAILURE;
        }

        private Point findHumanTile(Mob zombie) {
            Level level = zombie.getLevel();
            int rx = Math.floorDiv(zombie.getTileX(), 16);
            int ry = Math.floorDiv(zombie.getTileY(), 16);
            Point best = null;
            long score = Long.MAX_VALUE;
            for (Mob candidate : level.entityManager.mobs.getInRegionByTileRange(zombie.getTileX(), zombie.getTileY(), 32)) {
                if (candidate == zombie || candidate.removed() || candidate.getHealth() <= 0 || !candidate.isVisible()
                        || !(candidate.isHuman || candidate.isPlayer) || candidate.isSameTeam(zombie)
                        || !zombie.canTarget(candidate)) continue;
                if (Math.abs(Math.floorDiv(candidate.getTileX(), 16) - rx) > 1
                        || Math.abs(Math.floorDiv(candidate.getTileY(), 16) - ry) > 1) continue;
                long dx = candidate.getTileX() - zombie.getTileX();
                long dy = candidate.getTileY() - zombie.getTileY();
                long distance = dx * dx + dy * dy;
                if (distance < score) { score = distance; best = new Point(candidate.getTileX(), candidate.getTileY()); }
            }
            return best;
        }

        private boolean hasNearbyNormalPassiveTarget(Mob zombie) {
            for (Mob mob : zombie.getLevel().entityManager.mobs.getInRegionByTileRange(zombie.getTileX(), zombie.getTileY(), 12)) {
                if (ZombiePassiveAggroPatch.isModdedPassiveAggroTarget(zombie, mob)
                        && zombie.getDistance(mob) < vanillaFinder.distance.searchDistance
                        && zombie.estimateCanMoveTo(mob.getTileX(), mob.getTileY(), true)) return true;
            }
            return false;
        }

        private Point findBreachableBarrier(Mob zombie, Point destination, ZombieBreaching.State state) {
            Level level = zombie.getLevel();
            Point best = null;
            double bestCost = Double.MAX_VALUE;
            int x0 = Math.max(1, Math.min(zombie.getTileX(), destination.x) - 5);
            int y0 = Math.max(1, Math.min(zombie.getTileY(), destination.y) - 5);
            int x1 = Math.min(level.tileWidth - 2, Math.max(zombie.getTileX(), destination.x) + 5);
            int y1 = Math.min(level.tileHeight - 2, Math.max(zombie.getTileY(), destination.y) + 5);
            int checked = 0;
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) {
                GameObject object = level.getObject(x, y);
                if ((!object.isFence && !(object instanceof DoorObject)) || !ZombieBreaching.isBreakableTier(object)) continue;
                if (++checked > 48) return best;
                boolean connects = false;
                int[][] dirs = {{1,0},{0,1}};
                for (int[] dir : dirs) {
                    for (int sign = -1; sign <= 1; sign += 2) {
                        int ax = x + dir[0] * sign, ay = y + dir[1] * sign;
                        int bx = x - dir[0] * sign, by = y - dir[1] * sign;
                        if (state.normalPathDoorOption.canMoveToTile(zombie.getTileX(), zombie.getTileY(), ax, ay, false)
                                && state.normalPathDoorOption.canMoveToTile(bx, by, destination.x, destination.y, true)) {
                            connects = true; break;
                        }
                    }
                    if (connects) break;
                }
                if (!connects) continue;
                // Gate and fence share the same cost calculation; closer, softer obstructions win.
                double cost = Math.hypot(x - zombie.getTileX(), y - zombie.getTileY())
                        + Math.hypot(x - destination.x, y - destination.y)
                        + ZombieBreaching.getTier(object).getTier() * 8.0;
                if (cost < bestCost) { bestCost = cost; best = new Point(x, y); }
            }
            return best;
        }
    }
}
