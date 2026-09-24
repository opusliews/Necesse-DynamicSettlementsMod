package opusliews.object;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.List;
import necesse.engine.gameLoop.tickManager.Performance;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.util.GameRandom;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.DrawOptionsList;
import necesse.gfx.drawOptions.texture.TextureDrawOptions;
import necesse.gfx.drawables.LevelSortedDrawable;
import necesse.gfx.drawables.OrderableDrawables;
import necesse.gfx.gameTexture.GameTexture;
import necesse.inventory.InventoryItem;
import necesse.inventory.lootTable.LootTable;
import necesse.inventory.lootTable.lootItem.LootItem;
import necesse.inventory.recipe.Tech;
import necesse.level.gameObject.container.CraftingStationUpgrade;
import necesse.level.gameObject.container.IronAnvilObject;
import necesse.level.maps.Level;
import necesse.level.maps.light.GameLight;
import opusliews.DSItemRegistry;
import opusliews.container.CrudeAnvilContainer;
import opusliews.earlygame.CrudeAnvilFeature;
import opusliews.story.GuideStoryObjectiveRegistry;

public class CrudeAnvilObject extends IronAnvilObject {
	private final String stumpTextureName;
	private final String logStringID;
	private final GameRandom drawRandom = new GameRandom();
	private GameTexture stumpTexture;
	private GameTexture stumpRootsTexture;
	private GameTexture plateTexture;

	public CrudeAnvilObject(String stumpTextureName, String logStringID, Color mapColor) {
		super();
		this.stumpTextureName = stumpTextureName;
		this.logStringID = logStringID;
		this.mapColor = mapColor;
		this.hoverHitbox = new Rectangle(0, -16, 32, 48);
	}

	@Override
	public GameMessage getNewLocalization() {
		return new LocalMessage("object", "crudeanvil");
	}

	@Override
	public Tech[] getCraftingTechs() {
		return new Tech[]{CrudeAnvilFeature.tech};
	}

	@Override
	public CraftingStationUpgrade getStationUpgrade() {
		return null;
	}

	@Override
	public void loadTextures() {
		stumpTexture = GameTexture.fromFile("objects/" + stumpTextureName);
		stumpRootsTexture = GameTexture.fromFile("objects/" + stumpTextureName + "roots");
		plateTexture = GameTexture.fromFile("objects/crudeanvilplate");
	}

	@Override
	public Rectangle getCollision(Level level, int x, int y, int rotation) {
		return new Rectangle(x * 32 + 4, y * 32 + 12, 24, 16);
	}

	@Override
	public void addDrawables(List list, OrderableDrawables tileList, Level level, int tileX, int tileY, TickManager tickManager, GameCamera camera, PlayerMob perspective) {
		Performance.record(tickManager, "crudeAnvilSetup", () -> {
			GameLight light = level.getLightLevel(tileX, tileY);
			int drawX = camera.getTileDrawX(tileX);
			int drawY = camera.getTileDrawY(tileY);
			boolean mirror;
			synchronized (drawRandom) {
				drawRandom.setSeed(getTileSeed(tileX, tileY));
				mirror = drawRandom.nextBoolean();
			}

			final TextureDrawOptions rootsOptions = stumpRootsTexture.initDraw()
					.sprite(0, 0, 64)
					.light(light)
					.mirror(mirror, false)
					.pos(drawX - 16, drawY - 32);

			final DrawOptionsList bodyOptions = new DrawOptionsList();
			bodyOptions.add(stumpTexture.initDraw()
					.sprite(0, 0, 64)
					.addObjectDamageOverlay(this, level, tileX, tileY)
					.light(light)
					.mirror(mirror, false)
					.pos(drawX - 16, drawY - 32));
			bodyOptions.add(plateTexture.initDraw().light(light).mirror(mirror, false).pos(drawX - 16, drawY - 32));

			list.add(new LevelSortedDrawable(this, tileX, tileY) {
				@Override
				public int getSortY() {
					return -32;
				}

				@Override
				public void draw(TickManager tickManager) {
					rootsOptions.draw();
				}
			});

			list.add(new LevelSortedDrawable(this, tileX, tileY) {
				@Override
				public int getSortY() {
					return 12;
				}

				@Override
				public void draw(TickManager tickManager) {
					bodyOptions.draw();
				}
			});
		});
	}

	@Override
	public void drawPreview(Level level, int tileX, int tileY, int rotation, float alpha, PlayerMob player, GameCamera camera) {
		GameLight light = level.getLightLevel(tileX, tileY);
		int drawX = camera.getTileDrawX(tileX);
		int drawY = camera.getTileDrawY(tileY);
		boolean mirror;
		synchronized (drawRandom) {
			drawRandom.setSeed(getTileSeed(tileX, tileY));
			mirror = drawRandom.nextBoolean();
		}

		stumpRootsTexture.initDraw().sprite(0, 0, 64).alpha(alpha).light(light).mirror(mirror, false).draw(drawX - 16, drawY - 32);
		stumpTexture.initDraw().sprite(0, 0, 64).alpha(alpha).light(light).mirror(mirror, false).draw(drawX - 16, drawY - 32);
		plateTexture.initDraw().alpha(alpha).light(light).mirror(mirror, false).draw(drawX - 16, drawY - 32);
	}

	@Override
	public boolean canInteract(Level level, int tileX, int tileY, PlayerMob player) {
		InventoryItem selected = player.getSelectedItem();
		return selected != null && selected.item.isGlobalIngredient("anystone");
	}

	@Override
	public void interact(Level level, int tileX, int tileY, PlayerMob player) {
		if (level.isServer() && player.isServerClient()) {
			GuideStoryObjectiveRegistry.complete(
					player.getServerClient(),
					"guide22"
			);
		}

		super.interact(level, tileX, tileY, player);
	}

	@Override
	public LootTable getLootTable(Level level, int layerID, int tileX, int tileY) {
		LootTable lootTable = new LootTable();
		lootTable.items.add(new LootItem(DSItemRegistry.thickIronPlateStringID));
		if (logStringID != null) lootTable.items.add(LootItem.between(logStringID, 4, 5).splitItems(5));
		return lootTable;
	}
}
