package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.toolItem.pickaxeToolItem.PickaxeToolItem;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;

import java.awt.*;

/** Target the same hole tile as the controller interaction handler. */
@ModMethodPatch(target = Item.class, name = "getControllerAttackLevelPos",
		arguments = {Level.class, float.class, float.class, PlayerMob.class, InventoryItem.class})
public class ControllerHoleReticle {
	@Advice.OnMethodExit
	public static void exit(@Advice.This Item item, @Advice.Argument(0) Level level,
			@Advice.Argument(1) float aimX, @Advice.Argument(2) float aimY,
			@Advice.Argument(3) PlayerMob player, @Advice.Return(readOnly = false) Point result) {
		if (level == null || player == null || item == null) return;
		// Pickaxes must keep vanilla RT targeting. Deep-hole digging is LT only.
		if (item instanceof PickaxeToolItem) return;
		if (!ControllerHoleActions.Handler.isPitMaterial(item)) return;
		Point target = ControllerHoleActions.Handler.findHole(level, player, aimX, aimY);
		if (target != null) result = target;
	}
}
