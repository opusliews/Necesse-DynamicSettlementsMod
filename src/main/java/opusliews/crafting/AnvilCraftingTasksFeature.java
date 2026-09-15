package opusliews.crafting;

import necesse.engine.registries.ContainerRegistry;
import necesse.engine.registries.LevelJobRegistry;
import necesse.engine.registries.PacketRegistry;
import opusliews.container.AnvilCraftingTaskBoardContainer;
import opusliews.forms.AnvilCraftingTaskBoardContainerForm;
import opusliews.jobs.AnvilCraftingLevelJob;
import opusliews.network.PacketAnvilCraftingSound;
import opusliews.object.AnvilCraftingTaskBoardObject;
import opusliews.object.AnvilCraftingTaskBoardObjectEntity;

public class AnvilCraftingTasksFeature {
	public static int taskBoardContainerID = -1;
	public static int anvilCraftingJobID = -1;
	private static boolean registered;

	private AnvilCraftingTasksFeature() {
	}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;

		AnvilCraftingTaskBoardObject.registerBoard();
		PacketRegistry.registerPacket(PacketAnvilCraftingSound.class);
		anvilCraftingJobID = LevelJobRegistry.registerJob(
				"anvilcrafting",
				AnvilCraftingLevelJob.class,
				AnvilCraftingLevelJob::handler,
				"crafting",
				1000
		);

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
