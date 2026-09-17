package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.container.object.CraftingStationContainer;
import necesse.level.gameObject.TreeStumpObject;
import necesse.level.gameObject.furniture.ChairObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.DynamicSettlements;
import opusliews.item.SharpenedStoneItem;

@ModMethodPatch(
		target = ChairObject.class,
		name = "interact",
		arguments = {Level.class, int.class, int.class, PlayerMob.class}
)
public class TreeStumpCrudeWorkbenchPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.Argument(0) Level level,
			@Advice.Argument(1) int tileX,
			@Advice.Argument(2) int tileY,
			@Advice.Argument(3) PlayerMob player
	) {
		if (!(level.getObject(tileX, tileY) instanceof TreeStumpObject)) {
			return false;
		}

		InventoryItem selected = player.getSelectedItem();
		if (selected == null || !SharpenedStoneItem.stringID.equals(selected.item.getStringID())) {
			return false;
		}

		if (level.isServer() && player.isServerClient()) {
			CraftingStationContainer.openAndSendContainer(
					DynamicSettlements.crudeWorkbenchContainerID,
					player.getServerClient(),
					level,
					tileX,
					tileY
			);
		}

		return true;
	}
}
