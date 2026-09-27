package opusliews.multilevelsettlement;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.stream.Stream;

import necesse.engine.util.GameRandom;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.job.EntityJobWorker;
import necesse.entity.mobs.job.JobTypeHandler;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.jobs.GoJoggingTileLevelJob;
import necesse.level.maps.levelData.settlementData.ZoneTester;

public final class SettlementJoggingSystem {
	private SettlementJoggingSystem() {
	}

	public static boolean isOutsideOrCave(Level level, int tileX, int tileY) {
		return level != null && (level.isCave || level.isOutside(tileX, tileY));
	}

	public static Point findNextTargetTile(Mob mob, ZoneTester zoneTester) {
		if (mob == null || mob.getLevel() == null) return null;
		Point pathOffset = mob.getPathMoveOffset();
		ArrayList<Point> tiles = new ArrayList<>(GoJoggingTileLevelJob.NEXT_SEARCH_RANGE.size());
		int startTileX = mob.getTileX();
		int startTileY = mob.getTileY();
		Iterator<Point> iterator = GoJoggingTileLevelJob.NEXT_SEARCH_RANGE.getValidTiles(startTileX, startTileY).iterator();

		while (iterator.hasNext()) {
			Point tile = iterator.next();
			if (zoneTester != null && !zoneTester.containsTile(tile.x, tile.y)) continue;
			if (startTileX == tile.x && startTileY == tile.y) continue;
			if (mob.getLevel().isSolidTile(tile.x, tile.y)) continue;
			if (mob.getLevel().isLiquidTile(tile.x, tile.y)) continue;
			if (!isOutsideOrCave(mob.getLevel(), tile.x, tile.y)) continue;
			if (mob.collidesWith(mob.getLevel(), tile.x * 32 + pathOffset.x, tile.y * 32 + pathOffset.y)) continue;
			tiles.add(tile);
		}

		for (int attempts = 50; !tiles.isEmpty() && attempts > 0; --attempts) {
			int index = GameRandom.globalRandom.nextInt(tiles.size());
			Point tile = tiles.get(index);
			if (mob.estimateCanMoveTo(tile.x, tile.y, false)) return tile;
			tiles.remove(index);
		}
		return null;
	}

	public static JobTypeHandler.JobStreamSupplier getJobStreamer() {
		return (worker, handler) -> {
			Mob mob = worker.getMobWorker();
			if (mob == null || !isOutsideOrCave(worker.getLevel(), mob.getTileX(), mob.getTileY())) return Stream.empty();
			Point nextTargetTile = findNextTargetTile(mob, worker.getJobRestrictZone());
			return nextTargetTile == null ? Stream.empty() : Stream.of(new GoJoggingTileLevelJob(nextTargetTile.x, nextTargetTile.y));
		};
	}
}
