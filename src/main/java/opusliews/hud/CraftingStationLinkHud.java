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
import necesse.entity.objectEntity.ProcessingForgeObjectEntity;
import necesse.entity.objectEntity.interfaces.OEInventory;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.DrawOptionsList;
import necesse.gfx.drawables.SortedDrawable;
import necesse.gfx.ui.HUD;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import necesse.level.maps.hudManager.HudDrawElement;
import opusliews.object.CraftingTaskBoardObjectEntity;
import opusliews.object.DynamicCraftingStationObjectEntity;

public class CraftingStationLinkHud {
	private static final Color INPUT_COLOR = new Color(40, 220, 70, 255);
	private static final Color OUTPUT_COLOR = new Color(230, 55, 55, 255);
	private static final Color TASK_LINK_COLOR = new Color(40, 220, 70, 255);
	private static final Color FORGE_LINK_COLOR = new Color(255, 170, 80, 255);

	private static final Map<Level, HudDrawElement> elements = new WeakHashMap<>();
	private static Level openStationLevel;
	private static int openStationX;
	private static int openStationY;
	private static boolean hasOpenStation;
	private static Level openBoardLevel;
	private static int openBoardX;
	private static int openBoardY;
	private static boolean hasOpenBoard;

	private CraftingStationLinkHud() {
	}

	public static void ensureAdded(Level level) {
		if (level == null || elements.containsKey(level)) {
			return;
		}

		HudDrawElement element = new HudDrawElement() {
			@Override
			public void addDrawables(List list, GameCamera camera, PlayerMob perspective) {
				final DrawOptionsList options = new DrawOptionsList();

				DynamicCraftingStationObjectEntity station = getDisplayedStation(level, camera);
				if (station != null) {
					for (Point storage : station.getInputStorages()) {
						addStorageOutline(options, level, camera, storage, INPUT_COLOR, 0);
					}
					for (Point storage : station.getOutputStorages()) {
						int inset = station.getInputStorages().contains(storage) ? 3 : 0;
						addStorageOutline(options, level, camera, storage, OUTPUT_COLOR, inset);
					}
					addTaskBoardOutline(options, level, camera, station.getTaskBoard(), TASK_LINK_COLOR);
					for (Point forge : station.getLinkedForges()) {
						addForgeOutline(options, level, camera, forge, FORGE_LINK_COLOR);
					}
				}

				CraftingTaskBoardObjectEntity board = getDisplayedBoard(level, camera);
				if (board != null) {
					addStationOutline(options, level, camera, board.getLinkedStation(), TASK_LINK_COLOR);
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

	public static void setOpenStation(Level level, int tileX, int tileY) {
		openStationLevel = level;
		openStationX = tileX;
		openStationY = tileY;
		hasOpenStation = true;
		ensureAdded(level);
	}

	public static void clearOpenStation(Level level, int tileX, int tileY) {
		if (hasOpenStation && openStationLevel == level && openStationX == tileX && openStationY == tileY) {
			hasOpenStation = false;
			openStationLevel = null;
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

	private static DynamicCraftingStationObjectEntity getDisplayedStation(Level level, GameCamera camera) {
		if (hasOpenStation && openStationLevel == level) {
			ObjectEntity openEntity = level.entityManager.getObjectEntity(openStationX, openStationY);
			if (openEntity instanceof DynamicCraftingStationObjectEntity) {
				return (DynamicCraftingStationObjectEntity)openEntity;
			}
		}

		if (hasOpenBoard && openBoardLevel == level) {
			return null;
		}

		ObjectEntity hoveredEntity = getHoveredMasterEntity(level, camera);
		return hoveredEntity instanceof DynamicCraftingStationObjectEntity ? (DynamicCraftingStationObjectEntity)hoveredEntity : null;
	}

	private static CraftingTaskBoardObjectEntity getDisplayedBoard(Level level, GameCamera camera) {
		if (hasOpenBoard && openBoardLevel == level) {
			ObjectEntity openEntity = level.entityManager.getObjectEntity(openBoardX, openBoardY);
			if (openEntity instanceof CraftingTaskBoardObjectEntity) {
				return (CraftingTaskBoardObjectEntity)openEntity;
			}
		}

		if (hasOpenStation && openStationLevel == level) {
			return null;
		}

		ObjectEntity hoveredEntity = getHoveredMasterEntity(level, camera);
		return hoveredEntity instanceof CraftingTaskBoardObjectEntity
				? (CraftingTaskBoardObjectEntity)hoveredEntity
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
			Color color,
			int inset
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

		addMasterOutline(options, camera, master, color, inset);
	}

	private static void addTaskBoardOutline(
			DrawOptionsList options,
			Level level,
			GameCamera camera,
			Point point,
			Color color
	) {
		LevelObject master = point == null ? null : getStoredMaster(level, point);
		if (master != null && master.getObjectEntity() instanceof CraftingTaskBoardObjectEntity) {
			addMasterOutline(options, camera, master, color);
		}
	}

	private static void addForgeOutline(
			DrawOptionsList options,
			Level level,
			GameCamera camera,
			Point point,
			Color color
	) {
		LevelObject master = point == null ? null : getStoredMaster(level, point);
		if (master != null && master.getObjectEntity() instanceof ProcessingForgeObjectEntity) {
			addMasterOutline(options, camera, master, color);
		}
	}

	private static void addStationOutline(
			DrawOptionsList options,
			Level level,
			GameCamera camera,
			Point point,
			Color color
	) {
		LevelObject master = point == null ? null : getStoredMaster(level, point);
		if (master != null && master.getObjectEntity() instanceof DynamicCraftingStationObjectEntity) {
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
		addMasterOutline(options, camera, master, color, 0);
	}

	private static void addMasterOutline(
			DrawOptionsList options,
			GameCamera camera,
			LevelObject master,
			Color color,
			int inset
	) {
		Rectangle bounds = master.getMultiTile().getTileRectangle(master.tileX, master.tileY);
		if (inset <= 0) {
			options.add(HUD.tileBoundOptions(camera, color, true, bounds));
			return;
		}

		Rectangle pixelBounds = new Rectangle(
				bounds.x * 32 + inset,
				bounds.y * 32 + inset,
				bounds.width * 32 - inset * 2,
				bounds.height * 32 - inset * 2
		);
		options.add(HUD.levelBoundOptions(camera, color, true, pixelBounds));
	}
}
