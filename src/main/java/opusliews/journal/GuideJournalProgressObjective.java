package opusliews.journal;

import necesse.engine.localization.message.GameMessage;
import necesse.engine.network.client.Client;

public class GuideJournalProgressObjective {
	public final GameMessage label;
	private final ProgressTextProvider progressTextProvider;
	private final CompletionProvider completionProvider;

	public GuideJournalProgressObjective(GameMessage label, ProgressTextProvider progressTextProvider, CompletionProvider completionProvider) {
		if (label == null) throw new IllegalArgumentException("Journal progress objective label cannot be null");
		if (progressTextProvider == null) throw new IllegalArgumentException("Journal progress objective progress provider cannot be null");
		if (completionProvider == null) throw new IllegalArgumentException("Journal progress objective completion provider cannot be null");
		this.label = label;
		this.progressTextProvider = progressTextProvider;
		this.completionProvider = completionProvider;
	}

	public String getProgressText(Client client) {
		return progressTextProvider.getProgressText(client);
	}

	public boolean isCompleted(Client client) {
		return completionProvider.isCompleted(client);
	}

	@FunctionalInterface
	public interface ProgressTextProvider {
		String getProgressText(Client client);
	}

	@FunctionalInterface
	public interface CompletionProvider {
		boolean isCompleted(Client client);
	}
}
