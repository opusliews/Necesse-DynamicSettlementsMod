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
import opusliews.container.DynamicCraftingStationContainer;
import opusliews.object.AnvilObjectEntity;

public final class ForgeRequirementUI {
	private ForgeRequirementUI() {
	}

	public static GameMessage getUsableError(Container container, Recipe recipe) {
		if (!(container instanceof DynamicCraftingStationContainer)) return null;
		DynamicCraftingStationContainer stationContainer = (DynamicCraftingStationContainer)container;
		if (!(stationContainer.stationEntity instanceof AnvilObjectEntity)) return null;
		if (!ForgeRequirementSystem.requiresRunningForge(recipe)) return null;

		ForgeRequirementSystem.Status status = ForgeRequirementSystem.getStatus(
				stationContainer.stationEntity,
				recipe,
				stationContainer.getCraftInventories()
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
		if (!hovering || !(container instanceof DynamicCraftingStationContainer)) return;
		DynamicCraftingStationContainer stationContainer = (DynamicCraftingStationContainer)container;
		if (!(stationContainer.stationEntity instanceof AnvilObjectEntity)) return;
		if (!ForgeRequirementSystem.requiresRunningForge(recipe)) return;

		InventoryItem forgeItem = new InventoryItem(ObjectRegistry.getObject("forge").getObjectItem());
		ForgeRequirementSystem.Status status = ForgeRequirementSystem.getStatus(
				stationContainer.stationEntity,
				recipe,
				stationContainer.getCraftInventories()
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
}
