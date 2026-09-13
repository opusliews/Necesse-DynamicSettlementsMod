package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.world.WorldEntity;
import net.bytebuddy.asm.Advice;
import opus.sleep.SleepWarningSystem;

@ModMethodPatch(target = WorldEntity.class, name = "preventSleep", arguments = {})
public class WorldEntityRaidPreventSleepPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter() {
		return SleepWarningSystem.shouldSuppressRaidPreventSleep();
	}
}
