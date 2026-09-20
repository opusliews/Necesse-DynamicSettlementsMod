package opusliews.worldgen;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;

import necesse.engine.registries.BiomeRegistry;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.util.GameRandom;
import necesse.engine.world.WorldEntity;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;

public final class StartingFiremoneFeature {
	private static final int preferredRadius = 50;
	private static final int minRadius = 20;
	private static final int maxRadius = 100;
	private static final int biomeEdgeMargin = 12;
	private static final int clusterRadius = 4;
	private static final int minClusterSize = 8;
	private static final int maxClusterSize = 12;
	private static final int randomDirectionAttempts = 64;

	private StartingFiremoneFeature() {
	}

	public static void generate(WorldEntity worldEntity) {
		if (worldEntity == null || worldEntity.serverWorld == null || worldEntity.spawnTile == null) return;

		Level level = worldEntity.serverWorld.getLevel(worldEntity.spawnLevelIdentifier);
		if (level == null || !level.isServer()) return;

		int spawnX = worldEntity.spawnTile.x;
		int spawnY = worldEntity.spawnTile.y;
		if (level.getBiome(spawnX, spawnY) != BiomeRegistry.FOREST) return;

		GameObject firemone = ObjectRegistry.getObject("wildfiremone");
		if (firemone == null) return;

		GameRandom random = new GameRandom(((long)worldEntity.getWorldSeed() << 32) ^ 0x464952454D4F4E45L);

		for (int attempt = 0; attempt < randomDirectionAttempts; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			if (tryDirection(level, firemone, spawnX, spawnY, angle, random)) return;
		}

		// Safety fallback: exhaustively inspect the whole 20-100 tile annulus,
		// preferring positions closest to the requested 50 tile distance.
		ArrayList<Point> fallbackCenters = new ArrayList<>();
		int minRadiusSq = minRadius * minRadius;
		int maxRadiusSq = maxRadius * maxRadius;

		for (int x = spawnX - maxRadius; x <= spawnX + maxRadius; x++) {
			for (int y = spawnY - maxRadius; y <= spawnY + maxRadius; y++) {
				int dx = x - spawnX;
				int dy = y - spawnY;
				int distanceSq = dx * dx + dy * dy;
				if (distanceSq < minRadiusSq || distanceSq > maxRadiusSq) continue;
				fallbackCenters.add(new Point(x, y));
			}
		}

		fallbackCenters.sort(Comparator.comparingDouble(point ->
				Math.abs(Math.hypot(point.x - spawnX, point.y - spawnY) - preferredRadius)
		));

		for (Point center : fallbackCenters) {
			if (tryPlaceCluster(level, firemone, center.x, center.y, random)) return;
		}
	}

	private static boolean tryDirection(
			Level level,
			GameObject firemone,
			int spawnX,
			int spawnY,
			double angle,
			GameRandom random
	) {
		for (int radius = preferredRadius; radius <= maxRadius; radius++) {
			int centerX = spawnX + (int)Math.round(Math.cos(angle) * radius);
			int centerY = spawnY + (int)Math.round(Math.sin(angle) * radius);
			if (tryPlaceCluster(level, firemone, centerX, centerY, random)) return true;
		}

		for (int radius = preferredRadius - 1; radius >= minRadius; radius--) {
			int centerX = spawnX + (int)Math.round(Math.cos(angle) * radius);
			int centerY = spawnY + (int)Math.round(Math.sin(angle) * radius);
			if (tryPlaceCluster(level, firemone, centerX, centerY, random)) return true;
		}

		return false;
	}

	private static boolean tryPlaceCluster(Level level, GameObject firemone, int centerX, int centerY, GameRandom random) {
		if (!isFarEnoughFromBiomeEdge(level, centerX, centerY)) return false;

		ArrayList<Point> validTiles = collectValidTiles(level, firemone, centerX, centerY);
		if (validTiles.size() < minClusterSize) return false;

		int placeCount = Math.min(validTiles.size(), random.getIntBetween(minClusterSize, maxClusterSize));
		for (int i = 0; i < placeCount; i++) {
			Point tile = validTiles.remove(random.nextInt(validTiles.size()));
			firemone.placeObject(level, tile.x, tile.y, 0, false);
		}
		return true;
	}

	private static boolean isFarEnoughFromBiomeEdge(Level level, int centerX, int centerY) {
		for (int x = centerX - biomeEdgeMargin; x <= centerX + biomeEdgeMargin; x++) {
			for (int y = centerY - biomeEdgeMargin; y <= centerY + biomeEdgeMargin; y++) {
				if (!level.isTileWithinBounds(x, y) || level.getBiome(x, y) != BiomeRegistry.FOREST) return false;
			}
		}
		return true;
	}

	private static ArrayList<Point> collectValidTiles(Level level, GameObject firemone, int centerX, int centerY) {
		ArrayList<Point> validTiles = new ArrayList<>();
		for (int x = centerX - clusterRadius; x <= centerX + clusterRadius; x++) {
			for (int y = centerY - clusterRadius; y <= centerY + clusterRadius; y++) {
				int dx = x - centerX;
				int dy = y - centerY;
				if (dx * dx + dy * dy > clusterRadius * clusterRadius) continue;
				if (!level.isTileWithinBounds(x, y)) continue;
				if (firemone.canPlace(level, x, y, 0, false) != null) continue;
				validTiles.add(new Point(x, y));
			}
		}
		return validTiles;
	}
}
