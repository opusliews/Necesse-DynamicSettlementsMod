package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.SettlementWorkZoneManager;
import necesse.level.maps.levelData.settlementData.zones.SettlementForestryZone;
import net.bytebuddy.asm.Advice;
import opusliews.zones.SettlementWorkZoneLevelContextSupport;
import opusliews.zones.SettlementWorkZonePersistenceSupport;

@ModMethodPatch(target = SettlementForestryZone.class, name = "setChoppingAllowed", arguments = {boolean.class})
public class SettlementForestryChoppingLevelContextPatch {
	@Advice.OnMethodEnter
	public static void onEnter(
			@Advice.FieldValue("manager") SettlementWorkZoneManager manager,
			@Advice.Local("contextToken") SettlementWorkZoneLevelContextSupport.ContextToken contextToken
	) {
		contextToken = SettlementWorkZoneLevelContextSupport.begin(manager, "forestry-chopping");
	}

	@Advice.OnMethodExit(onThrowable = Throwable.class)
	public static void onExit(
			@Advice.This SettlementForestryZone zone,
			@Advice.FieldValue("manager") SettlementWorkZoneManager manager,
			@Advice.Local("contextToken") SettlementWorkZoneLevelContextSupport.ContextToken contextToken,
			@Advice.Thrown Throwable thrown
	) {
		try {
			if (thrown == null) SettlementWorkZonePersistenceSupport.persistAndBroadcast(zone, manager);
		}
		finally {
			SettlementWorkZoneLevelContextSupport.end(contextToken);
		}
	}
}
