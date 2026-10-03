package opusliews.zones;

import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.SettlementWorkZoneManager;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementPersonalityLevelSystem;

public final class SettlementWorkZoneLevelContextSupport {
	private SettlementWorkZoneLevelContextSupport() {
	}

	public static ContextToken begin(SettlementWorkZoneManager manager, String operation) {
		if (!(manager instanceof LevelScopedWorkZoneManager)) return null;

		LevelScopedWorkZoneManager levelManager = (LevelScopedWorkZoneManager)manager;
		Level previous = SettlementPersonalityLevelSystem.getContextLevel(levelManager.data);
		SettlementPersonalityLevelSystem.beginLevelContext(levelManager.data, levelManager.level);

		if (Logging.logEnabled) Logging.logMessage("[IndependentZonesDebug] Entered work-zone config level context settlement="
				+ levelManager.data.uniqueID + " level=" + levelManager.levelIdentifier
				+ " operation=" + operation);

		return new ContextToken(levelManager, previous, operation);
	}

	public static void end(ContextToken token) {
		if (token == null) return;

		if (token.previous != null) SettlementPersonalityLevelSystem.beginLevelContext(token.manager.data, token.previous);
		else SettlementPersonalityLevelSystem.endBedLevelContext();

		if (Logging.logEnabled) Logging.logMessage("[IndependentZonesDebug] Exited work-zone config level context settlement="
				+ token.manager.data.uniqueID + " level=" + token.manager.levelIdentifier
				+ " operation=" + token.operation);
	}

	public static final class ContextToken {
		public final LevelScopedWorkZoneManager manager;
		public final Level previous;
		public final String operation;

		public ContextToken(LevelScopedWorkZoneManager manager, Level previous, String operation) {
			this.manager = manager;
			this.previous = previous;
			this.operation = operation;
		}
	}
}
