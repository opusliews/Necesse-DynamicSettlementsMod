package opusliews.object;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.List;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.localization.message.LocalMessage;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.texture.TextureDrawOptions;
import necesse.gfx.drawables.LevelSortedDrawable;
import necesse.gfx.drawables.OrderableDrawables;
import necesse.gfx.gameTexture.GameTexture;
import necesse.inventory.lootTable.LootItemInterface;
import necesse.inventory.lootTable.LootTable;
import necesse.inventory.lootTable.lootItem.LootItem;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.light.GameLight;

public class PlacedLogObject extends GameObject {
	private final String sourceLogStringID;
	private final String texturePath;
	private GameTexture texture;

	public PlacedLogObject(String sourceLogStringID) {
		super(new Rectangle(3, 14, 26, 14));
		this.sourceLogStringID = sourceLogStringID;
		String type = sourceLogStringID.endsWith("log")
				? sourceLogStringID.substring(0, sourceLogStringID.length() - 3)
				: sourceLogStringID;
		this.texturePath = "objects/" + type + "treelog";
		mapColor = new Color(112, 72, 42);
		debrisColor = new Color(112, 72, 42);
		objectHealth = 20;
		isLightTransparent = true;
		displayMapTooltip = true;
		replaceRotations = false;
	}

	@Override
	public LocalMessage getNewLocalization() {
		return new LocalMessage("object", "placedlog");
	}

	@Override
	public void loadTextures() {
		super.loadTextures();
		texture = GameTexture.fromFile(texturePath);
	}

	@Override
	public LootTable getLootTable(Level level, int layerID, int tileX, int tileY) {
		return new LootTable(new LootItemInterface[]{new LootItem(sourceLogStringID, 1)});
	}

	@Override
	public void addDrawables(List list, OrderableDrawables tileList, Level level, int tileX, int tileY, TickManager tickManager, GameCamera camera, PlayerMob perspective) {
		GameLight light = level.getLightLevel(tileX, tileY);
		int drawX = camera.getTileDrawX(tileX);
		int drawY = camera.getTileDrawY(tileY);
		TextureDrawOptions options = texture.initDraw().light(light).addObjectDamageOverlay(this, level, tileX, tileY).pos(drawX, drawY);
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
		GameLight light = level.getLightLevel(tileX, tileY);
		int drawX = camera.getTileDrawX(tileX);
		int drawY = camera.getTileDrawY(tileY);
		texture.initDraw().light(light).alpha(alpha).draw(drawX, drawY);
	}
}
