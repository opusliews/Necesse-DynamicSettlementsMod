package opusliews.buff;

import java.awt.Rectangle;

import necesse.engine.localization.Localization;
import necesse.engine.util.GameRandom;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.entity.mobs.buffs.BuffEventSubscriber;
import necesse.entity.mobs.buffs.BuffModifiers;
import necesse.entity.mobs.buffs.staticBuffs.armorBuffs.trinketBuffs.TrinketBuff;
import necesse.entity.particle.Particle;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.trinketItem.TrinketItem;
import necesse.level.gameTile.GameTile;
import necesse.level.maps.CollisionFilter;
import necesse.level.maps.Level;

public class MalignanceGogglesBuff extends TrinketBuff {
	private static final int radiusTiles = 15;
	private static final int tileCheckIntervalTicks = 20;
	private static final int collisionSize = 20;
	private static final int smokeMinSize = 5;
	private static final int smokeMaxSize = 15;
	private static final int smokeLifeTime = 1500;

	@Override
	public void init(ActiveBuff buff, BuffEventSubscriber eventSubscriber) {
	}

	@Override
	public ListGameTooltips getTrinketTooltip(TrinketItem trinketItem, InventoryItem item, PlayerMob perspective) {
		ListGameTooltips tooltips = super.getTrinketTooltip(trinketItem, item, perspective);
		tooltips.add(Localization.translate("itemtooltip", "malignancegogglestip"));
		return tooltips;
	}

	@Override
	public void clientTick(ActiveBuff buff) {
		super.clientTick(buff);

		if (!(buff.owner instanceof PlayerMob)) {
			return;
		}

		PlayerMob player = (PlayerMob)buff.owner;
		Level level = player.getLevel();
		long totalTicks = level.tickManager().getTotalTicks();
		int currentPhase = Math.floorMod(totalTicks, tileCheckIntervalTicks);
		int centerTileX = player.getTileX();
		int centerTileY = player.getTileY();
		int radiusSquared = radiusTiles * radiusTiles;
		int lightThreshold = player.buffManager.getAndApplyModifiers(BuffModifiers.MOB_SPAWN_LIGHT_THRESHOLD, 0);

		for (int offsetY = -radiusTiles; offsetY <= radiusTiles; offsetY++) {
			for (int offsetX = -radiusTiles; offsetX <= radiusTiles; offsetX++) {
				if (offsetX * offsetX + offsetY * offsetY > radiusSquared) {
					continue;
				}

				int tileX = centerTileX + offsetX;
				int tileY = centerTileY + offsetY;
				if (getTilePhase(tileX, tileY) != currentPhase) {
					continue;
				}

				if (isStandardHostileSpawnTile(level, tileX, tileY, lightThreshold)) {
					for (int i = 0; i < 10; i++) {
						spawnSmoke(level, tileX, tileY);
					}
				}
			}
		}
	}

	private int getTilePhase(int tileX, int tileY) {
		int hash = tileX * 73428767 ^ tileY * 912931;
		return Math.floorMod(hash, tileCheckIntervalTicks);
	}

	private boolean isStandardHostileSpawnTile(Level level, int tileX, int tileY, int lightThreshold) {
		if (!level.isTileWithinBounds(tileX, tileY) || level.isSolidTile(tileX, tileY)) {
			return false;
		}

		GameTile tile = level.getTile(tileX, tileY);
		if (tile.isLiquid || tile.getMobSpawnPositionTickets(level, tileX, tileY) <= 0) {
			return false;
		}

		if (!level.isCave && !level.isOutside(tileX, tileY) && tile.isFloor) {
			return false;
		}

		if (level.lightManager.getStaticLight(tileX, tileY).getLevel() > lightThreshold) {
			return false;
		}

		int centerX = tileX * 32 + 16;
		int centerY = tileY * 32 + 16;
		int collisionOffset = collisionSize / 2;
		Rectangle collision = new Rectangle(centerX - collisionOffset, centerY - collisionOffset, collisionSize, collisionSize);
		return !level.collides(collision, new CollisionFilter().mobCollision());
	}

	private void spawnSmoke(Level level, int tileX, int tileY) {
		float x = tileX * 32 + 16 + GameRandom.globalRandom.getFloatBetween(-15.0F, 15.0F);
		float y = tileY * 32 + 16 + GameRandom.globalRandom.getFloatBetween(-15.0F, 15.0F);

		level.entityManager.addParticle(x, y, Particle.GType.COSMETIC)
				.smokeColor()
				.sizeFades(smokeMinSize, smokeMaxSize)
				.fadesAlphaTime(100, 550)
				.heightMoves(2.0F, 30.0F)
				.lifeTime(smokeLifeTime);
	}
}
