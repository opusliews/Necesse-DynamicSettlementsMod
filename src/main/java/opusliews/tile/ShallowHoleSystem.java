package opusliews.tile;

import necesse.engine.registries.TileRegistry;
import necesse.engine.util.GameMath;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.shovelToolItem.ShovelToolItem;
import necesse.level.maps.Level;
import necesse.level.maps.hudManager.floatText.ChatBubbleText;
import opusliews.item.DirtPileItem;
import opusliews.item.TreasureShovelItem;
import opusliews.network.PacketDigShallowHole;

public final class ShallowHoleSystem {
	public static final int[][] surroundingOffsets = {
			{0, -1},
			{1, -1},
			{1, 0},
			{1, 1},
			{0, 1},
			{-1, 1},
			{-1, 0},
			{-1, -1}
	};

	private ShallowHoleSystem() {
	}

	public static boolean tryClientDig(PlayerMob player, int levelX, int levelY) {
		Level level = player.getLevel();
		if (level == null || !level.isClient()) return false;

		InventoryItem item = player.getSelectedItem();
		if (item == null || !(item.item instanceof ShovelToolItem)) return false;

		int tileX = GameMath.getTileCoordinate(levelX);
		int tileY = GameMath.getTileCoordinate(levelY);
		if (!isHoleInteraction(level, tileX, tileY, player, item)) return false;

		if (level.isDeepCaveLevel()) {
			level.hudManager.addElement(new ChatBubbleText(player, "That ain't happening"));
			return true;
		}

		if (level.isBasicCaveLevel() && !TreasureShovelItem.stringID.equals(item.item.getStringID())) {
			level.hudManager.addElement(new ChatBubbleText(player, "I'm gonna need a better shovel"));
			return true;
		}

		if (!canDig(level, tileX, tileY, player, item)) return false;
		level.getClient().network.sendPacket(new PacketDigShallowHole(tileX, tileY));
		return true;
	}

	public static boolean tryServerDig(PlayerMob player, int tileX, int tileY) {
		Level level = player.getLevel();
		if (level == null || !level.isServer()) return false;

		InventoryItem item = player.getSelectedItem();
		if (item == null || !(item.item instanceof ShovelToolItem)) return false;
		if (level.isDeepCaveLevel()) return false;
		if (level.isBasicCaveLevel() && !TreasureShovelItem.stringID.equals(item.item.getStringID())) return false;
		if (!canDig(level, tileX, tileY, player, item)) return false;

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

	public static int getFillTileID(Level level, int tileX, int tileY) {
		return TileRegistry.dirtID;
	}

	private static boolean isHoleInteraction(Level level, int tileX, int tileY, PlayerMob player, InventoryItem item) {
		if (!level.isTileWithinBounds(tileX, tileY) || level.isProtected(tileX, tileY)) return false;
		if (level.getTileID(tileX, tileY) != getDigTileID(level, tileX, tileY)) return false;
		if (!"air".equals(level.getObject(tileX, tileY).getStringID())) return false;

		ShovelToolItem shovel = (ShovelToolItem)item.item;
		return shovel.isTileInRange(level, tileX, tileY, player, null, item);
	}

	private static boolean canDig(Level level, int tileX, int tileY, PlayerMob player, InventoryItem item) {
		if (!isHoleInteraction(level, tileX, tileY, player, item)) return false;
		return !hasAdjacentPit(level, tileX, tileY);
	}

	private static int getDigTileID(Level level, int tileX, int tileY) {
		return TileRegistry.dirtID;
	}

	public static boolean hasAdjacentPit(Level level, int tileX, int tileY) {
		for (int[] offset : surroundingOffsets) {
			int checkX = tileX + offset[0];
			int checkY = tileY + offset[1];
			if (CharcoalPitSystem.isPitTile(level, checkX, checkY)) return true;
		}
		return false;
	}
}
