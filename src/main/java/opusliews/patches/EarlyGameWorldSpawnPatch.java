package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.world.World;
import necesse.engine.world.WorldEntity;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ModMethodPatch(target = WorldEntity.class, name = "initServer", arguments = {boolean.class})
public class EarlyGameWorldSpawnPatch {
	@Advice.OnMethodEnter
	public static boolean onEnter(
			@Advice.This WorldEntity worldEntity,
			@Advice.Argument(value = 0, readOnly = false) boolean guideSpawn,
			@Advice.FieldValue("hasFoundSpawn") boolean hasFoundSpawn
	) {
		guideSpawn = false;
		return !hasFoundSpawn;
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This WorldEntity worldEntity,
			@Advice.Enter boolean newWorldSpawn,
			@Advice.FieldValue("serverWorld") World serverWorld
	) {
		if (!newWorldSpawn || serverWorld == null || worldEntity.spawnTile == null) {
			return;
		}

		Level level = serverWorld.getLevel(worldEntity.spawnLevelIdentifier);
		if (level == null) {
			return;
		}

		ensureOakStump(level, worldEntity.spawnTile, worldEntity.getWorldSeed());
	}

	public static void ensureOakStump(Level level, Point spawnTile, int worldSeed) {
		int stumpID = ObjectRegistry.getObjectID("oaktreestump");
		if (stumpID == -1) {
			return;
		}

		GameObject stump = ObjectRegistry.getObject(stumpID);
		List<Point> candidates = new ArrayList<>();
		for (int dx = -18; dx <= 16; dx++) {
			for (int dy = -18; dy <= 16; dy++) {
				double distance = Math.sqrt(dx * dx + dy * dy);
				if (distance > 3 && distance <= 6) {
					candidates.add(new Point(spawnTile.x + dx, spawnTile.y + dy));
				}
			}
		}

		Collections.shuffle(candidates, new java.util.Random(worldSeed ^ 0x5A17BEEF));
		for (Point candidate : candidates) {
			if (!level.isTileWithinBounds(candidate.x, candidate.y)) {
				continue;
			}
			if (level.getObjectID(candidate.x, candidate.y) != 0) {
				continue;
			}
			if (stump.canPlace(level, candidate.x, candidate.y, 0, false) != null) {
				continue;
			}

			level.setObject(candidate.x, candidate.y, stumpID, 0);
			return;
		}

		// Extremely defensive fallback: preserve the distance guarantee even if the ring is fully occupied.
		for (Point candidate : candidates) {
			if (!level.isTileWithinBounds(candidate.x, candidate.y) || level.isLiquidTile(candidate.x, candidate.y)) {
				continue;
			}

			level.setObject(candidate.x, candidate.y, 0);
			level.setObject(candidate.x, candidate.y, stumpID, 0);
			return;
		}
	}
}
