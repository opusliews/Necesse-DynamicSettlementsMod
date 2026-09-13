package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.PacketWriter;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opus.guard.GuardDutySystem;

@ModMethodPatch(target = HumanMob.class, name = "setupSpawnPacket", arguments = {PacketWriter.class})
public class GuardDutySetupSpawnPacketPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This HumanMob mob, @Advice.Argument(0) PacketWriter writer) {
		if (mob instanceof GuardHumanMob) {
			writer.putNextBoolean(GuardDutySystem.isNightDuty((GuardHumanMob)mob));
		}
	}
}
