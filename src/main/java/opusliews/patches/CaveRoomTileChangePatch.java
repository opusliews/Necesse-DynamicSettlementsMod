package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.packet.PacketTileDamage;
import necesse.entity.DamagedObjectEntity;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementCaveBedSystem;
import opusliews.tile.DirtDensityLevelData;

@ModMethodPatch(target = Level.class, name = "setTile", arguments = {int.class, int.class, int.class})
public class CaveRoomTileChangePatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This Level level,
			@Advice.Argument(0) int tileX,
			@Advice.Argument(1) int tileY,
			@Advice.Argument(value = 2, readOnly = false) int tileID,
			@Advice.Local("dirtDensityTileSubstituted") boolean dirtDensityTileSubstituted) {
		int requestedTileID = tileID;
		tileID = DirtDensityLevelData.prepareTileReplacement(level, tileX, tileY, tileID);
		dirtDensityTileSubstituted = tileID != requestedTileID;
	}

	@Advice.OnMethodExit
	public static void onExit(@Advice.This Level level,
			@Advice.Argument(0) int tileX,
			@Advice.Argument(1) int tileY,
			@Advice.Local("dirtDensityTileSubstituted") boolean dirtDensityTileSubstituted) {
		// Some vanilla tile-removal paths synchronize the destroyed tile they expected
		// (normally dirt), rather than the tile ID actually left in Level after our
		// substitution. Explicitly synchronize only when density logic changed the ID.
		if (dirtDensityTileSubstituted && level != null && level.isServer()) {
			// Vanilla normally clears tile damage when its PacketTileDestroyed is processed.
			// Dirt -> thin-dirt substitution can make that packet see a different tile ID
			// and skip the clear, leaving the damage overlay from the removed covering tile.
			// Clear only the tile-layer damage here; object-layer damage at this coordinate is preserved.
			DamagedObjectEntity damaged = level.entityManager.getDamagedObjectEntity(tileX, tileY);
			if (damaged != null && damaged.tileDamage != 0) {
				damaged.updateTileDamage(0, true);
				level.getServer().network.sendToClientsWithTile(
						new PacketTileDamage(level, tileX, tileY, -1, 0, 0, true, false, false, 0, 0),
						level, tileX, tileY);
			}
			level.sendTileUpdatePacket(tileX, tileY);
		}
		SettlementCaveBedSystem.onCaveRoomLevelChanged(level, tileX, tileY);
	}
}
