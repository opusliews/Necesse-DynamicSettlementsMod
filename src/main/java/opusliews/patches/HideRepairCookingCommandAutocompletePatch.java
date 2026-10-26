package opusliews.patches;

import java.util.Iterator;
import java.util.List;
import necesse.engine.commands.ChatCommand;
import necesse.engine.commands.CommandsManager;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import net.bytebuddy.asm.Advice;
import opusliews.commands.RepairCookingGuideCommand;

/**
 * Necesse autocomplete looks at getCommands(), ignoring shouldBeListed().
 * This method returns a copy, so removing our hidden command here has no
 * effect on the registered server command used for execution.
 */
@ModMethodPatch(target = CommandsManager.class, name = "getCommands", arguments = {})
public final class HideRepairCookingCommandAutocompletePatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.Return List commands) {
		if (commands == null) return;
		Iterator iterator = commands.iterator();
		while (iterator.hasNext()) {
			ChatCommand command = (ChatCommand)iterator.next();
			if (RepairCookingGuideCommand.commandName.equalsIgnoreCase(command.name)) iterator.remove();
		}
	}
}
