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
import opusliews.object.AnvilCraftingTaskBoardObjectEntity;
import opusliews.object.AnvilObjectEntity;

public class AnvilLinkHud {
	private static final Color INPUT_COLOR = new Color(40, 220, 70, 255);
	private static final Color OUTPUT_COLOR = new Color(230, 55, 55, 255);
	private static final Color TASK_LINK_COLOR = new Color(40, 220, 70, 255);

	private static final Map<Level, HudDrawElement> elements = new WeakHashMap<>();
	private static Level openAnvilLevel;
	private static int openAnvilX;
	private static int openAnvilY;
	private static boolean hasOpenAnvil;
	private static Level openBoardLevel;
	private static int openBoardX;
	private static int openBoardY;
	private static boolean hasOpenBoard;

	private AnvilLinkHud() {
	}

	public static void ensureAdded(Level level) {
		if (level == null || elements.containsKey(level)) {
			return;
		}

		HudDrawElement element = new HudDrawElement() {
			@Override
			public void addDrawables(List list, GameCamera camera, PlayerMob perspective) {
				final DrawOptionsList options = new DrawOptionsList();

				AnvilObjectEntity anvil = getDisplayedAnvil(level, camera);
				if (anvil != null) {
					addStorageOutline(options, level, camera, anvil.getInputStorage(), INPUT_COLOR);
					addStorageOutline(options, level, camera, anvil.getOutputStorage(), OUTPUT_COLOR);
					addTaskBoardOutline(options, level, camera, anvil.getTaskBoard(), TASK_LINK_COLOR);
				}

				AnvilCraftingTaskBoardObjectEntity board = getDisplayedBoard(level, camera);
				if (board != null) {
					addAnvilOutline(options, level, camera, board.getLinkedAnvil(), TASK_LINK_COLOR);
				}

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
		openAnvilLevel = level;
		openAnvilX = tileX;
		openAnvilY = tileY;
		hasOpenAnvil = true;
		ensureAdded(level);
	}

	public static void clearOpenAnvil(Level level, int tileX, int tileY) {
		if (hasOpenAnvil && openAnvilLevel == level && openAnvilX == tileX && openAnvilY == tileY) {
			hasOpenAnvil = false;
			openAnvilLevel = null;
		}
	}

	public static void setOpenTaskBoard(Level level, int tileX, int tileY) {
		openBoardLevel = level;
		openBoardX = tileX;
		openBoardY = tileY;
		hasOpenBoard = true;
		ensureAdded(level);
	}

	public static void clearOpenTaskBoard(Level level, int tileX, int tileY) {
		if (hasOpenBoard && openBoardLevel == level && openBoardX == tileX && openBoardY == tileY) {
			hasOpenBoard = false;
			openBoardLevel = null;
		}
	}

	private static AnvilObjectEntity getDisplayedAnvil(Level level, GameCamera camera) {
		if (hasOpenAnvil && openAnvilLevel == level) {
			ObjectEntity openEntity = level.entityManager.getObjectEntity(openAnvilX, openAnvilY);
			if (openEntity instanceof AnvilObjectEntity) {
				return (AnvilObjectEntity)openEntity;
			}
		}

		if (hasOpenBoard && openBoardLevel == level) {
			return null;
		}

		ObjectEntity hoveredEntity = getHoveredMasterEntity(level, camera);
		return hoveredEntity instanceof AnvilObjectEntity ? (AnvilObjectEntity)hoveredEntity : null;
	}

	private static AnvilCraftingTaskBoardObjectEntity getDisplayedBoard(Level level, GameCamera camera) {
		if (hasOpenBoard && openBoardLevel == level) {
			ObjectEntity openEntity = level.entityManager.getObjectEntity(openBoardX, openBoardY);
			if (openEntity instanceof AnvilCraftingTaskBoardObjectEntity) {
				return (AnvilCraftingTaskBoardObjectEntity)openEntity;
			}
		}

		if (hasOpenAnvil && openAnvilLevel == level) {
			return null;
		}

		ObjectEntity hoveredEntity = getHoveredMasterEntity(level, camera);
		return hoveredEntity instanceof AnvilCraftingTaskBoardObjectEntity
				? (AnvilCraftingTaskBoardObjectEntity)hoveredEntity
				: null;
	}

	private static ObjectEntity getHoveredMasterEntity(Level level, GameCamera camera) {
		int mouseX = camera.getMouseLevelTilePosX();
		int mouseY = camera.getMouseLevelTilePosY();
		LevelObject hovered = level.getLevelObject(mouseX, mouseY);
		if (hovered == null) {
			return null;
		}

		LevelObject master = (LevelObject)hovered.getMasterLevelObject().orElse(hovered);
		return master.getObjectEntity();
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

		LevelObject master = getStoredMaster(level, storage);
		if (master == null) {
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

		addMasterOutline(options, camera, master, color);
	}

	private static void addTaskBoardOutline(
			DrawOptionsList options,
			Level level,
			GameCamera camera,
			Point point,
			Color color
	) {
		LevelObject master = point == null ? null : getStoredMaster(level, point);
		if (master != null && master.getObjectEntity() instanceof AnvilCraftingTaskBoardObjectEntity) {
			addMasterOutline(options, camera, master, color);
		}
	}

	private static void addAnvilOutline(
			DrawOptionsList options,
			Level level,
			GameCamera camera,
			Point point,
			Color color
	) {
		LevelObject master = point == null ? null : getStoredMaster(level, point);
		if (master != null && master.getObjectEntity() instanceof AnvilObjectEntity) {
			addMasterOutline(options, camera, master, color);
		}
	}

	private static LevelObject getStoredMaster(Level level, Point point) {
		LevelObject object = level.getLevelObject(point.x, point.y);
		if (object == null) {
			return null;
		}

		LevelObject master = (LevelObject)object.getMasterLevelObject().orElse(null);
		if (master == null || master.tileX != point.x || master.tileY != point.y) {
			return null;
		}
		return master;
	}

	private static void addMasterOutline(DrawOptionsList options, GameCamera camera, LevelObject master, Color color) {
		Rectangle bounds = master.getMultiTile().getTileRectangle(master.tileX, master.tileY);
		options.add(HUD.tileBoundOptions(camera, color, true, bounds));
	}
}
