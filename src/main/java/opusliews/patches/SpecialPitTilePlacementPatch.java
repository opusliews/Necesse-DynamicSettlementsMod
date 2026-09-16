package opusliews.patches;

import java.awt.geom.Line2D;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.placeableItem.tileItem.TileItem;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.tile.CharcoalPitSystem;

@ModMethodPatch(
		target = TileItem.class,
		name = "canPlace",
		arguments = {Level.class, int.class, int.class, PlayerMob.class, Line2D.class, InventoryItem.class, GNDItemMap.class}
)
public class SpecialPitTilePlacementPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Argument(0) Level level,
			@Advice.Argument(1) int levelX,
			@Advice.Argument(2) int levelY,
			@Advice.Return(readOnly = false) String result
	) {
		int tileX = necesse.engine.util.GameMath.getTileCoordinate(levelX);
		int tileY = necesse.engine.util.GameMath.getTileCoordinate(levelY);

		if (CharcoalPitSystem.isPitTile(level, tileX, tileY)) {
			result = "wrongtile";
		}
	}
}
