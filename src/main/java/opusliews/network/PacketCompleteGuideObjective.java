package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.StoryObjectiveRegistry;
import necesse.engine.storyObjectives.StoryObjective;
import opusliews.story.GuideStoryObjectiveRegistry;

public class PacketCompleteGuideObjective extends Packet {
	private final String objectiveStringID;

	public PacketCompleteGuideObjective(String objectiveStringID) {
		this.objectiveStringID = objectiveStringID;

		PacketWriter writer = new PacketWriter(this);
		writer.putNextString(objectiveStringID);
	}

	public PacketCompleteGuideObjective(byte[] data) {
		super(data);

		PacketReader reader = new PacketReader(this);
		objectiveStringID = reader.getNextString();
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		if (!client.checkHasRequestedSelf() || client.isDead()) return;
		if (!GuideStoryObjectiveRegistry.isButtonObjective(objectiveStringID)) return;

		int objectiveID = StoryObjectiveRegistry.getObjectiveID(objectiveStringID);
		if (objectiveID < 0) return;

		StoryObjective objective = client.storyManager.getObjective(objectiveID);
		if (objective == null || objective.isCompleted() || !objective.isCurrentObjective()) return;

		GuideStoryObjectiveRegistry.complete(client, objectiveStringID);
	}
}
