package opusliews.patches;

import necesse.engine.modLoader.annotations.ModConstructorPatch;
import necesse.engine.network.NetworkClient;
import necesse.entity.mobs.PlayerMob;
import net.bytebuddy.asm.Advice;
import opusliews.progression.EarlyHealthProgressionSystem;

@ModConstructorPatch(target = PlayerMob.class, arguments = {long.class, NetworkClient.class})
public class PlayerMobStartingVitalsPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This PlayerMob player) {
		EarlyHealthProgressionSystem.initializeNewPlayer(player);
	}
}
