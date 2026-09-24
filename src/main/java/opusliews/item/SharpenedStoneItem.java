package opusliews.item;

import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.engine.util.GameMath;
import necesse.entity.mobs.GameDamage;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.itemAttacker.ItemAttackSlot;
import necesse.entity.mobs.itemAttacker.ItemAttackerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.SurfaceGrassObject;
import necesse.level.maps.Level;

public class SharpenedStoneItem extends Item {
	public static final String stringID = "sharpenedstone";
	private static final String hitCountKey = "sharpenedStoneTreeHits";
	private static final int requiredHits = 5;
	private static final int useRange = 100;

	public SharpenedStoneItem() {
		super(500);
		rarity = Rarity.NORMAL;
		setItemCategory("materials", "stone");
	}

	@Override
	public String canAttack(Level level, int x, int y, ItemAttackerMob attackerMob, InventoryItem item) {
		if (!(attackerMob instanceof PlayerMob)) {
			return "";
		}
		PlayerMob player = (PlayerMob)attackerMob;
		int tileX = GameMath.getTileCoordinate(x);
		int tileY = GameMath.getTileCoordinate(y);
		if (!level.isTileWithinBounds(tileX, tileY) || level.isProtected(tileX, tileY)) {
			return "";
		}

		GameObject object = level.getObject(tileX, tileY);
		if (object == null || (!object.isTree && !(object instanceof SurfaceGrassObject))) {
			return "";
		}

		return player.getPositionPoint().distance(tileX * 32.0 + 16.0, tileY * 32.0 + 16.0) <= useRange ? null : "";
	}

	@Override
	public InventoryItem onAttack(
			Level level,
			int x,
			int y,
			ItemAttackerMob attackerMob,
			int attackHeight,
			InventoryItem item,
			ItemAttackSlot slot,
			int animAttack,
			int seed,
			GNDItemMap mapContent
	) {
		if (!(attackerMob instanceof PlayerMob)) {
			return item;
		}

		int tileX = GameMath.getTileCoordinate(x);
		int tileY = GameMath.getTileCoordinate(y);
		if (!level.isTileWithinBounds(tileX, tileY)
				|| level.isProtected(tileX, tileY)
				|| (!level.getObject(tileX, tileY).isTree
					&& !(level.getObject(tileX, tileY) instanceof SurfaceGrassObject))) {
			return item;
		}

		if (level.isClient()) {
			if (level.getObject(tileX, tileY).isTree) {
				level.getObject(tileX, tileY).playDamageSound(level, tileX, tileY, true);
			}
		}

		if (!level.isServer()) {
			return item;
		}

		if (level.getObject(tileX, tileY).isTree) {
			int hits = item.getGndData().getInt(hitCountKey, 0) + 1;

			if (hits >= requiredHits) {
				item.setAmount(item.getAmount() - 1);
				item.getGndData().setInt(hitCountKey, 0);
				InventoryItem shaft = new InventoryItem("woodenshaft", 1);
				level.entityManager.pickups.add(shaft.getPickupEntity(level, tileX * 32.0F + 16.0F, tileY * 32.0F + 16.0F));
			} else {
				item.getGndData().setInt(hitCountKey, hits);
			}
		}
		else {
			level.getLevelObject(tileX, tileY).attackThrough(new GameDamage(10000.0F), attackerMob);
		}

		return item;
	}
}
