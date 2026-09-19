package opusliews.deephole;

import java.awt.Point;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.network.packet.PacketChangeObject;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.registries.TileRegistry;
import necesse.engine.sound.SoundEffect;
import necesse.engine.sound.SoundManager;
import necesse.engine.util.GameMath;
import necesse.engine.util.LevelIdentifier;
import necesse.engine.util.TeleportResult;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.objectEntity.LadderDownObjectEntity;
import necesse.entity.objectEntity.PortalObjectEntity;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.gfx.GameResources;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.pickaxeToolItem.PickaxeToolItem;
import necesse.level.gameObject.ObjectHoverHitbox;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import opusliews.buff.DeepHoleCaveFallBuff;
import opusliews.buff.DeepHoleDiggingBuff;
import opusliews.buff.DeepHoleHiddenBuff;
import opusliews.buff.DeepHoleLadderDescentBuff;
import opusliews.item.DirtPileItem;
import opusliews.logging.Logging;
import opusliews.network.PacketDeepHoleInteract;
import opusliews.network.PacketHoleCaveLadderInteract;
import opusliews.object.DeepHoleCeilingLightObject;
import opusliews.object.HoleCaveLadderObject;
import opusliews.object.HoleCaveLadderUpObject;
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
	public static final int ladderTravelDelay = 2000;
	public static final int caveFallDuration = 500;
	public static final int caveFallDistance = 192;
	public static final int ladderDescentDuration = 1800;
	public static final int ladderDescentDistance = 64;

	private static final String fallStartKey = "fallStartTime";
	private static final String diggingStartKey = "diggingStartTime";
	private static final String ladderTravelTimeKey = "ladderTravelTime";
	private static final String ladderTravelStartedKey = "ladderTravelStarted";
	private static final String safeDescentKey = "safeDescent";
	private static final String caveFallStartKey = "caveFallStartTime";
	private static final String ladderDescentStartKey = "ladderDescentStartTime";
	private static final String ladderDescentTravelStartedKey = "ladderDescentTravelStarted";
	private static final Map<PlayerMob, Long> clientFallStartTimes = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<PlayerMob, Long> clientCaveFallStartTimes = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<PlayerMob, Long> clientLadderDescentStartTimes = Collections.synchronizedMap(new WeakHashMap<>());
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

	public static boolean isCaveFalling(PlayerMob player) {
		boolean falling = player != null && player.buffManager.hasBuff(DeepHoleCaveFallBuff.stringID);
		if (!falling && player != null) clientCaveFallStartTimes.remove(player);
		return falling;
	}

	public static boolean isLadderDescending(PlayerMob player) {
		boolean descending = player != null && player.buffManager.hasBuff(DeepHoleLadderDescentBuff.stringID);
		if (!descending && player != null) clientLadderDescentStartTimes.remove(player);
		return descending;
	}

	public static boolean isTransitioning(PlayerMob player) {
		return isDigging(player) || isOccupied(player) || isCaveFalling(player) || isLadderDescending(player);
	}

	public static boolean usesSurfaceHoleMaskOffset(PlayerMob player) {
		return isOccupied(player) || isLadderDescending(player);
	}

	public static boolean isTransitionAt(Level level, int tileX, int tileY) {
		if (level == null) return false;
		return level.entityManager.players.streamInRegionsInTileRange(tileX * 32 + 16, tileY * 32 + 16, 1)
				.anyMatch(player -> player.getTileX() == tileX && player.getTileY() == tileY && isTransitioning(player));
	}

	public static boolean isShaftTransitionAt(Level level, int tileX, int tileY) {
		if (isTransitionAt(level, tileX, tileY)) return true;
		if (level == null || !level.isServer()) return false;

		LevelIdentifier linkedIdentifier;
		if (level.getIdentifier().equals(LevelIdentifier.SURFACE_IDENTIFIER)) {
			linkedIdentifier = LevelIdentifier.CAVE_IDENTIFIER;
		}
		else if (level.getIdentifier().equals(LevelIdentifier.CAVE_IDENTIFIER)) {
			linkedIdentifier = LevelIdentifier.SURFACE_IDENTIFIER;
		}
		else {
			return false;
		}

		if (!level.getServer().world.levelExists(linkedIdentifier)) return false;
		Level linkedLevel = level.getServer().world.getLevel(linkedIdentifier);
		return isTransitionAt(linkedLevel, tileX, tileY);
	}

	public static boolean fillDeepHole(Level surfaceLevel, int tileX, int tileY) {
		if (surfaceLevel == null || !surfaceLevel.isServer()) return false;
		if (surfaceLevel.getTileID(tileX, tileY) != TileRegistry.getTileID(DeepHoleTile.stringID)) return false;
		if (isShaftTransitionAt(surfaceLevel, tileX, tileY)) return false;

		int surfaceObjectID = surfaceLevel.getObjectID(tileX, tileY);
		int customLadderID = ObjectRegistry.getObjectID(HoleCaveLadderObject.stringID);
		int legacyLadderID = ObjectRegistry.getObjectID("ladderdown");
		boolean hadLadder = surfaceObjectID == customLadderID || surfaceObjectID == legacyLadderID;

		if (hadLadder) {
			surfaceLevel.setObject(tileX, tileY, 0);
			surfaceLevel.replaceObjectEntity(tileX, tileY);
			surfaceLevel.getServer().network.sendToClientsWithTile(
					new PacketChangeObject(surfaceLevel, 0, tileX, tileY, 0),
					surfaceLevel,
					tileX,
					tileY
			);
		}

		removeCaveShaftObject(surfaceLevel, tileX, tileY);

		surfaceLevel.setTile(tileX, tileY, TileRegistry.dirtID);
		surfaceLevel.sendTileUpdatePacket(tileX, tileY);
		surfaceLevel.getLevelTile(tileX, tileY).checkAround();
		surfaceLevel.getLevelObject(tileX, tileY).checkAround();
		return hadLadder;
	}

	private static void removeCaveShaftObject(Level surfaceLevel, int tileX, int tileY) {
		if (!surfaceLevel.getServer().world.levelExists(LevelIdentifier.CAVE_IDENTIFIER)) return;

		Level caveLevel = surfaceLevel.getServer().world.getLevel(LevelIdentifier.CAVE_IDENTIFIER);
		caveLevel.regionManager.ensureTileIsLoaded(tileX, tileY);

		int objectID = caveLevel.getObjectID(tileX, tileY);
		int ladderID = ObjectRegistry.getObjectID(HoleCaveLadderUpObject.stringID);
		int lightID = ObjectRegistry.getObjectID(DeepHoleCeilingLightObject.stringID);
		int legacyLadderID = ObjectRegistry.getObjectID("ladderup");
		if (objectID != ladderID && objectID != lightID && objectID != legacyLadderID) return;

		caveLevel.setObject(tileX, tileY, 0);
		caveLevel.replaceObjectEntity(tileX, tileY);
		caveLevel.getServer().network.sendToClientsWithTile(
				new PacketChangeObject(caveLevel, 0, tileX, tileY, 0),
				caveLevel,
				tileX,
				tileY
		);
		caveLevel.getLevelObject(tileX, tileY).checkAround();
	}

	public static boolean isFullyHidden(PlayerMob player) {
		return (isOccupied(player) && getFallProgress(player) >= 1.0F)
				|| (isLadderDescending(player) && getLadderDescentProgress(player) >= 1.0F);
	}

	public static boolean hasDrawYOffset(PlayerMob player) {
		return isOccupied(player) || isCaveFalling(player) || isLadderDescending(player);
	}

	public static int getDrawYOffset(PlayerMob player) {
		if (isCaveFalling(player)) {
			float progress = getCaveFallProgress(player);
			return -Math.round(caveFallDistance * (1.0F - progress));
		}
		if (isLadderDescending(player)) return Math.round(ladderDescentDistance * getLadderDescentProgress(player));
		if (isOccupied(player)) return Math.round(fallDistance * getFallProgress(player));
		return 0;
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

	public static boolean tryClientLadderInteract(PlayerMob player, int levelX, int levelY) {
		Level level = player.getLevel();
		if (level == null || !level.isClient() || isTransitioning(player)) return false;

		InventoryItem selected = player.getSelectedItem();
		if (selected != null && DirtPileItem.stringID.equals(selected.item.getStringID())) return false;

		int tileX = GameMath.getTileCoordinate(levelX);
		int tileY = GameMath.getTileCoordinate(levelY);
		if (level.getTileID(tileX, tileY) != TileRegistry.getTileID(DeepHoleTile.stringID)) return false;
		if (!hasSafeHoleLadder(level, tileX, tileY)) return false;

		LevelObject ladder = level.getLevelObject(tileX, tileY);
		if (!ladder.isInInteractRange(player) || !ladder.canInteract(player)) return false;

		boolean mouseOverLadder = false;
		for (ObjectHoverHitbox box : ladder.getHoverHitboxes()) {
			if (box.contains(levelX, levelY)) {
				mouseOverLadder = true;
				break;
			}
		}
		if (!mouseOverLadder) return false;

		level.getClient().network.sendPacket(new PacketHoleCaveLadderInteract(tileX, tileY));
		return true;
	}

	public static boolean tryClientInteract(PlayerMob player, int levelX, int levelY) {
		Level level = player.getLevel();
		if (level == null || !level.isClient()) return false;
		if (isTransitioning(player)) return true;

		InventoryItem selected = player.getSelectedItem();
		if (selected == null || !(selected.item instanceof PickaxeToolItem)) return false;

		int tileX = GameMath.getTileCoordinate(levelX);
		int tileY = GameMath.getTileCoordinate(levelY);
		if (tileX != player.getTileX() || tileY != player.getTileY()) return false;
		if (!isPlayerInSinkingArea(level, tileX, tileY, player)) return false;

		boolean canEnter = level.getTileID(tileX, tileY) == TileRegistry.getTileID(ShallowHoleTile.stringID)
				&& isDiggableHoleObject(level, tileX, tileY);
		if (!canEnter) return false;

		level.getClient().network.sendPacket(new PacketDeepHoleInteract(tileX, tileY, true));
		return true;
	}

	public static boolean tryServerInteract(PlayerMob player, int tileX, int tileY, boolean enter) {
		Level level = player.getLevel();
		if (level == null || !level.isServer()) return false;
		if (isTransitioning(player)) return false;
		if (!enter) return false;
		if (tileX != player.getTileX() || tileY != player.getTileY()) return false;
		if (!isPlayerInSinkingArea(level, tileX, tileY, player)) return false;

		InventoryItem selected = player.getSelectedItem();
		if (selected == null || !(selected.item instanceof PickaxeToolItem)) return false;

		return startDigging(level, tileX, tileY, player);
	}

	public static void tick(PlayerMob player) {
		if (player == null) return;

		if (isCaveFalling(player)) {
			tickCaveFall(player);
			return;
		}

		if (isLadderDescending(player)) {
			tickLadderDescent(player);
			return;
		}

		if (!isDigging(player)) {
			ClientDiggingState state = clientDiggingStates.remove(player);
			if (state != null && player.getLevel() != null && player.getLevel().isClient()) {
				player.forceEndAttack();
			}

			Level level = player.getLevel();
			if (level != null && level.isServer() && isOccupied(player)) {
				tickServerPendingLadderTravel(player);
			}
			else if (level != null && level.isServer() && !isTransitioning(player)) {
				tryStartOpenShaftFall(player);
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
		if (isTransitionAt(level, tileX, tileY)) return false;
		if (level.getTileID(tileX, tileY) != TileRegistry.getTileID(ShallowHoleTile.stringID)) return false;
		if (!isDiggableHoleObject(level, tileX, tileY)) return false;

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
				|| !isDiggableHoleObject(level, tileX, tileY)
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
		boolean safeDescent = hasHoleCaveLadder(level, tileX, tileY);

		level.setTile(tileX, tileY, TileRegistry.getTileID(DeepHoleTile.stringID));
		level.sendTileUpdatePacket(tileX, tileY);

		player.buffManager.removeBuff(DeepHoleDiggingBuff.stringID, true);
		if (safeDescent) {
			startSafeLadderDescent(level, tileX, tileY, player);
		}
		else {
			startUnsafeSurfaceFall(level, player);
		}
	}

	private static void tickServerPendingLadderTravel(PlayerMob player) {
		Level level = player.getLevel();
		ActiveBuff buff = player.buffManager.getBuff(DeepHoleHiddenBuff.stringID);
		if (level == null || buff == null) return;
		if (buff.getGndData().getLong(ladderTravelStartedKey, 0L) != 0L) return;

		long travelTime = buff.getGndData().getLong(ladderTravelTimeKey, Long.MAX_VALUE);
		if (level.getWorldEntity().getTime() < travelTime) return;

		buff.getGndData().setLong(ladderTravelStartedKey, 1L);
		useUnsafeShaftDescent(player);
	}

	public static boolean tryStartSafeLadderDescent(PlayerMob player, int tileX, int tileY) {
		Level level = player == null ? null : player.getLevel();
		if (level == null || !level.isServer() || isTransitioning(player)) return false;
		if (!level.isTileWithinBounds(tileX, tileY)) return false;
		if (level.getTileID(tileX, tileY) != TileRegistry.getTileID(DeepHoleTile.stringID)) return false;

		LevelObject ladder = level.getLevelObject(tileX, tileY);
		if (!ladder.isInInteractRange(player) || !ladder.canInteract(player)) return false;
		if (!ensureCustomHoleLadder(level, tileX, tileY)) return false;

		startSafeLadderDescent(level, tileX, tileY, player);
		return true;
	}

	private static void startSafeLadderDescent(Level level, int tileX, int tileY, PlayerMob player) {
		player.dismount();
		player.forceEndAttack();
		player.endAttackHandler(true);
		if (player.serverFollowersManager != null) player.serverFollowersManager.clearSummonFocus();

		player.setPos(tileX * 32.0F + 16.0F, tileY * 32.0F + 16.0F, true);
		player.setDir(0);
		player.sendMovementPacket(true);
		player.moveX = 0.0F;
		player.moveY = 0.0F;
		player.dx = 0.0F;
		player.dy = 0.0F;

		ActiveBuff buff = new ActiveBuff(DeepHoleLadderDescentBuff.stringID, player, Integer.MAX_VALUE, null);
		buff.getGndData().setLong(ladderDescentStartKey, level.getWorldEntity().getTime());
		buff.getGndData().setLong(ladderDescentTravelStartedKey, 0L);
		player.buffManager.addBuff(buff, true);
	}

	private static void startUnsafeSurfaceFall(Level level, PlayerMob player) {
		startUnsafeSurfaceFall(level, player, ladderTravelDelay);
	}

	private static void startUnsafeSurfaceFall(Level level, PlayerMob player, int travelDelay) {
		long now = level.getWorldEntity().getTime();
		ActiveBuff buff = new ActiveBuff(DeepHoleHiddenBuff.stringID, player, Integer.MAX_VALUE, null);
		buff.getGndData().setLong(fallStartKey, now);
		buff.getGndData().setLong(ladderTravelTimeKey, now + travelDelay);
		buff.getGndData().setLong(ladderTravelStartedKey, 0L);
		buff.getGndData().setBoolean(safeDescentKey, false);
		player.buffManager.addBuff(buff, true);
	}

	private static void tryStartOpenShaftFall(PlayerMob player) {
		Level level = player.getLevel();
		if (level == null || !level.isServer()) return;

		int tileX = player.getTileX();
		int tileY = player.getTileY();
		if (level.getTileID(tileX, tileY) != TileRegistry.getTileID(DeepHoleTile.stringID)) return;
		if (!isPlayerInSinkingArea(level, tileX, tileY, player)) return;

		if (hasSafeHoleLadder(level, tileX, tileY)) {
			if (ensureCustomHoleLadder(level, tileX, tileY)) startSafeLadderDescent(level, tileX, tileY, player);
			return;
		}

		player.dismount();
		player.forceEndAttack();
		player.endAttackHandler(true);
		if (player.serverFollowersManager != null) player.serverFollowersManager.clearSummonFocus();

		player.setPos(tileX * 32.0F + 16.0F, tileY * 32.0F + 16.0F, true);
		player.sendMovementPacket(true);
		startUnsafeSurfaceFall(level, player, fallDuration);
	}

	private static void tickLadderDescent(PlayerMob player) {
		Level level = player.getLevel();
		if (level == null) return;

		player.moveX = 0.0F;
		player.moveY = 0.0F;
		player.dx = 0.0F;
		player.dy = 0.0F;
		player.setDir(0);

		if (!level.isServer()) return;
		ActiveBuff buff = player.buffManager.getBuff(DeepHoleLadderDescentBuff.stringID);
		if (buff == null) return;

		long startTime = buff.getGndData().getLong(ladderDescentStartKey, level.getWorldEntity().getTime());
		if (level.getWorldEntity().getTime() - startTime < ladderDescentDuration) return;
		if (buff.getGndData().getLong(ladderDescentTravelStartedKey, 0L) != 0L) return;

		buff.getGndData().setLong(ladderDescentTravelStartedKey, 1L);
		useSafeLadderDescent(player);
	}

	private static void useSafeLadderDescent(PlayerMob player) {
		if (!player.isServerClient()) return;

		ServerClient client = player.getServerClient();
		Level sourceLevel = player.getLevel();
		if (sourceLevel == null || !sourceLevel.isServer()) return;

		int tileX = player.getTileX();
		int tileY = player.getTileY();
		int ladderUpID = ObjectRegistry.getObjectID(HoleCaveLadderUpObject.stringID);
		int vanillaLadderUpID = ObjectRegistry.getObjectID("ladderup");

		if (client.achievementsLoaded()) {
			client.achievements().SPELUNKER.markCompleted(client);
		}

		client.changeLevelCheck(LevelIdentifier.CAVE_IDENTIFIER, (destinationLevel) -> {
			destinationLevel.regionManager.ensureTilesAreLoaded(tileX, tileY, tileX, tileY);
			int existingObjectID = destinationLevel.getObjectID(tileX, tileY);
			if (existingObjectID != ladderUpID && existingObjectID != vanillaLadderUpID) {
				GameMessage error = destinationLevel.preventsLadderPlacement(tileX, tileY);
				if (error != null) {
					client.sendChatMessage(error);
					player.buffManager.removeBuff(DeepHoleLadderDescentBuff.stringID, true);
					return new TeleportResult(false, (Point)null);
				}
			}

			destinationLevel.regionManager.ensureTileIsLoaded(tileX, tileY);
			if (destinationLevel.getObjectID(tileX, tileY) != ladderUpID) {
				LadderDownObjectEntity.clearAndPlaceLadder(
						sourceLevel.getServer(),
						destinationLevel,
						tileX,
						tileY,
						ladderUpID,
						true
				);
			}

			Point destination = PortalObjectEntity.getTeleportDestinationAroundObject(
					destinationLevel,
					player,
					tileX,
					tileY,
					true
			);
			if (destination == null) {
				destination = new Point(tileX * 32 + 16, tileY * 32 + 16);
			}

			player.buffManager.removeBuff(DeepHoleLadderDescentBuff.stringID, true);
			client.newStats.ladders_used.increment(1);

			LadderDownObjectEntity portal = new LadderDownObjectEntity(
					"deepholetemporary",
					sourceLevel,
					tileX,
					tileY,
					LevelIdentifier.CAVE_IDENTIFIER,
					ObjectRegistry.getObjectID(HoleCaveLadderObject.stringID),
					ladderUpID
			);
			portal.runClearMobs(destinationLevel, tileX, tileY);
			return new TeleportResult(true, destination);
		}, true);
	}

	private static void useUnsafeShaftDescent(PlayerMob player) {
		if (!player.isServerClient()) return;

		ServerClient client = player.getServerClient();
		Level sourceLevel = player.getLevel();
		if (sourceLevel == null || !sourceLevel.isServer()) return;

		int tileX = player.getTileX();
		int tileY = player.getTileY();

		if (client.achievementsLoaded()) {
			client.achievements().SPELUNKER.markCompleted(client);
		}

		client.changeLevelCheck(LevelIdentifier.CAVE_IDENTIFIER, (destinationLevel) -> {
			destinationLevel.regionManager.ensureTilesAreLoaded(tileX, tileY, tileX, tileY);

			LadderDownObjectEntity.clearAndPlaceLadder(
					sourceLevel.getServer(),
					destinationLevel,
					tileX,
					tileY,
					ObjectRegistry.getObjectID(DeepHoleCeilingLightObject.stringID),
					true
			);

			Point destination = PortalObjectEntity.getTeleportDestinationAroundObject(
					destinationLevel,
					player,
					tileX,
					tileY,
					true
			);
			if (destination == null) {
				destination = new Point(tileX * 32 + 16, tileY * 32 + 16);
			}

			player.buffManager.removeBuff(DeepHoleHiddenBuff.stringID, true);
			ActiveBuff caveFallBuff = new ActiveBuff(DeepHoleCaveFallBuff.stringID, player, Integer.MAX_VALUE, null);
			caveFallBuff.getGndData().setLong(caveFallStartKey, 0L);
			player.buffManager.addBuff(caveFallBuff, true);

			PortalObjectEntity portal = new PortalObjectEntity(
					sourceLevel,
					"deepholetemporary",
					tileX,
					tileY,
					LevelIdentifier.CAVE_IDENTIFIER,
					tileX,
					tileY
			);
			portal.runClearMobs(destinationLevel, tileX, tileY);
			return new TeleportResult(true, destination);
		}, true);
	}

	private static void tickCaveFall(PlayerMob player) {
		Level level = player.getLevel();
		if (level == null) return;

		player.moveX = 0.0F;
		player.moveY = 0.0F;
		player.dx = 0.0F;
		player.dy = 0.0F;

		if (!level.isServer()) return;

		ActiveBuff buff = player.buffManager.getBuff(DeepHoleCaveFallBuff.stringID);
		if (buff == null) return;

		long startTime = buff.getGndData().getLong(caveFallStartKey, 0L);
		if (startTime == 0L) {
			startTime = level.getWorldEntity().getTime();
			buff.getGndData().setLong(caveFallStartKey, startTime);
		}
		if (level.getWorldEntity().getTime() - startTime < caveFallDuration) return;

		player.buffManager.removeBuff(DeepHoleCaveFallBuff.stringID, true);
		player.setHealth(0);
	}

	private static float getCaveFallProgress(PlayerMob player) {
		if (!isCaveFalling(player)) return 1.0F;

		Level level = player.getLevel();
		if (level == null) return 1.0F;

		long elapsed;
		if (level.isClient()) {
			Long startTime = clientCaveFallStartTimes.get(player);
			long now = System.nanoTime() / 1000000L;
			if (startTime == null) {
				clientCaveFallStartTimes.put(player, now);
				startTime = now;
			}
			elapsed = Math.max(0L, now - startTime);
		}
		else {
			ActiveBuff buff = player.buffManager.getBuff(DeepHoleCaveFallBuff.stringID);
			if (buff == null) return 1.0F;
			long startTime = buff.getGndData().getLong(caveFallStartKey, level.getWorldEntity().getTime());
			elapsed = Math.max(0L, level.getWorldEntity().getTime() - startTime);
		}

		float progress = Math.min(1.0F, (float)elapsed / (float)caveFallDuration);
		return progress * progress;
	}

	private static float getLadderDescentProgress(PlayerMob player) {
		if (!isLadderDescending(player)) return 1.0F;

		Level level = player.getLevel();
		if (level == null) return 1.0F;

		long elapsed;
		if (level.isClient()) {
			Long startTime = clientLadderDescentStartTimes.get(player);
			long now = System.nanoTime() / 1000000L;
			if (startTime == null) {
				clientLadderDescentStartTimes.put(player, now);
				startTime = now;
			}
			elapsed = Math.max(0L, now - startTime);
		}
		else {
			ActiveBuff buff = player.buffManager.getBuff(DeepHoleLadderDescentBuff.stringID);
			if (buff == null) return 1.0F;
			long startTime = buff.getGndData().getLong(ladderDescentStartKey, level.getWorldEntity().getTime());
			elapsed = Math.max(0L, level.getWorldEntity().getTime() - startTime);
		}

		return Math.min(1.0F, (float)elapsed / (float)ladderDescentDuration);
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

	private static boolean isDiggableHoleObject(Level level, int tileX, int tileY) {
		int objectID = level.getObjectID(tileX, tileY);
		return objectID == 0 || objectID == ObjectRegistry.getObjectID(HoleCaveLadderObject.stringID);
	}

	private static boolean hasHoleCaveLadder(Level level, int tileX, int tileY) {
		return level.getObjectID(tileX, tileY) == ObjectRegistry.getObjectID(HoleCaveLadderObject.stringID);
	}

	private static boolean hasSafeHoleLadder(Level level, int tileX, int tileY) {
		int objectID = level.getObjectID(tileX, tileY);
		return objectID == ObjectRegistry.getObjectID(HoleCaveLadderObject.stringID)
				|| objectID == ObjectRegistry.getObjectID("ladderdown");
	}

	private static boolean ensureCustomHoleLadder(Level level, int tileX, int tileY) {
		int customLadderID = ObjectRegistry.getObjectID(HoleCaveLadderObject.stringID);
		int objectID = level.getObjectID(tileX, tileY);
		if (objectID == customLadderID) return true;
		if (objectID != ObjectRegistry.getObjectID("ladderdown")) return false;

		level.setObject(tileX, tileY, customLadderID);
		level.replaceObjectEntity(tileX, tileY);
		level.getServer().network.sendToClientsWithTile(
				new PacketChangeObject(level, 0, tileX, tileY, customLadderID),
				level,
				tileX,
				tileY
		);
		return true;
	}

	private static boolean isPlayerInSinkingArea(Level level, int tileX, int tileY, PlayerMob player) {
		if (!(level.getTile(tileX, tileY) instanceof ShallowHoleTile)) return false;
		return ((ShallowHoleTile)level.getTile(tileX, tileY)).isMobInSinkingArea(player);
	}

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
