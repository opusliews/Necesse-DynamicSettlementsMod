package opusliews.multilevelsettlement;

import necesse.engine.registries.SettlerThoughtRegistry;
import necesse.engine.util.LevelIdentifier;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.level.maps.levelData.settlementData.LevelSettler;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementBed;
import opusliews.logging.Logging;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class SettlementLevelPreferenceSystem {
	public static final String caveResidenceThoughtStringID = "dscavelevelpreference";
	public static final int caveResidenceHappinessModifier = 15;
	private static final Map<HumanMob, SettlementLevelPreference> preferences = Collections.synchronizedMap(new WeakHashMap<>());

	private SettlementLevelPreferenceSystem() {
	}

	public static void registerThought() {
		SettlerThoughtRegistry.registerSettlerThought(caveResidenceThoughtStringID,
				new necesse.level.maps.levelData.settlementData.settler.thoughts.SimpleSettlerThought("settlement", "cavelevelpreference", caveResidenceHappinessModifier));
	}

	public static SettlementLevelPreference getPreference(HumanMob human) {
		if (human == null) return SettlementLevelPreference.AUTO;
		SettlementLevelPreference preference = preferences.get(human);
		return preference == null ? SettlementLevelPreference.AUTO : preference;
	}

	public static void setPreference(HumanMob human, SettlementLevelPreference preference) {
		if (human == null) return;
		SettlementLevelPreference resolved = preference == null ? SettlementLevelPreference.AUTO : preference;
		if (resolved == SettlementLevelPreference.AUTO) preferences.remove(human);
		else preferences.put(human, resolved);
		if (Logging.logEnabled) Logging.logMessage("[LevelPreference] Set settler=" + human.getUniqueID() + " preference=" + resolved);
	}

	public static boolean isPreferredJobLevel(HumanMob human, LevelIdentifier targetLevel) {
		if (human == null || targetLevel == null) return false;
		SettlementLevelPreference preference = getPreference(human);
		if (preference == SettlementLevelPreference.AUTO) return false;
		ServerSettlementData settlement = human.getSettlerSettlementServerData();
		if (settlement == null) return false;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null) return false;
		SettlementLevelType targetType = domain.getLevelType(targetLevel);
		return preference == SettlementLevelPreference.SURFACE && targetType == SettlementLevelType.SURFACE
				|| preference == SettlementLevelPreference.CAVE && targetType == SettlementLevelType.CAVE;
	}

	public static int getJobLevelPreferenceRank(HumanMob human, LevelIdentifier targetLevel) {
		return isPreferredJobLevel(human, targetLevel) ? 1 : 0;
	}

	public static String getHappinessThoughtStringID(HumanMob human) {
		if (human == null || !human.isSettler() || getPreference(human) != SettlementLevelPreference.CAVE) return null;
		ServerSettlementData settlement = human.getSettlerSettlementServerData();
		if (settlement == null) return null;
		LevelSettler settler = settlement.getSettler(human.getUniqueID());
		if (settler == null) return null;
		SettlementBed bed = settler.getBed();
		if (bed == null) return null;
		LevelIdentifier bedLevel = SettlementCaveBedSystem.getBedLevelIdentifier(settler, bed);
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (bedLevel == null || domain == null) return null;
		return domain.getLevelType(bedLevel) == SettlementLevelType.CAVE ? caveResidenceThoughtStringID : null;
	}
}
