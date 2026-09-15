package opusliews.crafting;

import java.awt.Color;
import java.awt.Point;
import necesse.engine.input.Input;
import necesse.engine.input.InputEvent;
import necesse.engine.input.controller.ControllerInput;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.StaticMessage;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.gfx.drawOptions.DrawOptions;
import necesse.gfx.forms.presets.containerComponent.settlement.SelectTileGameTool;
import necesse.gfx.gameTooltips.GameTooltips;
import necesse.gfx.gameTooltips.InputTooltip;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import necesse.level.maps.TilePosition;
import opusliews.container.IronAnvilContainer;
import opusliews.object.AnvilCraftingTaskBoardObjectEntity;

public class IronAnvilTaskBoardSelectTool extends SelectTileGameTool {
	private final IronAnvilContainer container;
	private final Runnable finished;

	public IronAnvilTaskBoardSelectTool(
			IronAnvilContainer container,
			Level level,
			Runnable finished
	) {
		super(level, new StaticMessage("Select task board"), true);
		this.container = container;
		this.finished = finished;
	}

	@Override
	public DrawOptions getIconTexture(Color color, int drawX, int drawY) {
		return null;
	}

	@Override
	public boolean inputEvent(InputEvent event) {
		boolean handled = super.inputEvent(event);

		if (handled) {
			return true;
		}

		// onSelected deliberately returns false so the selector stays active.
		// Consume left clicks anyway so they cannot also trigger the held item.
		if (event.getID() == -100 && event.state) {
			return true;
		}

		return false;
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
	public boolean onSelected(InputEvent event, TilePosition pos) {
		if (pos == null) {
			finished.run();
			return true;
		}

		LevelObject master = getMaster(pos);
		Point target = master == null
				? new Point(pos.tileX, pos.tileY)
				: new Point(master.tileX, master.tileY);

		container.setTaskBoard.runAndSend(target.x, target.y);
		return false;
	}

	@Override
	public GameMessage isValidTile(TilePosition pos) {
		lastHoverBounds = null;

		LevelObject master = getMaster(pos);
		Point current = container.anvilEntity.getTaskBoard();

		if (master == null) {
			if (current != null && current.x == pos.tileX && current.y == pos.tileY) {
				return null;
			}

			return new StaticMessage("Must be an Anvil Crafting Tasks board");
		}

		Point target = new Point(master.tileX, master.tileY);
		lastHoverBounds = master.getMultiTile().getTileRectangle(master.tileX, master.tileY);

		if (current != null && current.equals(target)) {
			return null;
		}

		ObjectEntity entity = master.getObjectEntity();
		if (!(entity instanceof AnvilCraftingTaskBoardObjectEntity)) {
			return new StaticMessage("Must be an Anvil Crafting Tasks board");
		}

		Point owner = ((AnvilCraftingTaskBoardObjectEntity)entity).getLinkedAnvil();
		if (owner != null && (owner.x != container.anvilEntity.tileX || owner.y != container.anvilEntity.tileY)) {
			return new StaticMessage("This board is already linked to another anvil");
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
}