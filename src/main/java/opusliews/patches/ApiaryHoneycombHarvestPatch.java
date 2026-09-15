package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.objectEntity.AbstractBeeHiveObjectEntity;
import necesse.entity.objectEntity.ApiaryObjectEntity;
import necesse.inventory.InventoryItem;
import net.bytebuddy.asm.Advice;

import java.util.ArrayList;

import static opusliews.DSItemRegistry.honeycombFragmentStringID;

@ModMethodPatch(
		target = AbstractBeeHiveObjectEntity.class,
		name = "getHarvestItems",
		arguments = {}
)
public class ApiaryHoneycombHarvestPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This AbstractBeeHiveObjectEntity hive,
			@Advice.Return ArrayList result
	) {
		if (!(hive instanceof ApiaryObjectEntity)) {
			return;
		}

		int honey = hive.getHoneyAmount();
		if (honey > 0) {
			result.add(new InventoryItem(honeycombFragmentStringID, honey));
		}
	}
}
