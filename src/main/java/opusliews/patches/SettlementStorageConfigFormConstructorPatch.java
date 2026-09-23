package opusliews.patches;

import java.awt.Point;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.modLoader.annotations.ModConstructorPatch;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementStorageConfigForm;
import necesse.inventory.Inventory;
import necesse.inventory.itemFilter.ItemCategoriesFilter;
import net.bytebuddy.asm.Advice;
import opusliews.stock.SettlementStockUI;
import opusliews.logging.Logging;

@ModConstructorPatch(
		target = SettlementStorageConfigForm.class,
		arguments = {
				String.class,
				int.class,
				int.class,
				Point.class,
				Client.class,
				Inventory.class,
				GameMessage.class,
				ItemCategoriesFilter.class,
				int.class
		}
)
public class SettlementStorageConfigFormConstructorPatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.Argument(value = 1, readOnly = false) int width) {
		if (Logging.logEnabled) Logging.logMessage("[StockUI] SettlementStorageConfigForm constructor width before=" + width + " extra=" + SettlementStockUI.extraWidth);
		width += SettlementStockUI.extraWidth;
	}

	@Advice.OnMethodExit
	public static void onExit(@Advice.This SettlementStorageConfigForm form) {
		if (Logging.logEnabled) Logging.logMessage("[StockUI] SettlementStorageConfigForm constructor exit tile=" + form.tile.x + "," + form.tile.y + " filterWidth=" + form.filterForm.getWidth());
		SettlementStockUI.attach(form);
	}
}
