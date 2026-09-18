package opusliews.deephole;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import necesse.engine.registries.TileRegistry;
import necesse.engine.sound.SoundEffect;
import necesse.engine.sound.SoundManager;
import necesse.engine.util.GameMath;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.gfx.GameResources;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.pickaxeToolItem.PickaxeToolItem;
import necesse.level.maps.Level;
import opusliews.buff.DeepHoleDiggingBuff;
import opusliews.buff.DeepHoleHiddenBuff;
import opusliews.logging.Logging;
import opusliews.network.PacketDeepHoleInteract;
import opusliews.tile.DeepHoleTile;
import opusliews.tile.ShallowHoleTile;

import static opusliews.DynamicSettlements.rockSlideSound;

public final class DeepHoleSystem {
	public static final int fallDuration = 350;
	public static final int fallDistance = 64;
	public static final int windupDuration = 1000;
	public static final int strikeDuration = 150;
	public static final int strikeCount = 5;
	public static final int strikeCycleDuration = windupDuration + strikeDuration;
	public static final int diggingDuration = strikeCycleDuration * strikeCount;

	private static final String fallStartKey = "fallStartTime";
	private static final String diggingStartKey = "diggingStartTime";
	private static final Map<PlayerMob, Long> clientFallStartTimes = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<PlayerMob, ClientDiggingState> clientDiggingStates = Collections.synchronizedMap(new WeakHashMap<>());

	private DeepHoleSystem() {
	}

	public static boolean isOccupied(PlayerMob player) {
		boolean occupied = player != null && player.buffManager.hasBuff(DeepHoleHiddenBuff.stringID);
		if (!occupied && player != null) clientFallStartTimes.remove(player);
		return occupied;
	}

	public static boolean isDigging(PlayerMob player) {
		return player != null && player.buffManager.hasBuff(DeepHoleDiggingBuff.stringID);
	}

	public static boolean isFullyHidden(PlayerMob player) {
		return isOccupied(player) && getFallProgress(player) >= 1.0F;
	}

	public static int getDrawYOffset(PlayerMob player) {
		if (!isOccupied(player)) return 0;
		return Math.round(fallDistance * getFallProgress(player));
	}

	public static float getDiggingAttackProgress(PlayerMob player) {
		if (!isDigging(player)) return 0.0F;

		long elapsed = getClientDiggingElapsed(player);
		long cycleElapsed = elapsed % strikeCycleDuration;
		if (elapsed >= diggingDuration) cycleElapsed = strikeCycleDuration - 1L;

		if (cycleElapsed < windupDuration) {
			float t = Math.min(1.0F, (float)cycleElapsed / (float)windupDuration);
			t = t * t * (3.0F - 2.0F * t);
			return 0.35F * (1.0F - t);
		}

		float t = Math.min(1.0F, (float)(cycleElapsed - windupDuration) / (float)strikeDuration);
		t = t * t;
		return t;
	}

	public static boolean tryClientInteract(PlayerMob player, int levelX, int levelY) {
		Level level = player.getLevel();
		if (level == null || !level.isClient()) return false;
		if (isDigging(player)) return true;

		InventoryItem selected = player.getSelectedItem();
		if (selected == null || !(selected.item instanceof PickaxeToolItem)) return false;

		int tileX = GameMath.getTileCoordinate(levelX);
		int tileY = GameMath.getTileCoordinate(levelY);
		if (tileX != player.getTileX() || tileY != player.getTileY()) return false;
		if (!isPlayerInSinkingArea(level, tileX, tileY, player)) return false;

		boolean canEnter = !isOccupied(player)
				&& level.getTileID(tileX, tileY) == TileRegistry.getTileID(ShallowHoleTile.stringID)
				&& level.getObjectID(tileX, tileY) == 0;
		boolean canExit = isOccupied(player)
				&& level.getTileID(tileX, tileY) == TileRegistry.getTileID(DeepHoleTile.stringID);

		if (!canEnter && !canExit) return false;

		level.getClient().network.sendPacket(new PacketDeepHoleInteract(tileX, tileY, canEnter));
		return true;
	}

