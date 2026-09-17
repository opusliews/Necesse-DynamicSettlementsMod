package opusliews.charcoal;

import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.registries.TileRegistry;
import necesse.engine.util.EventVariable;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.SurfaceGrassObject;
import necesse.level.maps.Level;
import necesse.level.maps.hudManager.HudDrawElement;
import necesse.level.maps.levelData.settlementData.zones.SettlementTileTickZone;
import opusliews.jobs.CharcoalProductionLevelJob;
import opusliews.tile.CharcoalPitSystem;

import java.awt.*;
import java.util.function.BooleanSupplier;

public class CharcoalProductionZone extends SettlementTileTickZone {
	public static final String stringID = "charcoalproduction";
	public static final EventVariable hideZones = new EventVariable(false);
	private static final int[][] cardinalOffsets = {
			{0, -1},
			{1, 0},
			{0, 1},
			{-1, 0}
	};

	@Override
	protected void handleTile(Point tile) {
		Level level = manager.data.getLevel();
		if (!isValidCandidate(level, tile.x, tile.y, null)) {
			return;
		}

		level.jobsLayer.addJob(new CharcoalProductionLevelJob(tile.x, tile.y, this));
	}

	public static boolean isValidCandidate(Level level, int tileX, int tileY, CharcoalProductionLevelJob currentJob) {
		if (level == null || !level.isTileWithinBounds(tileX, tileY) || level.isProtected(tileX, tileY)) {
			return false;
		}

		int tileID = level.getTileID(tileX, tileY);
		if (tileID != TileRegistry.dirtID && tileID != TileRegistry.grassID) {
			return false;
		}

		GameObject object = level.getObject(tileX, tileY);
		if (object.getID() != 0 && !(object instanceof SurfaceGrassObject)) {
			return false;
		}

		if (CharcoalPitSystem.isPitTile(level, tileX, tileY)) {
			return false;
		}

		for (int[] offset : cardinalOffsets) {
			int checkX = tileX + offset[0];
			int checkY = tileY + offset[1];
			if (!level.isTileWithinBounds(checkX, checkY)) {
				continue;
			}

			if (CharcoalPitSystem.isPitTile(level, checkX, checkY)) {
				return false;
			}

			boolean hasReservedPit = level.jobsLayer.streamJobsInTile(checkX, checkY)
					.anyMatch(job -> job instanceof CharcoalProductionLevelJob && job != currentJob);
			if (hasReservedPit) {
				return false;
			}
		}

		return true;
	}

	@Override
	public boolean isHiddenSetting() {
		return (Boolean)hideZones.get();
	}

	@Override
	protected GameMessage getDefaultName(int number) {
		return new LocalMessage("ui", "charcoalproductionzonedefname", "number", number);
	}

	@Override
	public GameMessage getAbstractName() {
		return new LocalMessage("ui", "charcoalproductionzone");
	}

	@Override
	public HudDrawElement getHudDrawElement(int drawPriority, BooleanSupplier overrideShow) {
		return getHudDrawElement(drawPriority, overrideShow, new Color(92, 92, 92, 150), new Color(46, 46, 46, 75));
	}
}
