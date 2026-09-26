package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.save.SaveData;
import necesse.entity.objectEntity.InventoryObjectEntity;
import net.bytebuddy.asm.Advice;
import opusliews.logging.InventoryPersistenceDebug;

@ModMethodPatch(target = InventoryObjectEntity.class, name = "addSaveData", arguments = {SaveData.class})
public class InventoryObjectPersistenceSaveDebugPatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This InventoryObjectEntity entity) {
		InventoryPersistenceDebug.logPlayerPlacedContainer("CONTAINER_SAVE", entity);
	}
}
