package opusliews;

import necesse.engine.localization.message.LocalMessage;
import necesse.gfx.fairType.TypeParsers;
import necesse.inventory.InventoryItem;
import opusliews.story.GuideStoryObjectiveRegistry;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static necesse.engine.storyObjectives.objectives.CraftPickaxeStoryObjective.LOG_ITEMS;

public class DSStoryObjectiveRegistry {
	public static void registerObjectives() {
		GuideStoryObjectiveRegistry.registerBeforeFirstVanilla(
				"guide1",
				new LocalMessage("storyguide", "guide1title"),
				new LocalMessage("storyguide", "guide1objective"),
				objective -> objective.getPlayer().getInv().hasAnyItem(
						false, false, false, false, "storyguide",
						item -> item.item.getStringID().equals("stone")
				)
		);

		GuideStoryObjectiveRegistry.registerBeforeFirstVanilla(
				"guide2",
				new LocalMessage("storyguide", "guide2title"),
				new LocalMessage("storyguide", "guide2objective"),
				objective -> objective.getPlayer().getInv().hasAnyItem(
						false, false, false, false, "storyguide",
						item -> item.item.getStringID().equals("sharpenedstone")
				)
		);

		GuideStoryObjectiveRegistry.registerBeforeFirstVanilla(
				"guide3",
				new LocalMessage("storyguide", "guide3title"),
				new LocalMessage("storyguide", "guide3objective"),
				objective -> objective.getPlayer().getInv().hasAnyItem(
						false, false, false, false, "storyguide",
						item -> item.item.getStringID().equals("woodenshaft")
				)
		);

		GuideStoryObjectiveRegistry.registerBeforeFirstVanilla(
				"guide4",
				new LocalMessage("storyguide", "guide4title"),
				new LocalMessage("storyguide", "guide4objective"),
				objective -> objective.getPlayer().getInv().hasAnyItem(
						false, false, false, false, "storyguide",
						item -> item.item.getStringID().equals("grass")
				)
		);

		GuideStoryObjectiveRegistry.registerBeforeFirstVanilla(
				"guide5",
				new LocalMessage("storyguide", "guide5title"),
				new LocalMessage("storyguide", "guide5objective"),
				objective -> objective.getPlayer().getInv().hasAnyItem(
						false, false, false, false, "storyguide",
						item -> item.item.getStringID().equals("string")
				)
		);

		GuideStoryObjectiveRegistry.registerBeforeFirstVanilla(
				"guide6",
				new LocalMessage("storyguide", "guide6title"),
				new LocalMessage("storyguide", "guide6objective"),
				objective -> objective.getPlayer().getInv().hasAnyItem(
						false, false, false, false, "storyguide",
						item -> item.item.getStringID().equals("crudeaxe")
				)
		);

		GuideStoryObjectiveRegistry.registerBeforeFirstVanilla(
				"guide7",
				new LocalMessage("storyguide", "guide7title"),
				new LocalMessage("storyguide", "guide7objective"),
				objective -> objective.getPlayer().getInv().hasAnyItem(
						false, false, false, false, "storyguide",
						item -> item.item.isGlobalIngredient("anylog")
				)
		);

		String logIcon = TypeParsers.getItemsParseString((List)LOG_ITEMS.stream().map(InventoryItem::new).collect(Collectors.toList()));
		String plankIcon = getRotatingItemIcon(
				"oakplank",
				"spruceplank",
				"pineplank",
				"palmplank",
				"birchplank",
				"willowplank",
				"mapleplank",
				"deadwoodplank",
				"bambooplank",
				"dryadplank");
		GuideStoryObjectiveRegistry.registerBeforeFirstVanilla(
				"guide8",
				new LocalMessage("storyguide", "guide8title"),
				new LocalMessage("storyguide", "guide8objective",
					new Object[]{
							"logicon", logIcon,
							"plankicon", plankIcon
					}),
				objective -> objective.getPlayer().getInv().hasAnyItem(
						false, false, false, false, "storyguide",
						item -> item.item.isGlobalIngredient("dsanyplank")
				)
		);
	}

	public static String getRotatingItemIcon(String... itemStringIDs) {
		return TypeParsers.getItemsParseString(
				Arrays.stream(itemStringIDs)
						.map(InventoryItem::new)
						.collect(Collectors.toList())
		);
	}
}
