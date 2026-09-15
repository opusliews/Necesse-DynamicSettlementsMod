package opusliews.object;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.List;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.localization.Localization;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.DrawOptions;
import necesse.gfx.drawables.LevelSortedDrawable;
import necesse.gfx.drawables.OrderableDrawables;
import necesse.gfx.gameTexture.GameTexture;
import necesse.inventory.item.toolItem.ToolType;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.light.GameLight;
import necesse.level.maps.multiTile.MultiTile;
import necesse.level.maps.multiTile.SideMultiTile;

public class AnvilCraftingTaskBoard2Object extends GameObject {
	public GameTexture texture;
	protected int counterID;

	public AnvilCraftingTaskBoard2Object() {
		super(new Rectangle(32, 32));
		displayMapTooltip = true;
		mapColor = new Color(132, 91, 25);
		toolType = ToolType.ALL;
		isLightTransparent = true;
		hoverHitbox = new Rectangle(0, -32, 32, 64);
	}

	@Override
	public MultiTile getMultiTile(int rotation) {
		return new SideMultiTile(0, 0, 1, 2, rotation, false, new int[]{getID(), counterID});
	}

	@Override
	public void loadTextures() {
		super.loadTextures();
		texture = GameTexture.fromFile("objects/anvilcraftingtaskboard");
	}

	@Override
	public Rectangle getCollision(Level level, int x, int y, int rotation) {
		if (rotation == 0) return new Rectangle(x * 32 + 4, y * 32 + 4, 24, 28);
		if (rotation == 1) return new Rectangle(x * 32, y * 32 + 6, 26, 20);
		return rotation == 2 ? new Rectangle(x * 32 + 4, y * 32, 24, 26) : new Rectangle(x * 32 + 6, y * 32 + 6, 26, 20);
	}

	@Override
	public void addDrawables(List list, OrderableDrawables tileList, Level level, int tileX, int tileY, TickManager tickManager, GameCamera camera, PlayerMob perspective) {
		GameLight light = level.getLightLevel(tileX, tileY);
		int drawX = camera.getTileDrawX(tileX);
		int drawY = camera.getTileDrawY(tileY);
		final DrawOptions options = texture.initDraw().sprite(1, 0, 32, texture.getHeight()).addObjectDamageOverlay(this, level, tileX, tileY).light(light).pos(drawX, drawY - texture.getHeight() + 32);
		list.add(new LevelSortedDrawable(this, tileX, tileY) {
			@Override
			public int getSortY() {
				return 16;
			}

			@Override
			public void draw(TickManager tickManager) {
				options.draw();
			}
		});
	}

	@Override
	public void drawPreview(Level level, int tileX, int tileY, int rotation, float alpha, PlayerMob player, GameCamera camera) {
		int drawX = camera.getTileDrawX(tileX);
		int drawY = camera.getTileDrawY(tileY);
		texture.initDraw().sprite(1, 0, 32, texture.getHeight()).alpha(alpha).pos(drawX, drawY - texture.getHeight() + 32).draw();
	}

	@Override
	public String getInteractTip(Level level, int x, int y, PlayerMob perspective, boolean debug) {
		return Localization.translate("controls", "usetip");
	}

	@Override
	public boolean canInteract(Level level, int x, int y, PlayerMob player) {
		return true;
	}

	@Override
	public void interact(Level level, int x, int y, PlayerMob player) {
		getMultiTile(level.getObjectRotation(x, y)).getMasterLevelObject(level, 0, x, y).ifPresent(master -> master.interact(player));
	}
}
