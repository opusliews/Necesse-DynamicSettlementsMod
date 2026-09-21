package opusliews.earlygame;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.network.PacketReader;
import necesse.engine.registries.ContainerRegistry;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.registries.RecipeTechRegistry;
import necesse.inventory.recipe.Tech;
import opusliews.container.CrudeAnvilContainer;
import opusliews.forms.CrudeAnvilContainerForm;
import opusliews.object.CrudeAnvilObject;

public final class CrudeAnvilFeature {
	public static final String techStringID = "crudeanvil";
	public static Tech tech;
	public static int containerID = -1;
	private static final Variant[] variants = new Variant[]{
			new Variant("oaktreestump", "oaklog", new Color(116, 69, 43)),
			new Variant("sprucetreestump", "sprucelog", new Color(121, 74, 23)),
			new Variant("willowtreestump", "willowlog", new Color(99, 52, 50)),
			new Variant("burnedtreestump", "charredbark", new Color(34, 20, 12))
	};

	private static final Map<String, String> stumpToCrudeAnvil = new HashMap<>();
	private static boolean registered;

	private CrudeAnvilFeature() {
	}

	public static void register() {
		if (registered) return;
		registered = true;

		tech = RecipeTechRegistry.registerTech(
				techStringID,
				"oaktreestump",
				new LocalMessage("object", "crudeanvil")
		);

		for (Variant variant : variants) {
			String crudeAnvilStringID = variant.stumpStringID.replace("treestump", "crudeanvil");
			stumpToCrudeAnvil.put(variant.stumpStringID, crudeAnvilStringID);
			ObjectRegistry.registerObject(
					crudeAnvilStringID,
					new CrudeAnvilObject(variant.stumpStringID, variant.logStringID, variant.mapColor),
					0.0F,
					false
			);
		}

		containerID = ContainerRegistry.registerSettlementDependantLOContainer(
				(client, uniqueSeed, settlement, levelObject, content) -> new CrudeAnvilContainerForm(
						client,
						new CrudeAnvilContainer(client.getClient(), uniqueSeed, settlement, levelObject, new PacketReader(content))
				),
				(client, uniqueSeed, settlement, levelObject, content, serverObject) -> new CrudeAnvilContainer(
						client, uniqueSeed, settlement, levelObject, new PacketReader(content)
				)
		);
	}

	public static int getMatchingCrudeAnvilID(String stumpStringID) {
		if (stumpStringID == null) return -1;
		String crudeAnvilStringID = stumpToCrudeAnvil.get(stumpStringID);
		return crudeAnvilStringID == null ? -1 : ObjectRegistry.getObjectID(crudeAnvilStringID);
	}

	private static class Variant {
		private final String stumpStringID;
		private final String logStringID;
		private final Color mapColor;

		private Variant(String stumpStringID, String logStringID, Color mapColor) {
			this.stumpStringID = stumpStringID;
			this.logStringID = logStringID;
			this.mapColor = mapColor;
		}
	}
}
