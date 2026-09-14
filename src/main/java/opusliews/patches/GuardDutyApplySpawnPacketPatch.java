package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.PacketReader;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.guard.GuardDuty;
import opusliews.guard.GuardDutySystem;
import opusliews.guard.GuardFatigueSystem;

@ModMethodPatch(target = HumanMob.class, name = "applySpawnPacket", arguments = {PacketReader.class})
public class GuardDutyApplySpawnPacketPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This HumanMob mob, @Advice.Argument(0) PacketReader reader) {
		if (mob instanceof GuardHumanMob) {
			GuardHumanMob guard = (GuardHumanMob)mob;
			GuardDutySystem.setDuty(guard, reader.getNextBoolean() ? GuardDuty.NIGHT : GuardDuty.DAY);
			GuardFatigueSystem.applyClientFatigue(guard, reader.getNextInt());
		}
	}
}
