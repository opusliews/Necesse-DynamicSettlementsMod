package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.container.mob.ShopContainer;
import net.bytebuddy.asm.Advice;
import opusliews.clay.ClayPackageSystem;

@ModMethodPatch(
		target = ShopContainer.class,
		name = "handleWorkItemsAction",
		arguments = {ShopContainer.WorkItemsAction.class}
)
public class ClayPackageRetrieveInventoryPatch {
	@Advice.OnMethodEnter
	public static void onEnter(
			@Advice.This ShopContainer container,
			@Advice.Argument(0) ShopContainer.WorkItemsAction action
	) {
		if (action == ShopContainer.WorkItemsAction.RECEIVE) {
			ClayPackageSystem.unpackAllPackages(container.humanShop.workInventory);
		}
	}
}
