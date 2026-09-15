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
		name = "getHarvestSplitItems",
		arguments = {}
)
public class ApiaryHoneycombSplitHarvestPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This AbstractBeeHiveObjectEntity hive,
			@Advice.Return ArrayList result
	) {
		if (!(hive instanceof ApiaryObjectEntity)) {
			return;
		}

		for (int i = 0; i < hive.getHoneyAmount(); i++) {
			result.add(new InventoryItem(honeycombFragmentStringID));		}
	}
}
