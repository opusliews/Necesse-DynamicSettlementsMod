package opusliews.charcoal;

import java.awt.Color;
import java.awt.Point;
import java.util.function.BooleanSupplier;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.registries.GlobalIngredientRegistry;
import necesse.engine.registries.TileRegistry;
import necesse.engine.util.EventVariable;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.SurfaceGrassObject;
import necesse.level.maps.Level;
import necesse.level.maps.hudManager.HudDrawElement;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageGlobalIngredientIDIndex;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageItemIDIndex;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageRecordsRegionData;
import necesse.level.maps.levelData.settlementData.zones.SettlementTileTickZone;
import opusliews.jobs.CharcoalProductionLevelJob;
import opusliews.tile.CharcoalPitLevelData;
import opusliews.tile.CharcoalPitSystem;

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
		if (!canProduce() || !hasEnoughLogs() || !isValidCandidate(level, tile.x, tile.y, null)) {
			return;
		}

		level.jobsLayer.addJob(new CharcoalProductionLevelJob(tile.x, tile.y, this));
	}

	public boolean canProduce() {
		if (manager == null || manager.data == null) {
			return false;
		}

		CharcoalPitLevelData settings = CharcoalPitLevelData.get(manager.data.getLevel(), false);
		if (settings == null) {
			return false;
		}

		if (settings.isRepeatForever()) {
			return true;
		}

		int target = settings.getProduceUntilUnitsStocked();
		if (target <= 0) {
			return false;
		}

		SettlementStorageItemIDIndex itemIndex = manager.data.storageRecords.getIndex(SettlementStorageItemIDIndex.class);
		int storedCharcoal = itemIndex.getTotalItems("charcoal");
		int potentialCharcoal = settings.getPotentialCharcoalAmount();
		return storedCharcoal + potentialCharcoal < target;
	}

	public boolean hasEnoughLogs() {
		if (manager == null || manager.data == null) {
			return false;
		}

		int anyLogID = GlobalIngredientRegistry.getGlobalIngredientID("anylog");
		SettlementStorageGlobalIngredientIDIndex index = manager.data.storageRecords.getIndex(SettlementStorageGlobalIngredientIDIndex.class);
		SettlementStorageRecordsRegionData logs = index.getGlobalIngredient(anyLogID);
		return logs != null && logs.getTotalItems() >= CharcoalProductionLevelJob.requiredLogs;
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

	public static boolean isValidReusableHole(Level level, int tileX, int tileY, CharcoalProductionLevelJob currentJob) {
		if (level == null || !level.isTileWithinBounds(tileX, tileY) || level.isProtected(tileX, tileY)) {
			return false;
		}

		if (level.getTileID(tileX, tileY) != TileRegistry.getTileID(opusliews.tile.ShallowHoleTile.stringID)) {
			return false;
		}

		if (level.getObjectID(tileX, tileY) != 0) {
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
