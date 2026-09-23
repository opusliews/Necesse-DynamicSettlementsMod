package opusliews.object;

import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.toolItem.ToolType;
import necesse.level.gameObject.TorchObject;
import necesse.level.maps.Level;
import opusliews.item.CrudeTorchItem;
import opusliews.torch.CrudeTorchLevelData;

import java.awt.*;
import java.util.ArrayList;

public class CrudeTorchObject extends TorchObject {
	public static final String stringID = "crudetorch";

	public CrudeTorchObject() {
		super(stringID, ToolType.ALL, new Color(180, 138, 79), 50.0F, 0.2F);
		this.stackSize = 500;
	}

	@Override
	public void loadTextures() {
		super.loadTextures();
		this.texture_off = this.texture;
		this.texture_decor_off = this.texture_decor;
	}

	@Override
	public boolean isActive(Level level, int layerID, int tileX, int tileY) {
		return true;
	}

	@Override
	public Item generateNewObjectItem() {
		return new CrudeTorchItem(this);
	}

	@Override
	public ArrayList<InventoryItem> getObjectDroppedItems(Level level, int layerID, int tileX, int tileY, String purpose) {
		ArrayList<InventoryItem> drops = new ArrayList<>();
		CrudeTorchLevelData data = CrudeTorchLevelData.get(level, false);
		CrudeTorchLevelData.TrackedTorch tracked = data == null ? null : data.getTrackedTorch(layerID, tileX, tileY);
		if (tracked == null || level.getWorldTime() >= tracked.expireWorldTime) return drops;

		InventoryItem drop = new InventoryItem(stringID, 1);
		CrudeTorchItem.setRunningState(drop, tracked.expireWorldTime, tracked.duration);
		drops.add(drop);
		return drops;
	}
}
