package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.container.BedContainer;
import net.bytebuddy.asm.Advice;
import opus.sleep.SleepSimulationSystem;

@ModMethodPatch(target = BedContainer.class, name = "tick", arguments = {})
public class DaytimeBedSleepPatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This BedContainer container) {
		SleepSimulationSystem.prepareBedTick(container);
	}
}
