package opusliews.crafting;

import necesse.engine.registries.ContainerRegistry;
import opusliews.container.JobRequestBulletinContainer;
import opusliews.forms.JobRequestBulletinContainerForm;
import opusliews.object.JobRequestBulletinObjectEntity;

public final class JobRequestBulletinFeature {
	public static int containerID = -1;

	private JobRequestBulletinFeature() {
	}

	public static void register() {
		if (containerID != -1) return;

		containerID = ContainerRegistry.registerOEContainer(
				(client, uniqueSeed, objectEntity, content) -> new JobRequestBulletinContainerForm(
						client,
						new JobRequestBulletinContainer(
								client.getClient(),
								uniqueSeed,
								(JobRequestBulletinObjectEntity)objectEntity
						)
				),
				(client, uniqueSeed, objectEntity, content, serverObject) -> new JobRequestBulletinContainer(
						client,
						uniqueSeed,
						(JobRequestBulletinObjectEntity)objectEntity
				)
		);
	}
}
