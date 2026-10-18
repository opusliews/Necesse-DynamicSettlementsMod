package opusliews.patches;

import java.awt.Point;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.camera.GameCamera;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.placeableItem.StonePlaceableItem;
import necesse.inventory.item.placeableItem.PlaceableItem;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.earlygame.StoneControllerAim;

/** StonePlaceableItem normally suppresses the reticle and targets gravel-placement tiles. */
public class StoneControllerAimPatch {
	@ModMethodPatch(target = PlaceableItem.class, name = "getControllerAttackLevelPos", arguments = {Level.class, float.class, float.class, PlayerMob.class, InventoryItem.class})
	public static class Position {
		@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
		public static boolean enter(@Advice.This PlaceableItem item) { return item instanceof StonePlaceableItem; }

		@Advice.OnMethodExit
		public static void exit(@Advice.This PlaceableItem item, @Advice.Argument(0) Level level, @Advice.Argument(1) float x, @Advice.Argument(2) float y, @Advice.Argument(3) PlayerMob player, @Advice.Return(readOnly = false) Point result) {
			if (item instanceof StonePlaceableItem) result = StoneControllerAim.getTarget(level, x, y, player, false);
		}
	}

	@ModMethodPatch(target = PlaceableItem.class, name = "drawControllerAimPos", arguments = {GameCamera.class, Level.class, PlayerMob.class, InventoryItem.class})
	public static class Reticle {
		@Advice.OnMethodExit
		public static void exit(@Advice.This PlaceableItem stone, @Advice.Argument(0) GameCamera camera, @Advice.Argument(1) Level level, @Advice.Argument(2) PlayerMob player) {
			if (stone instanceof StonePlaceableItem) StoneControllerAim.drawAim(stone, camera, level, player, false);
		}
	}
}
