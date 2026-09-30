package opusliews.settler;

import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.LocalMessage;
import necesse.gfx.HumanLook;
import necesse.gfx.drawOptions.human.HumanDrawOptions;
import necesse.inventory.InventoryItem;
import necesse.level.maps.levelData.settlementData.settler.Settler;

public class CarpenterSettler extends Settler {
	public CarpenterSettler() {
		super("carpenterhuman");
	}

	@Override
	public GameMessage getAcquireTip() {
		return new LocalMessage("settlement", "carpentertip");
	}

	@Override
	public void setDefaultArmor(HumanDrawOptions drawOptions, int settlerSeed, HumanLook look, boolean customLook) {
		drawOptions.helmet(new InventoryItem("safetyglasses"));
		drawOptions.chestplate(new InventoryItem("carpentershirt"));
		drawOptions.boots(new InventoryItem("carpenterboots"));
	}
}
