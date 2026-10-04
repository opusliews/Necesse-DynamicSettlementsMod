package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.ToolDamageItem;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;
import opusliews.settlement.SettlementChestProtectionSystem;

import java.awt.geom.Line2D;

@ModMethodPatch(
		target = ToolDamageItem.class,
		name = "runLevelDamage",
		arguments = {Level.class, int.class, int.class, int.class, int.class, int.class, PlayerMob.class, Line2D.class, InventoryItem.class, int.class, GNDItemMap.class}
)
public class SettlementChestProtectionToolDamagePatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.This ToolDamageItem tool,
			@Advice.Argument(0) Level level,
			@Advice.Argument(1) int levelX,
			@Advice.Argument(2) int levelY,
			@Advice.Argument(3) int priorityObjectLayerID,
			@Advice.Argument(4) int tileX,
			@Advice.Argument(5) int tileY,
			@Advice.Argument(6) PlayerMob player,
			@Advice.Argument(8) InventoryItem item,
			@Advice.Argument(10) GNDItemMap mapContent
	) {
		boolean ctrlDown = mapContent != null && mapContent.getBoolean(SettlementChestProtectionSystem.ctrlMapKey);
		if (Logging.logEnabled) {
			Logging.logMessage("[ChestProtectionSound] runLevelDamage enter side=" + (level == null ? "null" : level.isClient() ? "client" : level.isServer() ? "server" : "other")
					+ " level=" + (level == null ? "null" : level.getIdentifier())
					+ " tile=" + tileX + "," + tileY + " priorityLayer=" + priorityObjectLayerID
					+ " ctrl=" + ctrlDown + " map=" + (mapContent == null ? "null" : "present"));
		}

		boolean protect = SettlementChestProtectionSystem.shouldProtectToolDamage(tool, level, priorityObjectLayerID, tileX, tileY, item, ctrlDown);
		if (Logging.logEnabled) Logging.logMessage("[ChestProtectionSound] runLevelDamage protectionDecision=" + protect + " side=" + (level == null ? "null" : level.isClient() ? "client" : level.isServer() ? "server" : "other") + " tile=" + tileX + "," + tileY);
		if (!protect) return false;

		if (level.isClient()) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtectionSound] Client protected hit: explicitly requesting vanilla no-damage effects tile=" + tileX + "," + tileY);
			SettlementChestProtectionSystem.playProtectedHitEffects(
					level, priorityObjectLayerID, tileX, tileY, tool.getToolType(item), levelX, levelY);
		} else {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtectionSound] Server protected hit: invoking doToolDamage with damage=0 tile=" + tileX + "," + tileY);
			ServerClient client = player != null && player.isServerClient() ? player.getServerClient() : null;
			level.entityManager.doToolDamage(
					priorityObjectLayerID, tileX, tileY, 0,
					tool.getToolType(item), tool.getToolTier(item, player),
					new ToolDamageItem.ToolDamageItemAttacker(player, item), client,
					true, levelX, levelY);
		}
		if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Replaced protected container hit with zero damage level=" + level.getIdentifier() + " tile=" + tileX + "," + tileY + " ctrl=" + ctrlDown + " client=" + level.isClient());
		return true;
	}

	@Advice.OnMethodExit
	public static void onExit(@Advice.Enter boolean handled, @Advice.Argument(8) InventoryItem item, @Advice.Return(readOnly = false) InventoryItem result) {
		if (handled) result = item;
	}
}
