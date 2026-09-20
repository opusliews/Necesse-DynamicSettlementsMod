package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.world.WorldEntity;
import net.bytebuddy.asm.Advice;
import opusliews.worldgen.StartingFiremoneFeature;

@ModMethodPatch(target = WorldEntity.class, name = "initServer", arguments = {boolean.class})
public class StartingFiremoneWorldPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This WorldEntity worldEntity) {
		StartingFiremoneFeature.generate(worldEntity);
	}
}
