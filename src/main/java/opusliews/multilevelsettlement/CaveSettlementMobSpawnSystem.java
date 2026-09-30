package opusliews.multilevelsettlement;

import necesse.engine.util.GameRandom;
import necesse.entity.manager.EntityManager;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.level.maps.Level;
import necesse.level.maps.biomes.MobChance;
import necesse.level.maps.biomes.MobSpawnTable;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opusliews.logging.Logging;

import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class CaveSettlementMobSpawnSystem {
	private static final Map<ServerSettlementData, SpawnState> states = Collections.synchronizedMap(new WeakHashMap<>());
	private static final float vanillaAttemptsPerSecond = 0.6F;
	private static final long maxElapsedMs = 5000L;

	private CaveSettlementMobSpawnSystem() {
	}

	public static void serverTick(ServerSettlementData settlement, SettlementLevelDomain domain, Level cave, Rectangle spawnBounds) {
		if (settlement == null || domain == null || cave == null || spawnBounds == null || !cave.isServer()) return;
		if (settlement.getServer() == null || settlement.getServer().world.settings.disableMobSpawns) return;

		SpawnState state = states.computeIfAbsent(settlement, ignored -> new SpawnState());
		long now = cave.getTime();
		if (state.lastTime == 0L) {
			state.lastTime = now;
			return;
		}

		long elapsed = Math.max(0L, Math.min(maxElapsedMs, now - state.lastTime));
		state.lastTime = now;

		if (cave.presentPlayers > 0 || !hasLivingSettlementResident(settlement, domain, cave)) {
			state.spawnProgress = 0.0F;
			return;
		}

		int centerTileX = spawnBounds.x + spawnBounds.width / 2;
		int centerTileY = spawnBounds.y + spawnBounds.height / 2;
		float spawnRate = vanillaAttemptsPerSecond
				* settlement.getServer().world.settings.difficulty.enemySpawnRateModifier
				* cave.entityManager.getSpawnRate(centerTileX, centerTileY);
		state.spawnProgress += spawnRate * ((float)elapsed / 1000.0F);

		while (state.spawnProgress >= 1.0F) {
			state.spawnProgress -= 1.0F;
			if (!trySpawn(settlement, cave, spawnBounds, centerTileX, centerTileY)) state.spawnProgress += 0.5F;
		}
	}

	public static void remove(ServerSettlementData settlement) {
		if (settlement != null) states.remove(settlement);
	}

	private static boolean trySpawn(ServerSettlementData settlement, Level cave, Rectangle spawnBounds, int centerTileX, int centerTileY) {
		float spawnCap = EntityManager.getSpawnCap(1, 25.0F, 5.0F)
				* settlement.getServer().world.settings.difficulty.enemySpawnCapModifier
				* cave.entityManager.getSpawnCapMod(centerTileX, centerTileY);
		int centerX = centerTileX * 32 + 16;
		int centerY = centerTileY * 32 + 16;
		int hostileCount = cave.entityManager.countMobs(centerX, centerY, Mob.MOB_SPAWN_AREA, mob -> mob.isHostile && mob.canDespawn);
		if ((float)hostileCount >= spawnCap) return true;

		Point spawnTile = getSpawnTile(cave, spawnBounds);
		if (spawnTile == null) return false;

		MobSpawnTable spawnTable = new MobSpawnTable().include(cave.getBiome(spawnTile.x, spawnTile.y).getMobSpawnTable(cave));
		while (true) {
			MobChance randomMob = spawnTable.getRandomMob(cave, null, spawnTile, GameRandom.globalRandom, "mobspawning");
			if (randomMob == null) return false;
			Collection spawned = randomMob.spawnMob(cave, null, spawnTile, null, cave::onMobSpawned, "mobspawning");
			if (spawned != null) {
				return true;
			}
			spawnTable = spawnTable.withoutRandomMob(randomMob);
		}
	}

	private static Point getSpawnTile(Level cave, Rectangle spawnBounds) {
		int centerTileX = spawnBounds.x + spawnBounds.width / 2;
		int centerTileY = spawnBounds.y + spawnBounds.height / 2;
		return Mob.MOB_SPAWN_AREA.getRandomTicketTile(GameRandom.globalRandom, centerTileX, centerTileY, tile -> {
			if (!spawnBounds.contains(tile.x, tile.y)) return 0;
			if (!cave.isTileWithinBounds(tile.x, tile.y) || cave.isSolidTile(tile.x, tile.y)) return 0;
			return cave.getTile(tile.x, tile.y).getMobSpawnPositionTickets(cave, tile.x, tile.y);
		});
	}

	private static boolean hasLivingSettlementResident(ServerSettlementData settlement, SettlementLevelDomain domain, Level cave) {
		Level surface = settlement.getLevel();
		if (hasLivingSettlementResidentOnLevel(settlement, domain, surface)) return true;
		return cave != surface && hasLivingSettlementResidentOnLevel(settlement, domain, cave);
	}

	private static boolean hasLivingSettlementResidentOnLevel(ServerSettlementData settlement, SettlementLevelDomain domain, Level level) {
		if (level == null) return false;
		for (Mob mob : level.entityManager.mobs) {
			if (!(mob instanceof HumanMob) || mob.removed() || mob.getHealth() <= 0) continue;
			HumanMob human = (HumanMob)mob;
			if (!human.isSettler() || human.getSettlementUniqueID() != settlement.uniqueID) continue;
			if (!domain.isTileWithinBounds(level.getIdentifier(), mob.getTileX(), mob.getTileY())) continue;
			return true;
		}
		return false;
	}

	private static final class SpawnState {
		private long lastTime;
		private float spawnProgress;
	}
}
