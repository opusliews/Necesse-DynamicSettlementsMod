package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.level.maps.LevelObject;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementCaveInventoryOpenSupport;
import opusliews.worldgengating.WorldgenLockedContainerSystem;

@ModMethodPatch(target = LevelObject.class, name = "interact", arguments = {PlayerMob.class})
public class WorldgenLockedContainerInteractPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This LevelObject levelObject, @Advice.Argument(0) PlayerMob player) {
		// Worldgen interaction gating must run before the multi-level settlement container bridge.
		// Otherwise a locked natural container on a cave level can be opened by the bridge before
		// the key/progression gate gets a chance to block it.
		if (WorldgenLockedContainerSystem.handleInteract(levelObject, player)) return true;
		return SettlementCaveInventoryOpenSupport.tryOpen(levelObject, player);
	}
}
