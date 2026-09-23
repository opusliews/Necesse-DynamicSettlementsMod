package opusliews.patches;

import java.awt.Rectangle;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementStorageConfigForm;
import net.bytebuddy.asm.Advice;
import opusliews.stock.SettlementStockUI;

@ModMethodPatch(
		target = SettlementStorageConfigForm.class,
		name = "draw",
		arguments = {TickManager.class, PlayerMob.class, Rectangle.class}
)
public class SettlementStorageConfigFormDrawPatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This SettlementStorageConfigForm form) {
		SettlementStockUI.tickForm(form);
	}
}
