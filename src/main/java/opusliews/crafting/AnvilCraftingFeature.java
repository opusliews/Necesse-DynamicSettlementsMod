package opusliews.crafting;

import necesse.engine.network.PacketReader;
import necesse.engine.registries.ContainerRegistry;
import opusliews.container.AnvilContainer;
import opusliews.forms.AnvilContainerForm;

public class AnvilCraftingFeature {
	public static int anvilContainerID = -1;

	private AnvilCraftingFeature() {
	}

	public static void register() {
		if (anvilContainerID != -1) {
			return;
		}

		anvilContainerID = ContainerRegistry.registerSettlementDependantLOContainer(
				(client, uniqueSeed, settlement, levelObject, content) -> new AnvilContainerForm(
						client,
						new AnvilContainer(
								client.getClient(),
								uniqueSeed,
								settlement,
								levelObject,
								new PacketReader(content)
						)
				),
				(client, uniqueSeed, settlement, levelObject, content, serverObject) -> new AnvilContainer(
						client,
						uniqueSeed,
						settlement,
						levelObject,
						new PacketReader(content)
				)
		);
	}
}
