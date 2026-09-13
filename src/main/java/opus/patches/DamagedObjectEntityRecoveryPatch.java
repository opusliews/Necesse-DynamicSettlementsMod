package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.DamagedObjectEntity;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(target = DamagedObjectEntity.class, name = "tickDamageRecovery", arguments = {})
public class DamagedObjectEntityRecoveryPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	static boolean onEnter() {
		return true;
	}
}
