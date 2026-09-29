package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.friendly.human.AdventurePartyHumanHandler;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementCrossLevelCommandSystem;

@ModMethodPatch(target = AdventurePartyHumanHandler.class, name = "serverTick", arguments = {})
public class CrossLevelAdventurePartyTravelPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.FieldValue("mob") necesse.entity.mobs.friendly.human.HumanMob human) {
		return SettlementCrossLevelCommandSystem.shouldSuppressAdventurePartyTeleport(human);
	}
}
