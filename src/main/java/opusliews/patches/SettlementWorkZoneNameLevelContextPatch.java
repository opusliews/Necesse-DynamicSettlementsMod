package opusliews.patches;

import necesse.engine.localization.message.GameMessage;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.SettlementWorkZoneManager;
import necesse.level.maps.levelData.settlementData.zones.SettlementWorkZone;
import net.bytebuddy.asm.Advice;
import opusliews.zones.SettlementWorkZoneLevelContextSupport;
import opusliews.zones.SettlementWorkZonePersistenceSupport;

@ModMethodPatch(target = SettlementWorkZone.class, name = "setName", arguments = {GameMessage.class})
public class SettlementWorkZoneNameLevelContextPatch {
	@Advice.OnMethodEnter
	public static void onEnter(
			@Advice.FieldValue("manager") SettlementWorkZoneManager manager,
			@Advice.Local("contextToken") SettlementWorkZoneLevelContextSupport.ContextToken contextToken
	) {
		contextToken = SettlementWorkZoneLevelContextSupport.begin(manager, "work-zone-rename");
	}

	@Advice.OnMethodExit(onThrowable = Throwable.class)
	public static void onExit(
			@Advice.This SettlementWorkZone zone,
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
