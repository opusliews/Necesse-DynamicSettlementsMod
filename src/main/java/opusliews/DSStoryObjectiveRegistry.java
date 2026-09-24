package opusliews;

import necesse.engine.localization.message.LocalMessage;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.TileRegistry;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.fairType.TypeParsers;
import necesse.inventory.InventoryItem;
import necesse.level.maps.Level;
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

		GuideStoryObjectiveRegistry.registerBeforeFirstVanilla(
				"guide9",
				new LocalMessage("storyguide", "guide9title"),
				new LocalMessage("storyguide", "guide9objective"),
				objective -> hasItem(objective.getPlayer(), "woodsword")
						&& hasItem(objective.getPlayer(), "woodshovel")
		);

		GuideStoryObjectiveRegistry.registerBeforeFirstVanilla(
				"guide10",
				new LocalMessage("storyguide", "guide10title"),
				new LocalMessage("storyguide", "guide10objective"),
				objective -> hasNearbyTile(objective.getPlayer(), "shallowholetile", 4)
		);

		GuideStoryObjectiveRegistry.registerBeforeFirstVanilla(
				"guide11",
				new LocalMessage("storyguide", "guide11title"),
				new LocalMessage("storyguide", "guide11objective"),
				objective -> hasItem(objective.getPlayer(), "trapdoor")
		);

		GuideStoryObjectiveRegistry.registerBeforeFirstVanilla(
				"guide12",
				new LocalMessage("storyguide", "guide12title"),
				new LocalMessage("storyguide", "guide12objective"),
				objective -> hasItem(objective.getPlayer(), "firemone")
		);

		GuideStoryObjectiveRegistry.registerBeforeFirstVanilla(
				"guide13",
				new LocalMessage("storyguide", "guide13title"),
				new LocalMessage("storyguide", "guide13objective"),
				objective -> hasItem(objective.getPlayer(), "firestarter")
		);

		GuideStoryObjectiveRegistry.registerBeforeFirstVanilla(
				"guide14",
				new LocalMessage("storyguide", "guide14title"),
				new LocalMessage("storyguide", "guide14objective"),
				objective -> hasItem(objective.getPlayer(), "crudetorch")
		);

		GuideStoryObjectiveRegistry.registerBeforeFirstVanilla(
				"guide15",
				new LocalMessage("storyguide", "guide15title"),
				new LocalMessage("storyguide", "guide15objective"),
				objective -> hasItem(objective.getPlayer(), "crudepickaxe")
		);

		GuideStoryObjectiveRegistry.registerBeforeFirstVanilla(
				"guide16",
				new LocalMessage("storyguide", "guide16title"),
				new LocalMessage("storyguide", "guide16objective"),
				objective -> hasItem(objective.getPlayer(), "holecaveladder")
		);

		GuideStoryObjectiveRegistry.registerBeforeFirstVanilla(
				"guide17",
				new LocalMessage("storyguide", "guide17title"),
				new LocalMessage("storyguide", "guide17objective"),
				objective -> objective.getPlayer().getLevel() != null
						&& objective.getPlayer().getLevel().isBasicCaveLevel()
		);

		GuideStoryObjectiveRegistry.registerBeforeFirstVanilla(
				"guide18",
				new LocalMessage("storyguide", "guide18title"),
				new LocalMessage("storyguide", "guide18objective"),
				objective -> getItemAmount(objective.getPlayer(), "clay") >= 16
		);

		GuideStoryObjectiveRegistry.registerBeforeFirstVanilla(
				"guide19",
				new LocalMessage("storyguide", "guide19title"),
				new LocalMessage("storyguide", "guide19objective"),
				objective -> getItemAmount(objective.getPlayer(), "unfiredbrick") >= 8
						&& hasItem(objective.getPlayer(), "unfiredsawblademold")
						&& hasItem(objective.getPlayer(), "unfiredthickplatemold")
		);

		GuideStoryObjectiveRegistry.registerBeforeFirstVanilla(
				"guide20",
				new LocalMessage("storyguide", "guide20title"),
				new LocalMessage("storyguide", "guide20objective"),
				objective -> hasItem(objective.getPlayer(), "campfire")
						&& hasItem(objective.getPlayer(), "roastingstation")
		);

		GuideStoryObjectiveRegistry.registerBeforeFirstVanilla(
				"guide21",
				new LocalMessage("storyguide", "guide21title"),
				new LocalMessage("storyguide", "guide21objective",
					new Object[]{"logicon", logIcon}),
				objective -> getItemAmount(objective.getPlayer(), "brick") >= 8
						&& hasItem(objective.getPlayer(), "sawblademold")
						&& hasItem(objective.getPlayer(), "thickplatemold")
		);
	}

	private static boolean hasItem(PlayerMob player, String itemStringID) {
		return getItemAmount(player, itemStringID) > 0;
	}

	private static int getItemAmount(PlayerMob player, String itemStringID) {
		if (player == null || ItemRegistry.getItem(itemStringID) == null) return 0;
		return player.getInv().getAmount(ItemRegistry.getItem(itemStringID), false, false, false, false, "storyguide");
	}

	private static boolean hasNearbyTile(PlayerMob player, String tileStringID, int range) {
		if (player == null) return false;
		Level level = player.getLevel();
		if (level == null) return false;

		int tileID = TileRegistry.getTileID(tileStringID);
		for (int x = player.getTileX() - range; x <= player.getTileX() + range; x++) {
			for (int y = player.getTileY() - range; y <= player.getTileY() + range; y++) {
				if (level.isTileWithinBounds(x, y) && level.getTileID(x, y) == tileID) return true;
			}
		}

		return false;
	}

	public static String getRotatingItemIcon(String... itemStringIDs) {
		return TypeParsers.getItemsParseString(
				Arrays.stream(itemStringIDs)
						.map(InventoryItem::new)
						.collect(Collectors.toList())
		);
	}
}
