package opusliews.crafting;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.input.Input;
import necesse.engine.input.InputEvent;
import necesse.engine.input.controller.ControllerInput;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.StaticMessage;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.entity.objectEntity.interfaces.OEInventory;
import necesse.gfx.Renderer;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.DrawOptions;
import necesse.gfx.drawables.SortedDrawable;
import necesse.gfx.forms.presets.containerComponent.settlement.SelectTileGameTool;
import necesse.gfx.gameTooltips.GameTooltips;
import necesse.gfx.gameTooltips.InputTooltip;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import necesse.level.maps.TilePosition;
import necesse.level.maps.hudManager.HudDrawElement;
import opusliews.container.DynamicCraftingStationContainer;

import static opusliews.object.DynamicCraftingStationObjectEntity.STORAGE_LINK_RADIUS;

public class CraftingStorageSelectTool extends SelectTileGameTool {
	private final DynamicCraftingStationContainer container;
	private final boolean input;
	private final Runnable finished;
	private final HudDrawElement rangeHud;

	public CraftingStorageSelectTool(
			DynamicCraftingStationContainer container,
			Level level,
			boolean input,
			Runnable finished
	) {
		super(level, new StaticMessage(input ? "Set input storages" : "Set output storages"), true);
		this.container = container;
		this.input = input;
		this.finished = finished;

		rangeHud = new HudDrawElement() {
			@Override
			public void addDrawables(List list, GameCamera camera, PlayerMob perspective) {
				final int minX = container.stationEntity.tileX - STORAGE_LINK_RADIUS;
				final int minY = container.stationEntity.tileY - STORAGE_LINK_RADIUS;
				final int size = STORAGE_LINK_RADIUS * 2 + 1;

				list.add(new SortedDrawable() {
					@Override
					public int getPriority() {
						return -1000001;
					}

					@Override
					public void draw(TickManager tickManager) {
						int drawX = camera.getTileDrawX(minX);
						int drawY = camera.getTileDrawY(minY);
						Renderer.initQuadDraw(size * 32, size * 32)
								.color(new Color(40, 100, 255, 38))
								.draw(drawX, drawY);
						Renderer.drawRectangleLines(
								new Rectangle(minX * 32, minY * 32, size * 32, size * 32),
								camera,
								0.15F,
								0.4F,
								1.0F,
								1.0F
						);
					}
				});
			}
		};
		level.hudManager.addElement(rangeHud);
	}

	@Override
	public DrawOptions getIconTexture(Color color, int drawX, int drawY) {
		return null;
	}

	@Override
	public GameTooltips getTooltips() {
		ListGameTooltips tooltips = new ListGameTooltips();

		if (lastHoverErr != null && displayErrorTip) {
			tooltips.add(lastHoverErr.translate());
		}

		if (tooltip != null) {
			if (Input.lastInputIsController) {
				tooltips.add(new InputTooltip(ControllerInput.MENU_SELECT, tooltip.translate()));
				tooltips.add(new InputTooltip(ControllerInput.MENU_BACK, "Done"));
			} else {
				tooltips.add(new InputTooltip(-100, tooltip.translate()));
				tooltips.add(new InputTooltip(-99, "Done"));
			}
		}

		return tooltips.size() == 0 ? null : tooltips;
	}

	@Override
	public boolean inputEvent(InputEvent event) {
		boolean handled = super.inputEvent(event);

		if (handled) {
			return true;
		}

		if (event.getID() == -100 && event.state) {
			return true;
		}

		return false;
	}

	@Override
	public boolean onSelected(InputEvent event, TilePosition pos) {
		if (pos == null) {
			finished.run();
			return true;
		}

		LevelObject master = getMaster(pos);
		Point target = master == null
				? new Point(pos.tileX, pos.tileY)
				: new Point(master.tileX, master.tileY);

		if (input) {
			container.setInputStorage.runAndSend(target.x, target.y);
		} else {
			container.setOutputStorage.runAndSend(target.x, target.y);
		}

		return false;
	}

	@Override
	public GameMessage isValidTile(TilePosition pos) {
		lastHoverBounds = null;

		if (!container.stationEntity.isWithinStorageLinkRange(pos.tileX, pos.tileY)) {
			return new StaticMessage("Storage must be within " + STORAGE_LINK_RADIUS + " tiles of the crafting station");
		}

		LevelObject master = getMaster(pos);
		if (master == null) return new StaticMessage("Must be a storage container");

		Point target = new Point(master.tileX, master.tileY);
		lastHoverBounds = master.getMultiTile().getTileRectangle(master.tileX, master.tileY);

		if (!container.stationEntity.isWithinStorageLinkRange(target.x, target.y)) {
			return new StaticMessage("Storage must be within " + STORAGE_LINK_RADIUS + " tiles of the crafting station");
		}

		boolean alreadyLinked = input
				? container.stationEntity.hasInputStorage(target)
				: container.stationEntity.hasOutputStorage(target);
		if (alreadyLinked) return null;

		ObjectEntity objectEntity = master.getObjectEntity();
		if (!(objectEntity instanceof OEInventory) || objectEntity == container.stationEntity) {
			return new StaticMessage("Must be a storage container");
		}

		OEInventory inventory = (OEInventory)objectEntity;
		if (inventory.getInventory() == null || inventory.getSettlementStorage() == null) {
			return new StaticMessage("Must be a storage container");
		}

		if (input && container.stationEntity.isStorageInputForOtherStation(target)) {
			return new StaticMessage("This container is already linked as another crafting station input");
		}

		return null;
	}

	private LevelObject getMaster(TilePosition pos) {
		LevelObject object = pos.object();
		if (object == null) {
			return null;
		}
		return (LevelObject)object.getMasterLevelObject().orElse(null);
	}

	@Override
	public void isCancelled() {
		rangeHud.remove();
		super.isCancelled();
	}

	@Override
	public void isCleared() {
		rangeHud.remove();
		super.isCleared();
	}
}
