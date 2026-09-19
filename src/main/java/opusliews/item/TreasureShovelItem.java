package opusliews.item;

import necesse.engine.localization.Localization;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.item.toolItem.shovelToolItem.CustomShovelToolItem;

public class TreasureShovelItem extends CustomShovelToolItem {
	public static final String stringID = "treasureshovel";

	public TreasureShovelItem() {
		super(500, 95, 0.0F, 15, 50, 50, 400);
	}

	@Override
	public void addToolTooltips(ListGameTooltips tooltips) {
		super.addToolTooltips(tooltips);
		tooltips.add(Localization.translate("itemtooltip", "treasureshovel"));
	}
}

