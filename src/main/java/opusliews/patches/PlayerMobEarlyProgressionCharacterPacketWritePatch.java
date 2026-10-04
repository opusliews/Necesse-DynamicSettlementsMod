package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.PacketWriter;
import necesse.entity.mobs.PlayerMob;
import net.bytebuddy.asm.Advice;
import opusliews.progression.EarlyHealthProgressionSystem;
import opusliews.progression.GuideProgressionSystem;
import opusliews.worldgengating.WorldgenStationProgressionSystem;

@ModMethodPatch(target = PlayerMob.class, name = "setupLoadedCharacterPacket", arguments = {PacketWriter.class})
public class PlayerMobEarlyProgressionCharacterPacketWritePatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This PlayerMob player, @Advice.Argument(0) PacketWriter writer) {
		EarlyHealthProgressionSystem.writeCharacterPacket(player, writer);
		WorldgenStationProgressionSystem.writeCharacterPacket(player, writer);
		GuideProgressionSystem.writeCharacterPacket(player, writer);
	}
}
