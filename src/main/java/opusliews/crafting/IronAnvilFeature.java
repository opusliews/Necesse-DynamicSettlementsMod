package opusliews.crafting;

import necesse.engine.network.PacketReader;
import necesse.engine.registries.ContainerRegistry;
import opusliews.container.IronAnvilContainer;
import opusliews.forms.IronAnvilContainerForm;

public class IronAnvilFeature {
	public static int ironAnvilContainerID = -1;

	private IronAnvilFeature() {
	}

	public static void register() {
		if (ironAnvilContainerID != -1) {
			return;
		}

		ironAnvilContainerID = ContainerRegistry.registerSettlementDependantLOContainer(
				(client, uniqueSeed, settlement, levelObject, content) -> new IronAnvilContainerForm(
						client,
						new IronAnvilContainer(
								client.getClient(),
								uniqueSeed,
								settlement,
								levelObject,
								new PacketReader(content)
						)
				),
				(client, uniqueSeed, settlement, levelObject, content, serverObject) -> new IronAnvilContainer(
						client,
						uniqueSeed,
						settlement,
						levelObject,
						new PacketReader(content)
				)
		);
	}
}
