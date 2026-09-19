package opusliews.item;

import necesse.engine.localization.Localization;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.engine.registries.TileRegistry;
import necesse.engine.util.GameBlackboard;
import necesse.engine.util.GameMath;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.itemAttacker.ItemAttackSlot;
import necesse.entity.mobs.itemAttacker.ItemAttackerMob;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.ItemInteractAction;
import necesse.inventory.item.matItem.MatItem;
import necesse.level.maps.Level;
import opusliews.tile.ShallowHoleTile;
import opusliews.tile.ShallowHoleSystem;

public class DirtPileItem extends MatItem implements ItemInteractAction {
	public static final String stringID = "dirtpile";
	private static final int fillRange = 100;

	public DirtPileItem() {
		super(500, Item.Rarity.NORMAL);
	}

	@Override
	public ListGameTooltips getTooltips(InventoryItem item, PlayerMob perspective, GameBlackboard blackboard) {
		ListGameTooltips tooltips = super.getTooltips(item, perspective, blackboard);
		tooltips.add(Localization.translate("itemtooltip", "dirtpiletip"));
		return tooltips;
	}

	@Override
	public boolean canLevelInteract(Level level, int x, int y, ItemAttackerMob attackerMob, InventoryItem item) {
		if (!(attackerMob instanceof PlayerMob)) {
			return false;
		}

		int tileX = GameMath.getTileCoordinate(x);
		int tileY = GameMath.getTileCoordinate(y);

		if (!level.isTileWithinBounds(tileX, tileY) || level.isProtected(tileX, tileY)) {
			return false;
		}

		if (level.getTileID(tileX, tileY) != TileRegistry.getTileID(ShallowHoleTile.stringID)) {
			return false;
		}

		return attackerMob.getPositionPoint().distance(tileX * 32.0 + 16.0, tileY * 32.0 + 16.0) <= fillRange;
	}

	@Override
	public InventoryItem onLevelInteract(
			Level level,
			int x,
			int y,
			ItemAttackerMob attackerMob,
			int attackHeight,
			InventoryItem item,
			ItemAttackSlot slot,
			int seed,
			GNDItemMap mapContent
	) {
		if (!canLevelInteract(level, x, y, attackerMob, item)) {
			return item;
		}

		int tileX = GameMath.getTileCoordinate(x);
		int tileY = GameMath.getTileCoordinate(y);
		level.setTile(tileX, tileY, ShallowHoleSystem.getFillTileID(level, tileX, tileY));

		if (level.isServer()) {
			level.sendTileUpdatePacket(tileX, tileY);
			level.getLevelTile(tileX, tileY).checkAround();
			level.getLevelObject(tileX, tileY).checkAround();
		}

		item.setAmount(item.getAmount() - 1);
		return item;
	}

	@Override
	public boolean overridesObjectInteract(Level level, PlayerMob player, InventoryItem item) {
		return true;
	}
}
