package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.PacketReader;
import necesse.entity.mobs.Mob;
import net.bytebuddy.asm.Advice;
import opusliews.worldgengating.WorldgenSpecialLootGatingSystem;

@ModMethodPatch(target = Mob.class, name = "applySpawnPacket", arguments = {PacketReader.class})
public class WorldgenMimicSpawnReadPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This Mob mob, @Advice.Argument(0) PacketReader reader) {
		WorldgenSpecialLootGatingSystem.readMimicSpawnState(mob, reader);
	}
}
