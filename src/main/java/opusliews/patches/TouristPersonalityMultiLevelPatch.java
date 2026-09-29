package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.settler.personalities.TouristSettlerPersonality;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementPersonalityLevelSystem;

@ModMethodPatch(target = TouristSettlerPersonality.class, name = "isInAnotherSettlement", arguments = {ServerSettlementData.class})
public class TouristPersonalityMultiLevelPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This TouristSettlerPersonality personality,
			@Advice.Argument(0) ServerSettlementData settlement,
			@Advice.Local("ownSettlementDomain") boolean ownSettlementDomain) {
		ownSettlementDomain = personality.mob.adventureParty.isInAdventureParty()
				&& SettlementPersonalityLevelSystem.isInsideOwnSettlementDomain(personality.mob, settlement);
		return ownSettlementDomain;
	}

	@Advice.OnMethodExit
	public static void onExit(@Advice.Local("ownSettlementDomain") boolean ownSettlementDomain,
			@Advice.Return(readOnly = false) boolean result) {
		if (ownSettlementDomain) result = false;
	}
}
