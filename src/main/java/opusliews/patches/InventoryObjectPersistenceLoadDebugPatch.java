package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.save.LoadData;
import necesse.entity.objectEntity.InventoryObjectEntity;
import net.bytebuddy.asm.Advice;
import opusliews.logging.InventoryPersistenceDebug;

@ModMethodPatch(target = InventoryObjectEntity.class, name = "applyLoadData", arguments = {LoadData.class})
public class InventoryObjectPersistenceLoadDebugPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This InventoryObjectEntity entity) {
		InventoryPersistenceDebug.logPlayerPlacedContainer("CONTAINER_LOAD", entity);
	}
}
