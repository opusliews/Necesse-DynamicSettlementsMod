package opusliews.crafting;

import necesse.engine.network.PacketReader;
import necesse.engine.registries.ContainerRegistry;
import opusliews.container.DynamicCraftingStationContainer;
import opusliews.forms.DynamicCraftingStationContainerForm;

public final class CraftingStationFeature {
	public static int containerID = -1;

	private CraftingStationFeature() {
	}

	public static void register() {
		if (containerID != -1) return;

		containerID = ContainerRegistry.registerSettlementDependantLOContainer(
				(client, uniqueSeed, settlement, levelObject, content) -> new DynamicCraftingStationContainerForm(
						client,
						new DynamicCraftingStationContainer(
								client.getClient(),
								uniqueSeed,
								settlement,
								levelObject,
								new PacketReader(content)
						)
				),
				(client, uniqueSeed, settlement, levelObject, content, serverObject) -> new DynamicCraftingStationContainer(
						client,
						uniqueSeed,
						settlement,
						levelObject,
						new PacketReader(content)
				)
		);
	}
}
