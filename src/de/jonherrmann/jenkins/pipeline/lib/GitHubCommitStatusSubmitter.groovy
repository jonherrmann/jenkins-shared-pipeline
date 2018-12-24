package de.jonherrmann.jenkins.pipeline.lib

import org.kohsuke.github.GHCommitState
import org.kohsuke.github.GHRepository

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
class GitHubCommitStatusSubmitter {
	private boolean finalStatusSubmitted = false
	private final GHRepository repository
	private final String sha1
	private final String context
	private final String url

	GitHubCommitStatusSubmitter(GHRepository repository, String sha1, String context, String url) {
		this.repository = repository
		this.sha1 = sha1
		this.context = context
		this.url = url
		repository.createCommitStatus(sha1, GHCommitState.PENDING, url, "Building...", context)
	}

	void updatePending(final String description) {
		repository.createCommitStatus(sha1, GHCommitState.PENDING, url, description, context)
	}

	void submitSuccess(final String description) {
		if(!finalStatusSubmitted) {
			finalStatusSubmitted = true
			repository.createCommitStatus(sha1, GHCommitState.SUCCESS, url, description, context)
		}
	}

	void submitFailure(final String description) {
		finalStatusSubmitted = true
		repository.createCommitStatus(sha1, GHCommitState.FAILURE, url, description, context)
	}

	void destroy() {
		if(!finalStatusSubmitted) {
			repository.createCommitStatus(sha1, GHCommitState.ERROR, url, "An unknown error occurred during the build process", context)
		}
	}
}
