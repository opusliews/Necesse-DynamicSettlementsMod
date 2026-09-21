package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.level.gameObject.TreeStumpObject;
import necesse.level.gameObject.furniture.ChairObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.DSItemRegistry;
import opusliews.earlygame.CrudeAnvilFeature;

@ModMethodPatch(
		target = ChairObject.class,
		name = "interact",
		arguments = {Level.class, int.class, int.class, PlayerMob.class}
)
public class TreeStumpCrudeAnvilPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.Argument(0) Level level,
			@Advice.Argument(1) int tileX,
			@Advice.Argument(2) int tileY,
			@Advice.Argument(3) PlayerMob player
	) {
		if (!(level.getObject(tileX, tileY) instanceof TreeStumpObject)) return false;

		InventoryItem selected = player.getSelectedItem();
		if (selected == null || !DSItemRegistry.thickIronPlateStringID.equals(selected.item.getStringID())) return false;
		if (!level.isServer()) return true;
		if (!player.isServerClient()) return true;

		int crudeAnvilID = CrudeAnvilFeature.getMatchingCrudeAnvilID(level.getObject(tileX, tileY).getStringID());
		if (crudeAnvilID < 0) return true;

		ServerClient client = player.getServerClient();
		if (client == null) return true;
		if (player.getInv().removeItems(selected.item, 1, false, false, false, false, "use") <= 0) return true;

		level.setObject(tileX, tileY, crudeAnvilID, 0);
		level.sendObjectUpdatePacket(0, tileX, tileY);
		level.getLevelObject(tileX, tileY).checkAround();
		return true;
	}
}
