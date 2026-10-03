package opusliews.multilevelsettlement;

import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import necesse.level.maps.levelData.settlementData.LevelSettler;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementBed;

public final class SettlementPersonalityLevelSystem {
	private static final ThreadLocal<LevelContext> levelContext = new ThreadLocal<>();

	private SettlementPersonalityLevelSystem() {
	}

	public static void beginBedLevelContext(HumanMob human, ServerSettlementData settlement) {
		if (human == null || settlement == null || human.levelSettler == null) return;
		SettlementBed bed = human.levelSettler.getBed();
		if (!(bed instanceof SettlementCaveBed)) return;
		Level level = ((SettlementCaveBed)bed).getBedLevel();
		if (level != null) beginLevelContext(settlement, level);
	}

	public static void beginLevelContext(ServerSettlementData settlement, Level level) {
		if (settlement != null && level != null) levelContext.set(new LevelContext(settlement, level));
	}

	public static void endBedLevelContext() {
		levelContext.remove();
	}

	public static Level getContextLevel(ServerSettlementData settlement) {
		LevelContext context = levelContext.get();
		return context != null && context.settlement == settlement ? context.level : null;
	}

	public static boolean isCaveLevelObject(ServerSettlementData settlement, LevelObject levelObject) {
		if (settlement == null || levelObject == null || levelObject.level == null) return false;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		return domain != null && domain.getLevelType(levelObject.level.getIdentifier()) == SettlementLevelType.CAVE;
	}

	public static boolean isInsideOwnSettlementDomain(HumanMob human, ServerSettlementData settlement) {
		if (human == null || settlement == null || human.getLevel() == null) return false;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null) return false;
		SettlementLevelType type = domain.getLevelType(human.getLevel().getIdentifier());
		return type != null && domain.isTileWithinBounds(human.getLevel().getIdentifier(), human.getTileX(), human.getTileY());
	}

	public static Level getBedLevel(LevelSettler settler) {
		if (settler == null) return null;
		SettlementBed bed = settler.getBed();
		if (bed instanceof SettlementCaveBed) return ((SettlementCaveBed)bed).getBedLevel();
		return settler.data == null ? null : settler.data.getLevel();
	}

	private static final class LevelContext {
		final ServerSettlementData settlement;
		final Level level;

		LevelContext(ServerSettlementData settlement, Level level) {
			this.settlement = settlement;
			this.level = level;
		}
	}
}
