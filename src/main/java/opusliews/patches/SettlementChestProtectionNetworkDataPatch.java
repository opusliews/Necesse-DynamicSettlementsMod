package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.level.maps.levelData.settlementData.NetworkSettlementData;
import net.bytebuddy.asm.Advice;
import opusliews.settlement.SettlementChestProtectionSystem;

public class SettlementChestProtectionNetworkDataPatch {
	@ModMethodPatch(target = NetworkSettlementData.class, name = "writePacket", arguments = {PacketWriter.class, boolean.class})
	public static class WritePatch {
		@Advice.OnMethodExit
		public static void onExit(@Advice.This NetworkSettlementData data, @Advice.Argument(0) PacketWriter writer) {
			writer.putNextBoolean(SettlementChestProtectionSystem.resolveNetworkSetting(data));
		}
	}

	@ModMethodPatch(target = NetworkSettlementData.class, name = "readPacket", arguments = {PacketReader.class, boolean.class})
	public static class ReadPatch {
		@Advice.OnMethodExit
		public static void onExit(@Advice.This NetworkSettlementData data, @Advice.Argument(0) PacketReader reader) {
			SettlementChestProtectionSystem.setClientProtected(data, reader.getNextBoolean());
		}
	}
}
