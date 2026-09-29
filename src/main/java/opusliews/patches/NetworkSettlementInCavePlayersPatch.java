package opusliews.patches;

import java.awt.Rectangle;
import java.util.Objects;
import java.util.stream.Stream;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.engine.util.LevelIdentifier;
import necesse.entity.mobs.PlayerMob;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.NetworkSettlementData;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementLevelType;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;

@ModMethodPatch(target = NetworkSettlementData.class, name = "streamTeamMembersAndInSettlement", arguments = {})
public class NetworkSettlementInCavePlayersPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This NetworkSettlementData data,
			@Advice.Return(readOnly = false) Stream<ServerClient> result) {
		result = includeCavePlayers(data, result);
	}

	public static Stream<ServerClient> includeCavePlayers(NetworkSettlementData data, Stream<ServerClient> original) {
		Stream<ServerClient> result = original == null ? Stream.empty() : original;
		if (data == null) return result;
		ServerSettlementData settlement = data.getServerData();
		if (settlement == null || settlement.getServer() == null) return result;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null) return result;
		LevelIdentifier caveIdentifier = domain.getLevelIdentifier(SettlementLevelType.CAVE);
		if (caveIdentifier == null) return result;
		Level cave = settlement.getServer().world.levelManager.getLevel(caveIdentifier);
		if (cave == null) return result;
		Rectangle levelRectangle = data.getLevelRectangle();
		Stream<ServerClient> caveClients = cave.entityManager.players.streamInRegionsShape(levelRectangle, 0)
				.filter(player -> levelRectangle.contains(player.getX(), player.getY()))
				.map(PlayerMob::getServerClient)
				.filter(Objects::nonNull);
		return Stream.concat(result, caveClients).distinct();
	}
}
