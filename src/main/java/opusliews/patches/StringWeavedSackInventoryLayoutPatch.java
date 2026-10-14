package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.client.Client;
import necesse.entity.objectEntity.InventoryObjectEntity;
import necesse.gfx.forms.position.FormFixedPosition;
import necesse.gfx.forms.presets.containerComponent.object.OEInventoryContainerForm;
import necesse.inventory.container.object.OEInventoryContainer;
import net.bytebuddy.asm.Advice;
import opusliews.object.StringWeavedSackObject;

@ModMethodPatch(target = OEInventoryContainerForm.class, name = "<init>", arguments = {Client.class, OEInventoryContainer.class})
public class StringWeavedSackInventoryLayoutPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This OEInventoryContainerForm form, @Advice.Argument(1) OEInventoryContainer container) {
		if (!(container.oeInventory instanceof InventoryObjectEntity)) return;
		InventoryObjectEntity entity = (InventoryObjectEntity) container.oeInventory;
		if (entity.getLevelObject() == null || !StringWeavedSackObject.stringID.equals(entity.getLevelObject().object.getStringID())) return;
		if (form.slots == null || form.slots.length != 10) return;
		int firstY = form.slots[0].getPosition().getY();
		for (int i = 0; i < form.slots.length; i++) {
			form.slots[i].setPosition(new FormFixedPosition(4 + (i % 5) * 40, firstY + (i / 5) * 40));
		}
		form.inventoryForm.setHeight(form.inventoryForm.getHeight() + 40);
	}
}
