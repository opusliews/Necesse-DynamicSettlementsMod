package opusliews.tile;

import java.awt.Color;
import java.util.List;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.entity.mobs.MaskShaderOptions;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawables.LevelTileLiquidDrawOptions;
import necesse.gfx.drawables.LevelTileTerrainDrawOptions;
import necesse.gfx.drawables.OrderableDrawables;
import necesse.gfx.gameTexture.GameTexture;
import necesse.gfx.gameTexture.GameTextureSection;
import necesse.level.gameTile.DirtTile;
import necesse.level.gameTile.GameTile;
import necesse.level.maps.Level;
import necesse.level.maps.regionSystem.SimulatePriorityList;

public class ShallowHoleTile extends DirtTile {
	public static final String stringID = "shallowholetile";
	private static final int sideExitMargin = 8;
	public static final int mobSinkingAmount = 10;
	public static final int playerSinkingAmount = mobSinkingAmount;
	private static final int maskReferenceSpriteTop = 51;

	private GameTextureSection holeTexture;
	private GameTexture mobMaskTexture;

	public ShallowHoleTile() {
		super();
		mapColor = new Color(88, 68, 58);
		canBeMined = false;
	}

	protected String getHoleTexturePath() {
		return "tiles/shallowhole";
	}

	protected String getMobMaskTexturePath() {
		return "tiles/shallowholemask";
	}

	public boolean isMobInSinkingArea(Mob mob) {
		int tileX = mob.getTileX();
		float localX = mob.getX() - tileX * 32.0F;
		return localX > sideExitMargin && localX < 32.0F - sideExitMargin;
	}

	@Override
	public GameTexture generateItemTexture() {
		GameTexture texture = GameTexture.fromFile("tiles/shallowhole", true);
		GameTexture itemTexture = new GameTexture("shallow hole item", 32, 32);

		itemTexture.copy(texture, 0, 0, 0, 0, 32, 32);
		itemTexture.makeFinal();

		return itemTexture;
	}

	public boolean isPlayerInSinkingArea(PlayerMob player) {
		return isMobInSinkingArea(player);
	}

	@Override
	public boolean canBePlacedOn(Level level, int tileX, int tileY, GameTile placing) {
		return false;
	}

	@Override
	public void tick(Level level, int x, int y) {
		// Deliberately suppress DirtTile grass/snow spreading for the hole state.
	}

	@Override
	public void addSimulateLogic(Level level, int x, int y, long ticks, SimulatePriorityList list, boolean sendChanges) {
		// Deliberately suppress DirtTile simulation spreading for the hole state.
	}

	@Override
	protected void loadTextures() {
		super.loadTextures();
		holeTexture = tileTextures.addTexture(GameTexture.fromFile(getHoleTexturePath()));
		mobMaskTexture = GameTexture.fromFile(getMobMaskTexturePath());
	}

	@Override
	public int getMobSinkingAmount(Mob mob) {
		return 0;
	}

	@Override
	public void addDrawables(
			LevelTileTerrainDrawOptions underLiquidList,
			LevelTileLiquidDrawOptions liquidList,
			LevelTileTerrainDrawOptions overLiquidList,
			OrderableDrawables objectTileList,
			List sortedList,
			Level level,
			int tileX,
			int tileY,
			GameCamera camera,
			TickManager tickManager
	) {
		super.addDrawables(
				underLiquidList,
				liquidList,
				overLiquidList,
				objectTileList,
				sortedList,
				level,
				tileX,
				tileY,
				camera,
				tickManager
		);

		int drawX = camera.getTileDrawX(tileX);
		int drawY = camera.getTileDrawY(tileY);
		underLiquidList.add(holeTexture).pos(drawX, drawY);
	}

	public MaskShaderOptions getMobMaskOptions(Mob mob) {
		if (mobMaskTexture == null || !isMobInSinkingArea(mob)) {
			return new MaskShaderOptions(0, 0);
		}

		int maskMove = mob.getSwimMaskMove();
		int swimSinkOffset = mob.getSwimSinkOffset();
		int totalSwimMove = maskMove + swimSinkOffset;

		if (totalSwimMove <= 0) {
			return new MaskShaderOptions(mobMaskTexture, 0, 0, 0, 0);
		}

		int targetSink = Math.min(mobSinkingAmount, totalSwimMove);
		float depthPercent = (float)targetSink / (float)totalSwimMove;
		int vanillaMaskOffset = mob.getSwimMaskOffset() + (int)(depthPercent * (float)maskMove);
		int drawYOffset = -mob.getSwimMaskOffset() + vanillaMaskOffset
				+ (int)(depthPercent * (float)swimSinkOffset);

		int alignmentCorrection = mob.getSwimMaskOffset() - Math.round(swimSinkOffset / 2.0F);
		int localY = Math.round(mob.getY() - mob.getTileY() * 32.0F);
		int maskYOffset = localY - maskReferenceSpriteTop + alignmentCorrection + drawYOffset;

		return new MaskShaderOptions(mobMaskTexture, 0, drawYOffset, 0, maskYOffset);
	}

	public MaskShaderOptions getPlayerMaskOptions(PlayerMob player) {
		return getMobMaskOptions(player);
	}
}
