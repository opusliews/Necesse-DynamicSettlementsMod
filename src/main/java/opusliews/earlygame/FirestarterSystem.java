package opusliews.earlygame;

import necesse.engine.util.GameMath;
import necesse.engine.util.GameRandom;
import necesse.entity.levelEvent.mobAbilityLevelEvent.PhoenixFeatherGroundFireEvent;
import necesse.entity.mobs.GameDamage;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.level.maps.Level;
import opusliews.item.FirestarterItem;
import opusliews.network.PacketFirestarterUse;

public final class FirestarterSystem {
	private static final int interactRange = 100;

	private FirestarterSystem() {
	}

	public static boolean tryClientInteract(PlayerMob player, int levelX, int levelY) {
		Level level = player.getLevel();
		if (level == null || !level.isClient() || !isFirestarter(player.getSelectedItem())) {
			return false;
		}

		int tileX = GameMath.getTileCoordinate(levelX);
		int tileY = GameMath.getTileCoordinate(levelY);
		if (!level.isTileWithinBounds(tileX, tileY) || level.getObjectID(tileX, tileY) != 0) {
			return false;
		}

		level.getClient().network.sendPacket(new PacketFirestarterUse(tileX, tileY));
		return true;
	}

	public static void tryServerUse(PlayerMob player, int tileX, int tileY) {
		Level level = player.getLevel();
		if (level == null || !level.isServer() || !level.isTileWithinBounds(tileX, tileY) || level.isProtected(tileX, tileY)) {
			return;
		}

		if (!isFirestarter(player.getSelectedItem()) || !isWithinRange(player, tileX, tileY) || level.getObjectID(tileX, tileY) != 0) {
			return;
		}

		int x = tileX * 32 + 16;
		int y = tileY * 32 + 16;
		level.entityManager.addLevelEvent(new PhoenixFeatherGroundFireEvent(
				null,
				x,
				y,
				new GameRandom(GameRandom.globalRandom.nextLong()),
				new GameDamage(0.0F)
		));
	}

	private static boolean isFirestarter(InventoryItem item) {
		return item != null && FirestarterItem.stringID.equals(item.item.getStringID());
	}

	private static boolean isWithinRange(PlayerMob player, int tileX, int tileY) {
		return player.getPositionPoint().distance(tileX * 32.0 + 16.0, tileY * 32.0 + 16.0) <= interactRange;
	}
}
