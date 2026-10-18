package opusliews.patches;

import necesse.engine.Settings;
import necesse.engine.input.Input;
import necesse.engine.input.controller.ControllerInput;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.registries.TileRegistry;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.DrawOptions;
import necesse.gfx.ui.ControllerInteractTarget;
import necesse.gfx.ui.HUD;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.toolItem.ToolDamageItem;
import necesse.inventory.item.toolItem.ToolType;
import necesse.inventory.item.toolItem.pickaxeToolItem.PickaxeToolItem;
import necesse.level.gameObject.TreeStumpObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.DSItemRegistry;
import opusliews.deephole.DeepHoleSystem;
import opusliews.earlygame.CrudeAnvilFeature;
import opusliews.forge.MeltablePartSystem;
import opusliews.item.FirestarterItem;
import opusliews.logging.Logging;
import opusliews.tile.*;

import java.awt.*;
import java.awt.geom.Point2D;

/** Provide a controller LT interaction target for pit operations.
 * Unlike the red attack reticle, this target also controls Necesse's white LT selection outline.
 */
@ModMethodPatch(target = PlayerMob.class, name = "getControllerInteractTarget",
		arguments = {boolean.class, int.class, GameCamera.class})
public class ControllerHoleInteractTargetPatch {
	@Advice.OnMethodExit
	public static void exit(@Advice.This PlayerMob player,
			@Advice.Argument(0) boolean onlyItemInteract,
			@Advice.Argument(2) GameCamera camera,
			@Advice.Return(readOnly = false) ControllerInteractTarget result) {
		ControllerInteractTarget custom = Handler.getTarget(player, onlyItemInteract, camera);
		if (custom != null) result = custom;
	}

	public static class Handler {
		/** Pick occupied pits for extraction, or exposed dirt for the existing dig action. */
		public static Point findShovelTarget(Level level, PlayerMob player, float aimX, float aimY) {
			return findAlongAim(level, player, aimX, aimY, 1);
		}

		/** Firestarter LT should select ignitable covered charcoal and firing pits. */
		public static Point findCrudeAnvilStump(Level level, PlayerMob player, float aimX, float aimY) {
			return findAlongAim(level, player, aimX, aimY, 3);
		}

		public static Point findIgnitablePit(Level level, PlayerMob player, float aimX, float aimY) {
			return findAlongAim(level, player, aimX, aimY, 2);
		}

		private static Point findAlongAim(Level level, PlayerMob player, float aimX, float aimY, int mode) {
			float length = (float)Math.sqrt(aimX * aimX + aimY * aimY);
			if (length < 0.001F) return null;
			float dx = aimX / length;
			float dy = aimY / length;
			int lastX = Integer.MIN_VALUE;
			int lastY = Integer.MIN_VALUE;
			for (int distance = 16; distance <= 100; distance += 4) {
				int x = (int)Math.floor((player.x + dx * distance) / 32.0F);
				int y = (int)Math.floor((player.y + dy * distance) / 32.0F);
				if (x == lastX && y == lastY) continue;
				lastX = x;
				lastY = y;
				if (!level.isTileWithinBounds(x, y)) continue;
				if (player.getPositionPoint().distance(x * 32.0 + 16.0, y * 32.0 + 16.0) > 100.0) continue;
				int tile = level.getTileID(x, y);
				boolean matches;
				if (mode == 1) {
					matches = CharcoalPitSystem.isPitTile(level, x, y)
							|| (!Settings.smartMining && (tile == TileRegistry.dirtID
							|| tile == TileRegistry.getTileID(ThinDirtTile.stringID))
							&& "air".equals(level.getObject(x, y).getStringID()));
				} else if (mode == 3) {
					matches = level.getObject(x, y) instanceof TreeStumpObject
							&& CrudeAnvilFeature.getMatchingCrudeAnvilID(level.getObject(x, y).getStringID()) >= 0;
				} else {
					matches = tile == TileRegistry.getTileID(CoveredCharcoalPitTile.stringID)
							|| tile == TileRegistry.getTileID(FiringPitLogTile.stringID);
					for (int count = 1; !matches && count <= FiringPitTile.maxItems; count++) {
						matches = tile == TileRegistry.getTileID(FiringPitTile.getStringID(count));
					}
				}
				if (matches) return new Point(x * 32 + 16, y * 32 + 16);
			}
			return null;
		}

		public static ControllerInteractTarget getTarget(final PlayerMob player, boolean onlyItemInteract, final GameCamera camera) {
			if (player == null || !Input.lastInputIsController || ControllerInput.isCursorVisible()) return null;
			final Level level = player.getLevel();
			if (level == null || !level.isClient() || player.isAttacking || player.getAttackHandler() != null) return null;
			InventoryItem selected = player.getSelectedItem();
			if (selected == null) return null;
			Item item = selected.item;
			boolean pitMaterial = ControllerHoleActions.Handler.isPitMaterial(item);
			boolean shovel = item instanceof ToolDamageItem
					&& ((ToolDamageItem)item).getToolType(selected) == ToolType.SHOVEL;
			boolean pickaxe = item instanceof PickaxeToolItem;
			boolean firestarter = item instanceof FirestarterItem;
			boolean thickPlate = DSItemRegistry.thickIronPlateStringID.equals(item.getStringID())
					&& MeltablePartSystem.canUseAsComponent(selected);
			if (!pitMaterial && !shovel && !pickaxe && !firestarter && !thickPlate) return null;
			final Point target;
			if (pickaxe) {
				int x = player.getTileX();
				int y = player.getTileY();
				if (level.getTileID(x, y) != TileRegistry.getTileID(ShallowHoleTile.stringID)) return null;
				target = new Point(x * 32 + 16, y * 32 + 16);
			} else {
				Point2D.Float direction = player.getControllerAimDir();
				if (shovel) target = findShovelTarget(level, player, direction.x, direction.y);
				else if (firestarter) target = findIgnitablePit(level, player, direction.x, direction.y);
				else if (thickPlate) target = findCrudeAnvilStump(level, player, direction.x, direction.y);
				else target = ControllerHoleActions.Handler.findHole(level, player, direction.x, direction.y);
				if (target == null) return null;
			}
			final int tileX = Math.floorDiv(target.x, 32);
			final int tileY = Math.floorDiv(target.y, 32);
			if (!level.isTileWithinBounds(tileX, tileY)) return null;
			final boolean deepDig = pickaxe;
			return new ControllerInteractTarget() {
				@Override public void runInteract() {
					if (Logging.logEnabled) Logging.logMessage("[ControllerHoleLT] RUN item="
							+ (player.getSelectedItem() == null ? "none" : player.getSelectedItem().item.getStringID())
							+ " tile=" + tileX + "," + tileY);
					if (deepDig) {
						DeepHoleSystem.tryClientInteract(player, target.x, target.y);
					} else {
						// Reuse the exact right-click handler; it checks item eligibility and sends the server packet.
						player.runClientInteract(target.x, target.y, false);
					}
				}
				@Override public DrawOptions getDrawOptions() {
					return HUD.tileBoundOptions(camera, Settings.UI.controllerFocusBoundsColor, true,
							new Rectangle(tileX, tileY, 1, 1));
				}
				@Override public void onCurrentlyFocused() { }
			};
		}
	}
}
