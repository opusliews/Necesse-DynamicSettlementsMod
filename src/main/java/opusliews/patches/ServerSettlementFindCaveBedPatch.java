package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.LevelSettler;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementBed;
import necesse.level.maps.levelData.settlementData.settler.SettlerMob;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementCaveBed;
import opusliews.multilevelsettlement.SettlementCaveBedSystem;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementLevelType;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;

@ModMethodPatch(target = ServerSettlementData.class, name = "findBedForSettler", arguments = {LevelSettler.class})
public class ServerSettlementFindCaveBedPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.Argument(0) LevelSettler settler, @Advice.Return(readOnly = false) SettlementBed result) {
		SettlerMob mob = settler == null ? null : settler.getMob();
		if (mob == null || mob.getMob().getLevel() == null) return;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settler.data);
		if (domain == null || domain.getLevelType(mob.getMob().getLevel().getIdentifier()) != SettlementLevelType.CAVE) return;
		SettlementCaveBed caveBed = SettlementCaveBedSystem.findBestCaveBedForSettler(settler);
		if (caveBed != null) result = caveBed;
	}
}
