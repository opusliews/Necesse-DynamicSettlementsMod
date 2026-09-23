package opusliews.item;

import java.util.function.Consumer;
import necesse.engine.GameState;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.engine.network.server.ServerClient;
import necesse.engine.world.GameClock;
import necesse.engine.world.WorldSettings;
import necesse.entity.Entity;
import necesse.entity.TileEntity;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.Inventory;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.TickItem;
import necesse.inventory.item.placeableItem.objectItem.TorchObjectItem;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import opusliews.durability.ItemDurabilitySystem;
import opusliews.torch.CrudeTorchLevelData;

public class CrudeTorchItem extends TorchObjectItem implements TickItem {
	public static final String expireWorldTimeGndKey = "crudeTorchExpireWorldTime";
	public static final String durationGndKey = "crudeTorchDuration";
	public static final int durabilitySteps = 1000;

	public CrudeTorchItem(GameObject object) {
		super(object, false);
	}

	@Override
	public boolean canCombineItem(Level level, PlayerMob player, InventoryItem me, InventoryItem them, String purpose) {
		if (isRunning(me) || isRunning(them)) return false;
		return super.canCombineItem(level, player, me, them, purpose);
	}

	@Override
	public boolean onPlaceObject(GameObject object, Level level, int layerID, int tileX, int tileY, int rotation, ServerClient client, InventoryItem item) {
		long duration = getDuration(item);
		if (duration <= 0L) duration = CrudeTorchLevelData.getDayDurationMillis(level);

		long expireWorldTime = getExpireWorldTime(item);
		if (expireWorldTime <= 0L) expireWorldTime = level.getWorldTime() + duration;

		boolean success = super.onPlaceObject(object, level, layerID, tileX, tileY, rotation, client, item);
		if (success && level.isServer()) {
			CrudeTorchLevelData.get(level, true).registerTorch(layerID, tileX, tileY, expireWorldTime, duration);
		}
		return success;
	}

	@Override
	public void tick(Inventory inventory, int slot, InventoryItem item, GameClock clock, GameState state, Entity entity, TileEntity tileEntity, WorldSettings worldSettings, Consumer setItem) {
		long expireWorldTime = getExpireWorldTime(item);
		if (expireWorldTime <= 0L) return;

		long currentWorldTime = clock.getWorldTime();
		if (currentWorldTime >= expireWorldTime) {
			setItem.accept(null);
			return;
		}

		long duration = Math.max(1L, getDuration(item));
		long remaining = Math.max(0L, expireWorldTime - currentWorldTime);
		int durability = (int)Math.ceil(durabilitySteps * Math.min(1.0, remaining / (double)duration));
		ItemDurabilitySystem.setDurability(item, Math.max(1, durability));
	}

	public static boolean isRunning(InventoryItem item) {
		return getExpireWorldTime(item) > 0L;
	}

	public static long getExpireWorldTime(InventoryItem item) {
		if (item == null) return 0L;
		GNDItemMap gnd = item.getGndData();
		return gnd.getLong(expireWorldTimeGndKey, 0L);
	}

	public static long getDuration(InventoryItem item) {
		if (item == null) return 0L;
		return item.getGndData().getLong(durationGndKey, 0L);
	}

	public static void setRunningState(InventoryItem item, long expireWorldTime, long duration) {
		if (item == null) return;
		item.getGndData().setLong(expireWorldTimeGndKey, expireWorldTime);
		item.getGndData().setLong(durationGndKey, Math.max(1L, duration));
	}
}
