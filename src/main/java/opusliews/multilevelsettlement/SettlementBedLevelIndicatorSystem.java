package opusliews.multilevelsettlement;

import necesse.gfx.drawOptions.DrawOptions;
import necesse.gfx.drawOptions.DrawOptionsList;
import necesse.inventory.container.settlement.data.SettlementSettlerData;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.LevelSettler;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementBed;
import opusliews.DynamicSettlements;
import opusliews.network.PacketSettlementBedLevelSync;

import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class SettlementBedLevelIndicatorSystem {
	private static final Color upArrowColor = new Color(76, 200, 96);
	private static final Color downArrowColor = new Color(210, 64, 64);
	private static final Map<Integer, Set<Integer>> syncedMobIDsBySettlement = new HashMap<>();
	private static final Map<Integer, Boolean> syncedBedLevelByMob = new HashMap<>();
	private static final ThreadLocal<Boolean> drawingBedFlag = ThreadLocal.withInitial(() -> false);

	private SettlementBedLevelIndicatorSystem() {
	}

	public static PacketSettlementBedLevelSync getSyncPacket(ServerSettlementData settlement) {
		HashMap<Integer, Boolean> bedLevels = new HashMap<>();
		if (settlement != null) {
			for (Object value : settlement.getSettlers()) {
				if (!(value instanceof LevelSettler)) continue;
				LevelSettler settler = (LevelSettler)value;
				SettlementBed bed = settler.getBed();
				if (bed == null) continue;
				bedLevels.put(settler.mobUniqueID, bed instanceof SettlementCaveBed);
			}
		}
		return new PacketSettlementBedLevelSync(settlement == null ? 0 : settlement.uniqueID, bedLevels);
	}

	public static synchronized void applySync(int settlementUniqueID, Map<Integer, Boolean> bedLevels) {
		Set<Integer> previous = syncedMobIDsBySettlement.remove(settlementUniqueID);
		if (previous != null) {
			for (int mobUniqueID : previous) syncedBedLevelByMob.remove(mobUniqueID);
		}

		HashSet<Integer> current = new HashSet<>();
		if (bedLevels != null) {
			for (Map.Entry<Integer, Boolean> entry : bedLevels.entrySet()) {
				if (entry.getKey() == null || entry.getValue() == null) continue;
				current.add(entry.getKey());
				syncedBedLevelByMob.put(entry.getKey(), entry.getValue());
			}
		}
		syncedMobIDsBySettlement.put(settlementUniqueID, current);
	}

	public static void beginBedFlagDraw() {
		drawingBedFlag.set(true);
	}

	public static void endBedFlagDraw() {
		drawingBedFlag.remove();
	}

	public static DrawOptions decorateBedPortrait(SettlementSettlerData data, Level currentLevel, int size, int drawX, int drawY, DrawOptions original) {
		if (original == null || data == null || data.bedPosition == null || currentLevel == null || !drawingBedFlag.get()) return original;

		Boolean caveBed;
		synchronized (SettlementBedLevelIndicatorSystem.class) {
			caveBed = syncedBedLevelByMob.get(data.mobUniqueID);
		}
		if (caveBed == null || caveBed == currentLevel.isCave) return original;

		int arrowSize = 30;

		int arrowX = drawX + size;
		int arrowY = drawY + size - arrowSize;

		boolean pointsUp = !caveBed;
		float rotation = pointsUp ? 0.0F : 180.0F;
		Color color = pointsUp ? upArrowColor : downArrowColor;

		DrawOptionsList result = new DrawOptionsList();
		result.add(original);
		result.add(DynamicSettlements.outlinedArrowTexture.initDraw().color(color).rotate(rotation, arrowSize / 2, arrowSize / 2).size(arrowSize, arrowSize).pos(arrowX, arrowY));
		return result;
	}
}
