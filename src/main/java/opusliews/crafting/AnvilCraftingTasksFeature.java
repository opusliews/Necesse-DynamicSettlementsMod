package opusliews.crafting;

import necesse.engine.registries.ContainerRegistry;
import necesse.inventory.container.Container;
import opusliews.container.AnvilCraftingTaskBoardContainer;
import opusliews.forms.AnvilCraftingTaskBoardContainerForm;
import opusliews.object.AnvilCraftingTaskBoardObject;
import opusliews.object.AnvilCraftingTaskBoardObjectEntity;

public class AnvilCraftingTasksFeature {
	public static int taskBoardContainerID = -1;
	private static boolean registered;

	private AnvilCraftingTasksFeature() {
	}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;

		AnvilCraftingTaskBoardObject.registerBoard();
		taskBoardContainerID = ContainerRegistry.registerOEContainer(
				(client, uniqueSeed, oe, content) -> new AnvilCraftingTaskBoardContainerForm(
						client,
						new AnvilCraftingTaskBoardContainer(
								client.getClient(),
								uniqueSeed,
								(AnvilCraftingTaskBoardObjectEntity)oe
						)
				),
				(client, uniqueSeed, oe, content, serverObject) -> new AnvilCraftingTaskBoardContainer(
						client,
						uniqueSeed,
						(AnvilCraftingTaskBoardObjectEntity)oe
				)
		);
	}
}
