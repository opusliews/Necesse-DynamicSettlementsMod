package opusliews.tile;

import necesse.engine.registries.TileRegistry;
import necesse.engine.util.GameMath;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.shovelToolItem.ShovelToolItem;
import necesse.level.maps.Level;
import opusliews.item.DirtPileItem;
import opusliews.network.PacketDigShallowHole;

public final class ShallowHoleSystem {
	private static final int[][] cardinalOffsets = {
			{0, -1},
			{1, 0},
			{0, 1},
			{-1, 0}
	};

	private ShallowHoleSystem() {
	}

	public static boolean tryClientDig(PlayerMob player, int levelX, int levelY) {
		Level level = player.getLevel();

		if (level == null || !level.isClient()) {
			return false;
		}

		InventoryItem item = player.getSelectedItem();

		if (item == null || !(item.item instanceof ShovelToolItem)) {
			return false;
		}

		int tileX = GameMath.getTileCoordinate(levelX);
		int tileY = GameMath.getTileCoordinate(levelY);

		if (!canDig(level, tileX, tileY, player, item)) {
			return false;
		}

		level.getClient().network.sendPacket(new PacketDigShallowHole(tileX, tileY));
		return true;
	}

	public static boolean tryServerDig(PlayerMob player, int tileX, int tileY) {
		Level level = player.getLevel();

		if (level == null || !level.isServer()) {
			return false;
		}

		InventoryItem item = player.getSelectedItem();

		if (item == null || !(item.item instanceof ShovelToolItem)) {
			return false;
		}

		if (!canDig(level, tileX, tileY, player, item)) {
			return false;
		}

		level.setTile(tileX, tileY, TileRegistry.getTileID(ShallowHoleTile.stringID));
		level.sendTileUpdatePacket(tileX, tileY);

		InventoryItem dirtPile = new InventoryItem(DirtPileItem.stringID, 2);
		level.entityManager.pickups.add(dirtPile.getPickupEntity(level, tileX * 32.0F + 16.0F, tileY * 32.0F + 16.0F));
		return true;
	}

	public static boolean isInShallowHole(PlayerMob player) {
		Level level = player.getLevel();
		return level != null && level.getTileID(player.getTileX(), player.getTileY()) == TileRegistry.getTileID(ShallowHoleTile.stringID);
	}

	private static boolean canDig(Level level, int tileX, int tileY, PlayerMob player, InventoryItem item) {
		if (!level.isTileWithinBounds(tileX, tileY)) {
			return false;
		}

		if (level.getTileID(tileX, tileY) != TileRegistry.dirtID) {
			return false;
		}

		if (!"air".equals(level.getObject(tileX, tileY).getStringID())) {
			return false;
		}

		if (hasCardinalShallowHole(level, tileX, tileY)) {
			return false;
		}

		ShovelToolItem shovel = (ShovelToolItem)item.item;
		return shovel.isTileInRange(level, tileX, tileY, player, null, item);
	}

	private static boolean hasCardinalShallowHole(Level level, int tileX, int tileY) {
		int shallowHoleTileID = TileRegistry.getTileID(ShallowHoleTile.stringID);

		for (int[] offset : cardinalOffsets) {
			int checkX = tileX + offset[0];
			int checkY = tileY + offset[1];

			if (level.isTileWithinBounds(checkX, checkY) && level.getTileID(checkX, checkY) == shallowHoleTileID) {
				return true;
			}
		}

		return false;
	}
}
