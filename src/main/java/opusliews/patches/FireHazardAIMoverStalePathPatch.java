package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.util.AIMover;
import net.bytebuddy.asm.Advice;
import opusliews.tile.FireHazardPathing;

@ModMethodPatch(
		target = AIMover.class,
		name = "tick",
		arguments = {Mob.class}
)
public class FireHazardAIMoverStalePathPatch {
	@Advice.OnMethodEnter
	public static void onEnter(
			@Advice.This AIMover mover,
			@Advice.Argument(0) Mob mob
	) {
		FireHazardPathing.stopStalePathIfFireAppeared(mover, mob);
	}
}
