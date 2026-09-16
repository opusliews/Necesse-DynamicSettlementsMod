package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.engine.sound.SoundEffect;
import necesse.engine.sound.SoundManager;
import necesse.engine.util.GameMath;
import necesse.engine.util.GameRandom;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.itemAttacker.ItemAttackSlot;
import necesse.entity.mobs.itemAttacker.ItemAttackerMob;
import necesse.gfx.GameResources;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.placeableItem.StonePlaceableItem;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.DynamicSettlements;
import opusliews.earlygame.EarlyGameStoneSystem;
import opusliews.item.SharpenedStoneItem;

@ModMethodPatch(
		target = StonePlaceableItem.class,
		name = "onAttack",
		arguments = {
				Level.class, int.class, int.class, ItemAttackerMob.class, int.class,
				InventoryItem.class, ItemAttackSlot.class, int.class, int.class, GNDItemMap.class
		}
)
public class StoneKnappingPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.This StonePlaceableItem stoneItem,
			@Advice.Argument(0) Level level,
			@Advice.Argument(1) int levelX,
			@Advice.Argument(2) int levelY,
			@Advice.Argument(3) ItemAttackerMob attackerMob,
			@Advice.Argument(5) InventoryItem item,
			@Advice.Argument(8) int seed
	) {
		if (!(attackerMob instanceof PlayerMob)) {
			return false;
		}

		int tileX = GameMath.getTileCoordinate(levelX);
		int tileY = GameMath.getTileCoordinate(levelY);
		if (!level.isTileWithinBounds(tileX, tileY) || level.isProtected(tileX, tileY)) {
			return false;
		}

		GameObject target = level.getObject(tileX, tileY);

		if (!EarlyGameStoneSystem.isRockObject(target)) {
			return false;
		}

		PlayerMob player = (PlayerMob)attackerMob;
		if (!stoneItem.isInPlaceRange(level, tileX * 32 + 16, tileY * 32 + 16, player, null, item)) {
			return true;
		}

		if (level.isClient()) {
			double pitch = GameRandom.globalRandom.nextDouble(0.8,1.2);
			SoundManager.playSound(DynamicSettlements.stoneTapSound, SoundEffect.effect(tileX * 32.0F + 16.0F, tileY * 32.0F + 16.0F).pitch((float)pitch).volume(1.0F));
			SoundManager.playSound(GameResources.blunthit, SoundEffect.effect(tileX * 32.0F + 16.0F, tileY * 32.0F + 16.0F).pitch(1.15F).volume(0.35F));
		}

		if (level.isServer()) {
			GameRandom random = new GameRandom(seed);
			boolean sharpened = random.getChance(0.2F);
			boolean extraStone = random.getChance(0.3F);

			if (sharpened && item.getAmount() > 0) {
				item.setAmount(item.getAmount() - 1);
				drop(level, tileX, tileY, new InventoryItem(SharpenedStoneItem.stringID, 1));
			}

			if (extraStone) {
				drop(level, tileX, tileY, new InventoryItem(item.item.getStringID(), 1));
			}
		}

		return true;
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Enter boolean handled,
			@Advice.Argument(5) InventoryItem item,
			@Advice.Return(readOnly = false) InventoryItem result
	) {
		if (handled) {
			result = item;
		}
	}

	public static void drop(Level level, int tileX, int tileY, InventoryItem item) {
		level.entityManager.pickups.add(item.getPickupEntity(level, tileX * 32.0F + 16.0F, tileY * 32.0F + 16.0F));
	}

	@ModMethodPatch(
			target = Item.class,
			name = "getAttackCooldownTime",
			arguments = {InventoryItem.class, ItemAttackerMob.class}
	)
	public static class rockAttackCooldownTimePatch {
		@Advice.OnMethodExit
		public static void onExit(
				@Advice.This Item item,
				@Advice.Return(readOnly = false) int result
		) {
			if (item instanceof StonePlaceableItem || item instanceof SharpenedStoneItem) {
				result = 1000;
			}
		}
	}
}
