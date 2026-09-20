package opusliews.crafting;

import necesse.engine.registries.ContainerRegistry;
import necesse.engine.registries.LevelJobRegistry;
import necesse.engine.registries.PacketRegistry;
import opusliews.container.CraftingTaskBoardContainer;
import opusliews.forms.CraftingTaskBoardContainerForm;
import opusliews.jobs.CraftingStationLevelJob;
import opusliews.logging.Logging;
import opusliews.network.PacketAnvilCraftingSound;
import opusliews.object.CraftingTaskBoardObject;
import opusliews.object.CraftingTaskBoardObjectEntity;

public class CraftingTasksFeature {
	public static int taskBoardContainerID = -1;
	public static int craftingStationJobID = -1;
	private static boolean registered;

	private CraftingTasksFeature() {
	}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;

		CraftingTaskBoardObject.registerBoard();
		PacketRegistry.registerPacket(PacketAnvilCraftingSound.class);
		craftingStationJobID = LevelJobRegistry.registerJob(
				"craftingstation",
				CraftingStationLevelJob.class,
				CraftingStationLevelJob::handler,
				"crafting",
				1000
		);
		Logging.logMessage("[CraftingJob] Registered craftingstation job ID=" + craftingStationJobID);

		taskBoardContainerID = ContainerRegistry.registerOEContainer(
				(client, uniqueSeed, oe, content) -> new CraftingTaskBoardContainerForm(
						client,
						new CraftingTaskBoardContainer(
								client.getClient(),
								uniqueSeed,
								(CraftingTaskBoardObjectEntity)oe
						)
				),
				(client, uniqueSeed, oe, content, serverObject) -> new CraftingTaskBoardContainer(
						client,
						uniqueSeed,
						(CraftingTaskBoardObjectEntity)oe
				)
		);
	}
}