	public static boolean tryServerInteract(PlayerMob player, int tileX, int tileY, boolean enter) {
		Level level = player.getLevel();
		if (level == null || !level.isServer()) return false;
		if (tileX != player.getTileX() || tileY != player.getTileY()) return false;
		if (!isPlayerInSinkingArea(level, tileX, tileY, player)) return false;

		InventoryItem selected = player.getSelectedItem();
		if (selected == null || !(selected.item instanceof PickaxeToolItem)) return false;

		if (enter) {
			if (isOccupied(player) || isDigging(player)) return false;
			return startDigging(level, tileX, tileY, player);
		}

		if (isDigging(player) || !isOccupied(player)) return false;
		return exitDeepHole(level, tileX, tileY, player);
	}

	public static void tick(PlayerMob player) {
		if (player == null) return;

		if (!isDigging(player)) {
			ClientDiggingState state = clientDiggingStates.remove(player);
			if (state != null && player.getLevel() != null && player.getLevel().isClient()) {
				player.forceEndAttack();
			}
			return;
		}

		Level level = player.getLevel();
		if (level == null) return;

		player.moveX = 0.0F;
		player.moveY = 0.0F;
		player.dx = 0.0F;
		player.dy = 0.0F;
		player.setDir(2);

		if (level.isClient()) {
			tickClientDigging(player);
		}
		else if (level.isServer()) {
			tickServerDigging(player);
		}
	}

	private static boolean startDigging(Level level, int tileX, int tileY, PlayerMob player) {
		if (level.getTileID(tileX, tileY) != TileRegistry.getTileID(ShallowHoleTile.stringID)) return false;
		if (level.getObjectID(tileX, tileY) != 0) return false;

		player.dismount();
		player.forceEndAttack();
		player.endAttackHandler(true);
		if (player.serverFollowersManager != null) player.serverFollowersManager.clearSummonFocus();

		player.setPos(tileX * 32.0F + 16.0F, tileY * 32.0F + 16.0F, true);
		player.setDir(2);
		player.sendMovementPacket(true);
		player.moveX = 0.0F;
		player.moveY = 0.0F;
		player.dx = 0.0F;
		player.dy = 0.0F;

		ActiveBuff buff = new ActiveBuff(DeepHoleDiggingBuff.stringID, player, 20.0F, null);
		buff.getGndData().setLong(diggingStartKey, level.getWorldEntity().getTime());
		player.buffManager.addBuff(buff, true);
		return true;
	}

	private static void tickServerDigging(PlayerMob player) {
		Level level = player.getLevel();
		ActiveBuff buff = player.buffManager.getBuff(DeepHoleDiggingBuff.stringID);
		if (level == null || buff == null) return;

		long startTime = buff.getGndData().getLong(diggingStartKey, level.getWorldEntity().getTime());
		if (level.getWorldEntity().getTime() - startTime < diggingDuration) return;

		int tileX = player.getTileX();
		int tileY = player.getTileY();
		if (level.getTileID(tileX, tileY) != TileRegistry.getTileID(ShallowHoleTile.stringID)
				|| level.getObjectID(tileX, tileY) != 0
				|| !isPlayerInSinkingArea(level, tileX, tileY, player)) {
			player.buffManager.removeBuff(DeepHoleDiggingBuff.stringID, true);
			return;
		}

		finishDigging(level, tileX, tileY, player);
	}

	private static void tickClientDigging(PlayerMob player) {
		ClientDiggingState state = clientDiggingStates.get(player);
		if (state == null) {
			state = new ClientDiggingState(System.nanoTime() / 1000000L);
			clientDiggingStates.put(player, state);

			InventoryItem selected = player.getSelectedItem();
			if (selected != null && selected.item instanceof PickaxeToolItem) {
				state.pickaxe = selected.copy();
				player.showItemAttack(
						state.pickaxe,
						player.getX(),
						player.getY() + 32,
						0,
						0,
						null
				);
			}
		}

		player.setDir(2);
		player.isAttacking = true;

		long elapsed = Math.max(0L, System.nanoTime() / 1000000L - state.startTime);

		int reachedStrikes = 0;

		if (elapsed >= windupDuration) {
			reachedStrikes = Math.min(
					strikeCount,
					1 + (int)((elapsed - windupDuration) / strikeCycleDuration)
			);
		}

		while (state.lastStrikePlayed < reachedStrikes) {
			state.lastStrikePlayed++;

			Logging.logMessage(
					"Strike: " + state.lastStrikePlayed + "/" + strikeCount
			);

			playStrikeSound(player);

			if (state.lastStrikePlayed == strikeCount) {
				playFinalStrikeSound(player);
			}
		}
	}

