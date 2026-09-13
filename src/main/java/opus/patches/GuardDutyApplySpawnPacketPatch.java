package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.PacketReader;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opus.guard.GuardDuty;
import opus.guard.GuardDutySystem;

@ModMethodPatch(target = HumanMob.class, name = "applySpawnPacket", arguments = {PacketReader.class})
public class GuardDutyApplySpawnPacketPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This HumanMob mob, @Advice.Argument(0) PacketReader reader) {
		if (mob instanceof GuardHumanMob) {
			GuardDutySystem.setDuty(
					(GuardHumanMob)mob,
					reader.getNextBoolean() ? GuardDuty.NIGHT : GuardDuty.DAY
			);
		}
	}
}
