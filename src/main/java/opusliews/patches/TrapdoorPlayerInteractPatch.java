package opusliews.patches;

import necesse.engine.GameEvents;
import necesse.engine.events.players.ObjectInteractEvent;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.packet.PacketObjectInteract;
import necesse.entity.mobs.PlayerMob;
import necesse.level.gameObject.ObjectHoverHitbox;
import necesse.level.maps.LevelObject;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;
import opusliews.deephole.DeepHoleSystem;
import opusliews.object.TrapdoorObject;
import opusliews.trapdoor.TrapdoorSystem;

@ModMethodPatch(target = PlayerMob.class, name = "runClientInteract", arguments = {int.class, int.class, boolean.class})
public class TrapdoorPlayerInteractPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.This PlayerMob player,
			@Advice.Argument(0) int levelX,
			@Advice.Argument(1) int levelY,
			@Advice.Argument(2) boolean onlyItemInteract
	) {
		if (player.getLevel() == null) return false;

		if (DeepHoleSystem.tryClientLadderInteract(player, levelX, levelY)) return true;
		if (DeepHoleSystem.tryClientInteract(player, levelX, levelY)) return true;
		if (DeepHoleSystem.isTransitioning(player)) return true;

		boolean hidden = TrapdoorSystem.isHidden(player);
		if (onlyItemInteract) return hidden;

		int tileX = player.getTileX();
		int tileY = player.getTileY();
		if (!(player.getLevel().getObject(tileX, tileY) instanceof TrapdoorObject)) return hidden;

		LevelObject trapdoor = player.getLevel().getLevelObject(tileX, tileY);
		if (!trapdoor.isInInteractRange(player) || !trapdoor.canInteract(player)) return hidden;

		boolean mouseOverTrapdoor = false;
		for (ObjectHoverHitbox box : trapdoor.getHoverHitboxes()) {
			if (box.contains(levelX, levelY)) {
				mouseOverTrapdoor = true;
				break;
			}
		}

		if (!mouseOverTrapdoor) return hidden;

		if (Logging.logEnabled) {
			Logging.logMessage(
					"[TrapdoorInteractPatch] Direct trapdoor interaction at "
							+ tileX + "," + tileY
							+ " click=" + levelX + "," + levelY
			);
		}

		ObjectInteractEvent event = new ObjectInteractEvent(player.getLevel(), tileX, tileY, player);
		GameEvents.triggerEvent(event);
		if (event.isPrevented()) {
			if (Logging.logEnabled) Logging.logMessage("[TrapdoorInteractPatch] ObjectInteractEvent was prevented");
			return false;
		}

		trapdoor.interact(player);
		player.getLevel().getClient().network.sendPacket(
				new PacketObjectInteract(
						player.getLevel(),
						player.getLevel().getClient().getSlot(),
						tileX,
						tileY
				)
		);

		if (Logging.logEnabled) Logging.logMessage("[TrapdoorInteractPatch] Interaction handled and packet sent");
		return true;
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Enter boolean handled,
			@Advice.Return(readOnly = false) boolean result
	) {
		if (handled) result = true;
	}
}
