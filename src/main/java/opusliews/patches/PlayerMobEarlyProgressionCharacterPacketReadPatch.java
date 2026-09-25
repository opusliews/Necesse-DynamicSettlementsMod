package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.PacketReader;
import necesse.entity.mobs.PlayerMob;
import net.bytebuddy.asm.Advice;
import opusliews.progression.EarlyHealthProgressionSystem;

@ModMethodPatch(target = PlayerMob.class, name = "applyLoadedCharacterPacket", arguments = {PacketReader.class})
public class PlayerMobEarlyProgressionCharacterPacketReadPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This PlayerMob player, @Advice.Argument(0) PacketReader reader) {
		EarlyHealthProgressionSystem.applyCharacterPacket(player, reader);
	}
}
