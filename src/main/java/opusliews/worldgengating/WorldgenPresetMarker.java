package opusliews.worldgengating;

import necesse.level.maps.Level;
import necesse.level.maps.presets.Preset;

public final class WorldgenPresetMarker {
	private WorldgenPresetMarker() {
	}

	public static void addContentTierRefresh(Preset preset, int tileX, int tileY) {
		preset.addCustomApply(tileX, tileY, 0, new ContentTierRefreshApply(), true);
	}

	public static void addPirateDisplayStandMarker(Preset preset, int tileX, int tileY) {
		preset.addCustomApply(tileX, tileY, 0, new PirateDisplayStandApply(), true);
	}

	public static class ContentTierRefreshApply implements Preset.CustomApplyFunction {
		@Override
		public Preset.UndoLogic applyToLevel(Level level, int levelX, int levelY, int dir, necesse.engine.util.GameBlackboard blackboard) {
			WorldgenGatingLevelData data = WorldgenGatingLevelData.get(level, true);
			data.refreshNaturalObjectFromContents(0, levelX, levelY);
			return null;
		}
	}

	public static class PirateDisplayStandApply implements Preset.CustomApplyFunction {
		@Override
		public Preset.UndoLogic applyToLevel(Level level, int levelX, int levelY, int dir, necesse.engine.util.GameBlackboard blackboard) {
			WorldgenGatingLevelData data = WorldgenGatingLevelData.get(level, true);
			data.markSpecial(0, levelX, levelY, WorldgenGatingData.NaturalType.LOCKED_CONTAINER, WorldgenTierTable.PIRATE_DISPLAY_STAND);
			return null;
		}
	}
}
