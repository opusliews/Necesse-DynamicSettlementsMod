package opusliews.hud;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.entity.objectEntity.interfaces.OEInventory;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.DrawOptionsList;
import necesse.gfx.drawables.SortedDrawable;
import necesse.gfx.ui.HUD;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import necesse.level.maps.hudManager.HudDrawElement;
import opusliews.object.IronAnvilObjectEntity;

public class IronAnvilLinkHud {
	private static final Map<Level, HudDrawElement> elements = new WeakHashMap<>();
	private static Level openLevel;
	private static int openAnvilX;
	private static int openAnvilY;
	private static boolean hasOpenAnvil;

	private IronAnvilLinkHud() {
	}

	public static void ensureAdded(Level level) {
		if (level == null || elements.containsKey(level)) {
			return;
		}

		HudDrawElement element = new HudDrawElement() {
			@Override
			public void addDrawables(List list, GameCamera camera, PlayerMob perspective) {
				IronAnvilObjectEntity anvil = getDisplayedAnvil(level, camera);
				if (anvil == null) {
					return;
				}

				final DrawOptionsList options = new DrawOptionsList();
				addStorageOutline(options, level, camera, anvil.getInputStorage(), new Color(40, 220, 70, 255));
				addStorageOutline(options, level, camera, anvil.getOutputStorage(), new Color(230, 55, 55, 255));

				if (options.isEmpty()) {
					return;
				}

				list.add(new SortedDrawable() {
					@Override
					public int getPriority() {
						return -999999;
					}

					@Override
					public void draw(TickManager tickManager) {
						options.draw();
					}
				});
			}
		};

		level.hudManager.addElement(element);
		elements.put(level, element);
	}

	public static void setOpenAnvil(Level level, int tileX, int tileY) {
		openLevel = level;
		openAnvilX = tileX;
		openAnvilY = tileY;
		hasOpenAnvil = true;
		ensureAdded(level);
	}

	public static void clearOpenAnvil(Level level, int tileX, int tileY) {
		if (hasOpenAnvil && openLevel == level && openAnvilX == tileX && openAnvilY == tileY) {
			hasOpenAnvil = false;
			openLevel = null;
		}
	}

	private static IronAnvilObjectEntity getDisplayedAnvil(Level level, GameCamera camera) {
		if (hasOpenAnvil && openLevel == level) {
			ObjectEntity openEntity = level.entityManager.getObjectEntity(openAnvilX, openAnvilY);
			if (openEntity instanceof IronAnvilObjectEntity) {
				return (IronAnvilObjectEntity)openEntity;
			}
		}

		int mouseX = camera.getMouseLevelTilePosX();
		int mouseY = camera.getMouseLevelTilePosY();
		LevelObject hovered = level.getLevelObject(mouseX, mouseY);
		if (hovered == null) {
			return null;
		}

		LevelObject master = (LevelObject)hovered.getMasterLevelObject().orElse(hovered);
		ObjectEntity hoveredEntity = master.getObjectEntity();
		return hoveredEntity instanceof IronAnvilObjectEntity ? (IronAnvilObjectEntity)hoveredEntity : null;
	}

	private static void addStorageOutline(
			DrawOptionsList options,
			Level level,
			GameCamera camera,
			Point storage,
			Color color
	) {
		if (storage == null) {
			return;
		}

		LevelObject object = level.getLevelObject(storage.x, storage.y);
		if (object == null) {
			return;
		}

		LevelObject master = object.getMasterLevelObject().orElse(null);
		if (master == null || master.tileX != storage.x || master.tileY != storage.y) {
			return;
		}

		ObjectEntity objectEntity = master.getObjectEntity();
		if (!(objectEntity instanceof OEInventory)) {
			return;
		}

		OEInventory inventory = (OEInventory)objectEntity;
		if (inventory.getInventory() == null || inventory.getSettlementStorage() == null) {
			return;
		}

		Rectangle bounds = master.getMultiTile().getTileRectangle(master.tileX, master.tileY);
		options.add(HUD.tileBoundOptions(camera, color, true, bounds));
	}
}
