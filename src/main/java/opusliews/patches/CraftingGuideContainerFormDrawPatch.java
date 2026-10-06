package opusliews.patches;

import java.awt.Rectangle;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.client.Client;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.forms.presets.containerComponent.item.CraftingGuideContainerForm;
import necesse.inventory.container.Container;
import necesse.inventory.container.item.CraftingGuideContainer;
import net.bytebuddy.asm.Advice;
import opusliews.craftingguide.CraftingGuideExtension;

@ModMethodPatch(target = CraftingGuideContainerForm.class, name = "draw", arguments = {TickManager.class, PlayerMob.class, Rectangle.class})
public class CraftingGuideContainerFormDrawPatch {
	@Advice.OnMethodEnter
	public static void onEnter(
			@Advice.This CraftingGuideContainerForm form,
			@Advice.FieldValue("client") Client client,
			@Advice.FieldValue("container") Container container,
			@Advice.FieldValue(value = "itemID", readOnly = false) int itemID) {
		if (container instanceof CraftingGuideContainer) {
			CraftingGuideExtension.setupForm(form, client, (CraftingGuideContainer)container);
		}
		itemID = CraftingGuideExtension.beforeDraw(form);
	}
}
