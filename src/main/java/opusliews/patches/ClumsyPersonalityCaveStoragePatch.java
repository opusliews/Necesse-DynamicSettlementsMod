package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.gameObject.furniture.SettlerBedObject;
import necesse.level.maps.LevelObject;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.settler.personalities.ClumsySettlerPersonality;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementLevelStorageManager;
import opusliews.multilevelsettlement.SettlementPersonalityLevelSystem;

@ModMethodPatch(target = ClumsySettlerPersonality.class, name = "isValidObject", arguments = {ServerSettlementData.class, LevelObject.class})
public class ClumsyPersonalityCaveStoragePatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.Argument(0) ServerSettlementData settlement,
			@Advice.Argument(1) LevelObject levelObject,
			@Advice.Local("handledResult") boolean handledResult) {
		if (!SettlementPersonalityLevelSystem.isCaveLevelObject(settlement, levelObject)) return false;

		handledResult = levelObject.object.getID() != 0
				&& levelObject.object.objectHealth > 10
				&& levelObject.object.isMultiTileMaster()
				&& !levelObject.object.roomProperties.contains("lights")
				&& !(levelObject.object instanceof SettlerBedObject)
				&& SettlementLevelStorageManager.getStorage(settlement, levelObject.level.getIdentifier(), levelObject.tileX, levelObject.tileY) == null;
		return true;
	}

	@Advice.OnMethodExit
	public static void onExit(@Advice.Argument(0) ServerSettlementData settlement,
			@Advice.Argument(1) LevelObject levelObject,
			@Advice.Local("handledResult") boolean handledResult,
			@Advice.Return(readOnly = false) boolean result) {
		if (SettlementPersonalityLevelSystem.isCaveLevelObject(settlement, levelObject)) result = handledResult;
	}
}
