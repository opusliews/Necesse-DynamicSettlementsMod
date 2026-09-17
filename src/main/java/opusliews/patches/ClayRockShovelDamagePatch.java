package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ObjectRegistry;
import necesse.entity.DamagedObjectEntity;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.ToolDamageItem;
import necesse.inventory.item.toolItem.ToolType;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;
import opusliews.network.PacketClayDiggingSound;

@ModMethodPatch(
		target = ToolDamageItem.class,
		name = "runTileDamage",
		arguments = {
				Level.class,
				int.class,
				int.class,
				int.class,
				int.class,
				int.class,
				PlayerMob.class,
				InventoryItem.class,
				int.class
		}
)
public class ClayRockShovelDamagePatch {
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
			@Advice.Argument(7) InventoryItem item,
			@Advice.Argument(8) int damage
	) {
		if (tool.getToolType(item) != ToolType.SHOVEL || priorityObjectLayerID < 0) {
			return false;
		}

		int clayRockID = ObjectRegistry.getObjectID("clayrock");
		if (level.getObjectID(priorityObjectLayerID, tileX, tileY) != clayRockID) {
			return false;
		}

		DamagedObjectEntity damagedObject =
				level.entityManager.getOrCreateDamagedObjectEntity(tileX, tileY);

		int currentDamage = damagedObject.objectDamage[priorityObjectLayerID];
		int objectHealth = ObjectRegistry.getObject(clayRockID).objectHealth;

		boolean willBreak = currentDamage + damage >= objectHealth;

		Logging.logMessage("DAMAGE:"+currentDamage+":"+damage+":"+ objectHealth+":"+willBreak);
		if (level.isServer()) {
			level.getServer().network.sendToClientsWithTile(
					new PacketClayDiggingSound(tileX, tileY, willBreak), level, tileX, tileY);

			ServerClient client =
					player != null && player.isServerClient()
							? player.getServerClient()
							: null;

			damagedObject.doObjectDamage(
					priorityObjectLayerID,
					damage,
					tool.getToolTier(item, player),
					new ToolDamageItem.ToolDamageItemAttacker(player, item),
					client,
					true,
					levelX,
					levelY
			);
		}

		return true;
	}
}