package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.PacketReader;
import necesse.inventory.container.settlement.actions.zones.CreateNewWorkZoneAction;
import net.bytebuddy.asm.Advice;
import opusliews.progression.GuideProgressionSystem;
import opusliews.zones.SettlementIndependentZoneActionSupport;

@ModMethodPatch(target = CreateNewWorkZoneAction.class, name = "executePacket", arguments = {PacketReader.class})
public class CreateWorkZoneIndependentPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.This CreateNewWorkZoneAction action,
			@Advice.Argument(0) PacketReader reader,
			@Advice.Local("guideZoneID") int guideZoneID,
			@Advice.Local("guideZoneUniqueID") int guideZoneUniqueID
	) {
		PacketReader copy = new PacketReader(reader);
		guideZoneID = copy.getNextInt();
		guideZoneUniqueID = copy.getNextInt();
		return SettlementIndependentZoneActionSupport.handleCreateWorkZone(action.container, reader);
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This CreateNewWorkZoneAction action,
			@Advice.Local("guideZoneID") int guideZoneID,
			@Advice.Local("guideZoneUniqueID") int guideZoneUniqueID
	) {
		GuideProgressionSystem.onWorkZoneCreated(action.container, guideZoneID, guideZoneUniqueID);
	}
}
