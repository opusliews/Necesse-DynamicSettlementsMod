package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.Attacker;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementLadderSystem;
import opusliews.settlement.SettlementPlayerBedSystem;

import java.util.ArrayList;

@ModMethodPatch(target = GameObject.class, name = "onDestroyed", arguments = {Level.class, int.class, int.class, int.class, Attacker.class, ServerClient.class, ArrayList.class})
public class SettlementLadderDestroyedPatch {
	@Advice.OnMethodEnter
	public static void onEnter(
			@Advice.This GameObject object,
			@Advice.Argument(0) Level level,
			@Advice.Argument(2) int tileX,
			@Advice.Argument(3) int tileY
	) {
		SettlementLadderSystem.onSupportedLadderDestroyed(level, tileX, tileY, object);
		SettlementPlayerBedSystem.onBedDestroyed(level, tileX, tileY, object);
	}
}
