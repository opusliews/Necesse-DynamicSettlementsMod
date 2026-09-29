package opusliews.patches;

import java.lang.reflect.Field;
import java.util.ArrayList;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.job.EntityJobWorker;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.jobCondition.DoWhenThresholdsJobCondition;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementLevelStorageManager;

@ModMethodPatch(target = DoWhenThresholdsJobCondition.class, name = "isConditionMet", arguments = {EntityJobWorker.class, ServerSettlementData.class})
public class WorkstationThresholdMultiLevelPatch {
	private static volatile Field thresholdsField;

	@Advice.OnMethodExit
	public static void onExit(@Advice.This DoWhenThresholdsJobCondition condition,
			@Advice.Argument(1) ServerSettlementData settlement,
			@Advice.Return(readOnly = false) boolean result) {
		result = evaluate(condition, settlement, result);
	}

	public static boolean evaluate(DoWhenThresholdsJobCondition condition, ServerSettlementData settlement, boolean fallback) {
		if (condition == null || settlement == null) return fallback;
		try {
			Field field = getThresholdsField();
			Object raw = field.get(condition);
			if (!(raw instanceof ArrayList)) return fallback;
			ArrayList<?> thresholds = (ArrayList<?>)raw;
			if (thresholds.isEmpty()) return false;
			for (Object value : thresholds) {
				if (!(value instanceof DoWhenThresholdsJobCondition.ItemThreshold)) return fallback;
				DoWhenThresholdsJobCondition.ItemThreshold threshold = (DoWhenThresholdsJobCondition.ItemThreshold)value;
				int totalItems = SettlementLevelStorageManager.getTotalStoredItems(settlement, threshold.item);
				if (threshold.whenGreaterThan) {
					if (totalItems <= threshold.threshold) return false;
				}
				else if (totalItems >= threshold.threshold) {
					return false;
				}
			}
			return true;
		}
		catch (ReflectiveOperationException | RuntimeException e) {
			return fallback;
		}
	}

	public static Field getThresholdsField() throws NoSuchFieldException {
		Field field = thresholdsField;
		if (field != null) return field;
		synchronized (WorkstationThresholdMultiLevelPatch.class) {
			field = thresholdsField;
			if (field == null) {
				field = DoWhenThresholdsJobCondition.class.getDeclaredField("thresholds");
				field.setAccessible(true);
				thresholdsField = field;
			}
			return field;
		}
	}
}
