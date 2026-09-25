package opusliews.trapdoor;

import necesse.engine.registries.ObjectRegistry;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.level.maps.Level;
import opusliews.buff.TrapdoorHiddenBuff;
import opusliews.object.TrapdoorObject;
import opusliews.buff.DeepHoleHiddenBuff;

public class TrapdoorSystem {
	public static boolean isTrapdoorHidden(PlayerMob player) {
		return player != null && player.buffManager.hasBuff(TrapdoorHiddenBuff.stringID);
	}

	public static boolean isHidden(PlayerMob player) {
		return player != null && (
				player.buffManager.hasBuff(TrapdoorHiddenBuff.stringID)
						|| player.buffManager.hasBuff(DeepHoleHiddenBuff.stringID)
		);
	}


	public static boolean hasHiddenPlayerAt(Level level, int tileX, int tileY) {
		if (level == null) return false;
		return level.entityManager.players.streamInRegionsInTileRange(tileX * 32 + 16, tileY * 32 + 16, 1)
				.anyMatch(player -> isTrapdoorHidden(player)
						&& player.getTileX() == tileX
						&& player.getTileY() == tileY);
	}

	public static void reconcilePlayerState(PlayerMob player) {
		if (player == null || !player.isServer() || player.getLevel() == null) return;

		Level level = player.getLevel();
		int tileX = player.getTileX();
		int tileY = player.getTileY();
		boolean hidden = isTrapdoorHidden(player);
		boolean openTrapdoor = level.getObjectID(tileX, tileY) == ObjectRegistry.getObjectID(TrapdoorObject.openStringID);
		boolean closedTrapdoor = level.getObjectID(tileX, tileY) == ObjectRegistry.getObjectID(TrapdoorObject.closedStringID);

		if (hidden) {
			if (closedTrapdoor) return;

			if (openTrapdoor) {
				level.setObject(tileX, tileY, ObjectRegistry.getObjectID(TrapdoorObject.closedStringID), 0);
				level.sendObjectUpdatePacket(tileX, tileY);
				return;
			}

			player.buffManager.removeBuff(TrapdoorHiddenBuff.stringID, true);
			return;
		}

		if (closedTrapdoor && !hasHiddenPlayerAt(level, tileX, tileY)) {
			level.setObject(tileX, tileY, ObjectRegistry.getObjectID(TrapdoorObject.openStringID), 0);
			level.sendObjectUpdatePacket(tileX, tileY);
		}
	}

	public static void enterTrapdoor(Level level, int tileX, int tileY, PlayerMob player) {
		if (player == null || isHidden(player)) return;
		if (player.getTileX() != tileX || player.getTileY() != tileY) return;
		if (level.getObjectID(tileX, tileY) != ObjectRegistry.getObjectID(TrapdoorObject.openStringID)) return;

		player.dismount();
		player.forceEndAttack();
		player.endAttackHandler(true);
		if (player.isServer() && player.serverFollowersManager != null) {
			player.serverFollowersManager.clearSummonFocus();
		}
		player.moveX = 0.0F;
		player.moveY = 0.0F;
		player.dx = 0.0F;
		player.dy = 0.0F;
		player.buffManager.addBuff(new ActiveBuff(TrapdoorHiddenBuff.stringID, player, Integer.MAX_VALUE, null), true);
		level.setObject(tileX, tileY, ObjectRegistry.getObjectID(TrapdoorObject.closedStringID), 0);
		level.sendObjectUpdatePacket(tileX, tileY);
	}

	public static void exitTrapdoor(Level level, int tileX, int tileY, PlayerMob player) {
		if (player == null || !isTrapdoorHidden(player)) return;
		if (player.getTileX() != tileX || player.getTileY() != tileY) return;
		if (level.getObjectID(tileX, tileY) != ObjectRegistry.getObjectID(TrapdoorObject.closedStringID)) return;

		player.buffManager.removeBuff(TrapdoorHiddenBuff.stringID, true);
		level.setObject(tileX, tileY, ObjectRegistry.getObjectID(TrapdoorObject.openStringID), 0);
		level.sendObjectUpdatePacket(tileX, tileY);
	}

	public static void revealHiddenPlayerAt(Level level, int tileX, int tileY) {
		level.entityManager.players.streamInRegionsInTileRange(tileX * 32 + 16, tileY * 32 + 16, 1)
				.filter(TrapdoorSystem::isTrapdoorHidden)
				.filter(player -> player.getTileX() == tileX && player.getTileY() == tileY)
				.forEach(player -> player.buffManager.removeBuff(TrapdoorHiddenBuff.stringID, true));
	}
}
