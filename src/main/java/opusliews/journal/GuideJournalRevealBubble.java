package opusliews.journal;

import java.util.HashSet;
import java.util.Set;
import necesse.engine.localization.Localization;
import necesse.engine.network.client.Client;
import necesse.level.maps.hudManager.floatText.ChatBubbleText;
import opusliews.logging.Logging;

public final class GuideJournalRevealBubble {
	private static Client trackedClient;
	private static final Set<String> knownVisibleSections = new HashSet<>();
	private static boolean initialized;

	private GuideJournalRevealBubble() {
	}

	public static void frameTick(Client client) {
		if (client == null) {
			reset();
			return;
		}

		if (trackedClient != client) {
			trackedClient = client;
			knownVisibleSections.clear();
			initialized = false;
		}

		if (client.getPlayer() == null || client.getLevel() == null) return;

		boolean revealedNewSection = false;

		for (GuideJournalRegistry.SectionEntry section : GuideJournalRegistry.getSections("dynamicsettlements")) {
			if (!section.isVisible(client)) continue;

			if (!initialized) {
				knownVisibleSections.add(section.challengeStringID);
				continue;
			}

			if (knownVisibleSections.add(section.challengeStringID)) {
				revealedNewSection = true;
				Logging.logMessage(
						"Journal entry revealed: category=" + section.categoryStringID
								+ ", section=" + section.sectionStringID
				);
			}
		}

		if (!initialized) {
			initialized = true;
			Logging.logMessage("Journal reveal bubble initialized with " + knownVisibleSections.size() + " already-visible entries");
			return;
		}

		if (revealedNewSection) {
			client.getLevel().hudManager.addElement(new ChatBubbleText(
					client.getPlayer(),
					Localization.translate("ui", "newjournalentrybubble")
			));
		}
	}

	private static void reset() {
		if (trackedClient == null && !initialized && knownVisibleSections.isEmpty()) return;

		trackedClient = null;
		knownVisibleSections.clear();
		initialized = false;
	}
}
