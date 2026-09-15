package opusliews;

import necesse.engine.registries.ItemRegistry;
import necesse.inventory.item.Item;
import necesse.inventory.item.matItem.MatItem;
import opusliews.armor.BuilderBootsArmorItem;
import opusliews.armor.BuilderHatArmorItem;
import opusliews.armor.BuilderShirtArmorItem;
import opusliews.item.BlueprintItem;
import opusliews.item.InspectionGlassItem;
import opusliews.item.MalignanceGogglesItem;
import opusliews.item.ProjectEraserItem;


public class DSItemRegistry {
	public static final String featherStringID = "feather";
	public static final String stringStringID = "string";
	public static final String groundFiremoneStringID = "groundfiremone";
	public static final String honeycombFragmentStringID = "honeycombfragment";
	public static final String waxStringID = "wax";

	public static void registerItems() {
		ItemRegistry.registerItem("builderhat",
				new BuilderHatArmorItem(), 50.0F, true);
		ItemRegistry.registerItem("buildershirt",
				new BuilderShirtArmorItem(), 50.0F, true);
		ItemRegistry.registerItem("builderboots",
				new BuilderBootsArmorItem(), 50.0F, true);
		ItemRegistry.registerItem("blueprintItem",
				new BlueprintItem(), 25.0F, true);
		ItemRegistry.registerItem("projecteraser",
				new ProjectEraserItem(), 30.0F, true);
		ItemRegistry.registerItem("inspectionglass",
				new InspectionGlassItem(), 20.0F, true);
		ItemRegistry.registerItem("malignancegoggles",
				new MalignanceGogglesItem(), 100.0F, true);

		ItemRegistry.registerItem(featherStringID,
				new MatItem(500, Item.Rarity.NORMAL).setItemCategory(new String[]{"materials", "mobdrops"}),
				3.0F,
				true
		);
		ItemRegistry.registerItem(stringStringID,
				new MatItem(500, Item.Rarity.NORMAL).setItemCategory(new String[]{"materials"}),
				2.0F,
				true
		);
		ItemRegistry.registerItem(groundFiremoneStringID,
				new MatItem(500, Item.Rarity.NORMAL).setItemCategory(new String[]{"materials"}),
				8.0F,
				true
		);
		ItemRegistry.registerItem(honeycombFragmentStringID,
				new MatItem(500, Item.Rarity.NORMAL).setItemCategory(new String[]{"materials"}),
				5.0F,
				true
		);
		ItemRegistry.registerItem(waxStringID,
				new MatItem(500, Item.Rarity.NORMAL).setItemCategory(new String[]{"materials"}),
				6.0F,
				true
		);
	}
}
