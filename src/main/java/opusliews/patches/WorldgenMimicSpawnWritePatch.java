package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.PacketWriter;
import necesse.entity.mobs.Mob;
import net.bytebuddy.asm.Advice;
import opusliews.worldgengating.WorldgenSpecialLootGatingSystem;

@ModMethodPatch(target = Mob.class, name = "setupSpawnPacket", arguments = {PacketWriter.class})
public class WorldgenMimicSpawnWritePatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This Mob mob, @Advice.Argument(0) PacketWriter writer) {
		WorldgenSpecialLootGatingSystem.writeMimicSpawnState(mob, writer);
	}
}
