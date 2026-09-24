package opusliews.forge;

import necesse.engine.localization.Localization;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.registries.ObjectRegistry;
import necesse.gfx.GameBackground;
import necesse.gfx.fairType.FairType;
import necesse.gfx.fairType.TypeParsers;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.gameTooltips.FairTypeTooltip;
import necesse.gfx.gameTooltips.GameTooltipManager;
import necesse.gfx.gameTooltips.TooltipLocation;
import necesse.inventory.InventoryItem;
import necesse.inventory.container.Container;
import necesse.inventory.recipe.Recipe;
import opusliews.container.CrudeAnvilContainer;
import opusliews.container.DynamicCraftingStationContainer;
import opusliews.object.DynamicCraftingStationObjectEntity;
import opusliews.object.AnvilObjectEntity;

public final class ForgeRequirementUI {
	private ForgeRequirementUI() {
	}

	public static GameMessage getUsableError(Container container, Recipe recipe) {
		DynamicCraftingStationObjectEntity station = getForgeStation(container);
		if (station == null || !ForgeRequirementSystem.requiresRunningForge(recipe)) return null;

		ForgeRequirementSystem.Status status = ForgeRequirementSystem.getStatus(
				station, recipe, container.getCraftInventories()
		);
		if (status == ForgeRequirementSystem.Status.NO_LINKED_FORGE) {
			return new LocalMessage("ui", "craftingrequireslinkedforge");
		}
		if (status == ForgeRequirementSystem.Status.NO_FUEL) {
			return new LocalMessage("ui", "craftingrequiresforgefuel");
		}
		return null;
	}

	public static void addRequirementTooltip(Container container, Recipe recipe, boolean hovering) {
		if (!hovering) return;
		DynamicCraftingStationObjectEntity station = getForgeStation(container);
		if (station == null || !ForgeRequirementSystem.requiresRunningForge(recipe)) return;

		InventoryItem forgeItem = new InventoryItem(ObjectRegistry.getObject("forge").getObjectItem());
		ForgeRequirementSystem.Status status = ForgeRequirementSystem.getStatus(
				station, recipe, container.getCraftInventories()
		);
		String color = status == ForgeRequirementSystem.Status.READY ? "§a" : "§c";
		String text = color + Localization.translate(
				"ui",
				"requiresrunningforge",
				"forge",
				TypeParsers.getItemParseString(forgeItem)
		) + "§!";
		FairType fairType = new FairType();
		fairType.append(new FontOptions(12).outline(), text);
		fairType.applyParsers(TypeParsers.ItemIcon(16), TypeParsers.GAME_COLOR);
		GameTooltipManager.addTooltip(new FairTypeTooltip(fairType), GameBackground.getItemTooltipBackground(), TooltipLocation.FORM_FOCUS);
	}
	private static DynamicCraftingStationObjectEntity getForgeStation(Container container) {
		if (container instanceof CrudeAnvilContainer) {
			return ((CrudeAnvilContainer)container).stationEntity;
		}
		if (container instanceof DynamicCraftingStationContainer) {
			DynamicCraftingStationContainer stationContainer = (DynamicCraftingStationContainer)container;
			return stationContainer.stationEntity instanceof AnvilObjectEntity ? stationContainer.stationEntity : null;
		}
		return null;
	}
}
