package opusliews.tile;

import java.awt.Color;
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
import necesse.level.maps.Level;
import necesse.level.maps.regionSystem.SimulatePriorityList;

import java.util.List;

public class ShallowHoleTile extends DirtTile {
	public static final String stringID = "shallowholetile";
	private static final int sideExitMargin = 8;
	public static final int playerSinkingAmount = 10;
	private static final int maskTextureCutoffY = 32;
	private static final int playerSpriteTopOffset = 51;
	private static final int playerSpriteHeight = 64;

	private GameTextureSection holeTexture;
	private GameTexture playerMaskTexture;

	public boolean isPlayerInSinkingArea(PlayerMob player) {
		int tileX = player.getTileX();
		float localX = player.getX() - tileX * 32.0F;

		return localX > sideExitMargin && localX < 32.0F - sideExitMargin;
	}

	public ShallowHoleTile() {
		super();
		mapColor = new Color(88, 68, 58);
		canBeMined = false;
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
		holeTexture = tileTextures.addTexture(GameTexture.fromFile("tiles/shallowhole"));
		playerMaskTexture = GameTexture.fromFile("tiles/shallowholemask");
	}

	@Override
	public int getMobSinkingAmount(Mob mob) {
		if (!(mob instanceof PlayerMob)) {
			return 0;
		}

		PlayerMob player = (PlayerMob)mob;
		return isPlayerInSinkingArea(player) ? playerSinkingAmount : 0;
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

	public MaskShaderOptions getPlayerMaskOptions(PlayerMob player) {
		if (playerMaskTexture == null) {
			return new MaskShaderOptions(0, 0);
		}

		int tileY = player.getTileY();
		int southEdgeWorldY = (tileY + 1) * 32;

		int cutoffLocalY = southEdgeWorldY - player.getY() + playerSpriteTopOffset - playerSinkingAmount;
		cutoffLocalY = Math.max(0, Math.min(playerSpriteHeight, cutoffLocalY));

		int maskYOffset = maskTextureCutoffY - cutoffLocalY;
		return new MaskShaderOptions(playerMaskTexture, 0, 0, 0, maskYOffset);
	}
}