	private static void finishDigging(Level level, int tileX, int tileY, PlayerMob player) {
		level.setTile(tileX, tileY, TileRegistry.getTileID(DeepHoleTile.stringID));
		level.sendTileUpdatePacket(tileX, tileY);

		player.buffManager.removeBuff(DeepHoleDiggingBuff.stringID, true);
		ActiveBuff buff = new ActiveBuff(DeepHoleHiddenBuff.stringID, player, Integer.MAX_VALUE, null);
		buff.getGndData().setLong(fallStartKey, level.getWorldEntity().getTime());
		player.buffManager.addBuff(buff, true);
	}

	private static void playStrikeSound(PlayerMob player) {
		SoundManager.playSound(
				GameResources.cling,
				SoundEffect.effect(player.getX(), player.getY()).volume(0.8F).pitch(1.0F)
		);
	}

	public static void playFinalStrikeSound(PlayerMob player) {
		SoundManager.playSound(
				GameResources.cling,
				SoundEffect.effect(player.getX(), player.getY()).volume(0.8F).pitch(1.0F)
		);
		SoundManager.playSound(
				rockSlideSound,
				SoundEffect.effect(player.getX(), player.getY()).volume(0.8F).pitch(0.7F)
		);
	}

	private static boolean isPlayerInSinkingArea(Level level, int tileX, int tileY, PlayerMob player) {
		if (!(level.getTile(tileX, tileY) instanceof ShallowHoleTile)) return false;
		return ((ShallowHoleTile)level.getTile(tileX, tileY)).isMobInSinkingArea(player);
	}

	// TODO-remove: Temporary pickaxe interaction used to leave the deep hole during development.
	private static boolean exitDeepHole(Level level, int tileX, int tileY, PlayerMob player) {
		if (level.getTileID(tileX, tileY) != TileRegistry.getTileID(DeepHoleTile.stringID)) return false;

		level.setTile(tileX, tileY, TileRegistry.getTileID(ShallowHoleTile.stringID));
		level.sendTileUpdatePacket(tileX, tileY);
		player.buffManager.removeBuff(DeepHoleHiddenBuff.stringID, true);

		player.setPos(tileX * 32.0F + 16.0F, tileY * 32.0F + 16.0F, true);
		player.sendMovementPacket(true);
		player.moveX = 0.0F;
		player.moveY = 0.0F;
		player.dx = 0.0F;
		player.dy = 0.0F;
		return true;
	} // TODO-remove ^

	private static long getClientDiggingElapsed(PlayerMob player) {
		ClientDiggingState state = clientDiggingStates.get(player);
		if (state == null) return 0L;
		return Math.max(0L, System.nanoTime() / 1000000L - state.startTime);
	}

	private static float getFallProgress(PlayerMob player) {
		if (!isOccupied(player)) return 0.0F;

		Level level = player.getLevel();
		if (level == null) return 1.0F;

		long elapsed;
		if (level.isClient()) {
			Long startTime = clientFallStartTimes.get(player);
			long now = System.nanoTime() / 1000000L;
			if (startTime == null) {
				clientFallStartTimes.put(player, now);
				startTime = now;
			}
			elapsed = Math.max(0L, now - startTime);
		}
		else {
			ActiveBuff buff = player.buffManager.getBuff(DeepHoleHiddenBuff.stringID);
			if (buff == null) return 0.0F;
			long startTime = buff.getGndData().getLong(fallStartKey, level.getWorldEntity().getTime());
			elapsed = Math.max(0L, level.getWorldEntity().getTime() - startTime);
		}

		float progress = Math.min(1.0F, (float)elapsed / (float)fallDuration);
		return progress * progress;
	}

	private static class ClientDiggingState {
		private final long startTime;
		private int lastStrikePlayed;
		private InventoryItem pickaxe;

		private ClientDiggingState(long startTime) {
			this.startTime = startTime;
		}
	}
}
