package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.PacketWriter;
import necesse.inventory.container.settlement.SettlementContainerObjectStatusManager;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementLevelObjectStatusSupport;

/** Writes level-aware storage/workstation status into settlement-aware object containers. */
@ModMethodPatch(target = SettlementContainerObjectStatusManager.class, name = "writeContent", arguments = {ServerSettlementData.class, Level.class, int.class, int.class, PacketWriter.class})
public class SettlementContainerObjectStatusWriteMultiLevelPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.Argument(0) ServerSettlementData settlement,
			@Advice.Argument(1) Level level,
			@Advice.Argument(2) int tileX,
			@Advice.Argument(3) int tileY,
			@Advice.Argument(4) PacketWriter writer) {
		return SettlementLevelObjectStatusSupport.writeCustomContent(settlement, level, tileX, tileY, writer);
	}
}
