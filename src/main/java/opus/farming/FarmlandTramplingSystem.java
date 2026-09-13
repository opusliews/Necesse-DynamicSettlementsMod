package opus.farming;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import necesse.engine.network.packet.PacketChangeObjects;
import necesse.engine.network.packet.PacketChangeTile;
import necesse.engine.registries.MobRegistry;
import necesse.engine.registries.ObjectLayerRegistry;
import necesse.engine.registries.TileRegistry;
import necesse.engine.util.GameRandom;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.friendly.FriendlyRopableMob;
import necesse.entity.mobs.hostile.HostileMob;
import necesse.level.gameObject.SeedObject;
import necesse.level.maps.Level;

public final class FarmlandTramplingSystem {
	private static final float trampleChance = 0.30F;
	private static int farmlandTileID;
	private static int dirtTileID;
	private static int chickenID;
	private static int roosterID;
	private static int penguinID;
	private static int polarBearID;
	private static int crocodileID;
	private static int thrumboID;
	private static boolean registryIDsInitialized;
	private static final Map<Mob, TrampleState> states = Collections.synchronizedMap(new WeakHashMap<>());

	private FarmlandTramplingSystem() {
	}

	public static void serverTick(Mob mob) {
		if (mob == null || !mob.isServer() || mob.removed() || mob.getLevel() == null) {
			return;
		}

		ensureRegistryIDs();

		if (mob.dx == 0.0F && mob.dy == 0.0F) {
			return;
		}

		int tileX = mob.getTileX();
		int tileY = mob.getTileY();
		TrampleState state;

		synchronized (states) {
			state = states.get(mob);
			if (state == null) {
				state = new TrampleState(tileX, tileY, canTrample(mob));
				states.put(mob, state);
				return;
			}

			if (state.tileX == tileX && state.tileY == tileY) {
				return;
			}

			state.tileX = tileX;
			state.tileY = tileY;
		}

		if (!state.canTrample) {
			return;
		}

		Level level = mob.getLevel();
		if (level.getTileID(tileX, tileY) != farmlandTileID) {
			return;
		}

		if (!GameRandom.globalRandom.getChance(trampleChance)) {
			return;
		}

		trample(level, tileX, tileY);
	}

	private static synchronized void ensureRegistryIDs() {
		if (registryIDsInitialized) {
			return;
		}

		farmlandTileID = TileRegistry.getTileID("farmland");
		dirtTileID = TileRegistry.getTileID("dirttile");
		chickenID = MobRegistry.getMobID("chicken");
		roosterID = MobRegistry.getMobID("rooster");
		penguinID = MobRegistry.getMobID("penguin");
		polarBearID = MobRegistry.getMobID("polarbear");
		crocodileID = MobRegistry.getMobID("crocodile");
		thrumboID = MobRegistry.getMobID("thrumbo");
		registryIDsInitialized = true;
	}

	private static boolean canTrample(Mob mob) {
		if (mob.isFlying()) {
			return false;
		}

		if (mob instanceof HostileMob) {
			return true;
		}

		int mobID = mob.getID();
		if (mob instanceof FriendlyRopableMob) {
			return mobID != chickenID
					&& mobID != roosterID
					&& mobID != penguinID;
		}

		return mobID == polarBearID
				|| mobID == crocodileID
				|| mobID == thrumboID;
	}

	private static void trample(Level level, int tileX, int tileY) {
		boolean removedCrop = false;
		for (int layerID : ObjectLayerRegistry.getLayerIDs()) {
			if (level.getObject(layerID, tileX, tileY) instanceof SeedObject) {
				level.objectLayer.setObject(layerID, tileX, tileY, 0);
				level.objectLayer.setObjectRotation(layerID, tileX, tileY, 0);
				level.objectLayer.setIsPlayerPlaced(layerID, tileX, tileY, false);
				removedCrop = true;
			}
		}

		level.setTile(tileX, tileY, dirtTileID);
		level.tileLayer.setIsPlayerPlaced(tileX, tileY, false);
		level.getLevelTile(tileX, tileY).checkAround();
		level.getLevelObject(tileX, tileY).checkAround();

		if (removedCrop) {
			level.getServer().network.sendToClientsWithTile(
					new PacketChangeObjects(level, tileX, tileY),
					level,
					tileX,
					tileY
			);
		}

		level.getServer().network.sendToClientsWithTile(
				new PacketChangeTile(level, tileX, tileY),
				level,
				tileX,
				tileY
		);
	}

	private static final class TrampleState {
		private int tileX;
		private int tileY;
		private final boolean canTrample;

		private TrampleState(int tileX, int tileY, boolean canTrample) {
			this.tileX = tileX;
			this.tileY = tileY;
			this.canTrample = canTrample;
		}
	}
}
