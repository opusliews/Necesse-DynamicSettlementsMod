package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.DamagedObjectEntity;
import necesse.entity.ObjectDamageResult;
import net.bytebuddy.asm.Advice;
import opusliews.damage.DamageRepairLevelData;

@ModMethodPatch(
		target = DamagedObjectEntity.class,
		name = "doObjectDamageOverride",
		arguments = {int.class, int.class, boolean.class}
)
public class DamagedObjectEntityObjectOverridePatch {
	@Advice.OnMethodExit
	static void onExit(
			@Advice.This DamagedObjectEntity entity,
			@Advice.Argument(0) int objectLayerID,
			@Advice.Return ObjectDamageResult result
	) {
		if (!entity.isServer() || result == null) {
			return;
		}

		DamageRepairLevelData data = DamageRepairLevelData.get(entity.getLevel(), true);
		data.recordDamage(result, objectLayerID);
	}
}
