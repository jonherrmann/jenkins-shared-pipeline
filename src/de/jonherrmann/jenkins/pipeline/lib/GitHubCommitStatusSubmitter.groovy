package de.jonherrmann.jenkins.pipeline.lib

import com.cloudbees.groovy.cps.NonCPS
import org.kohsuke.github.GHCommitState
import org.kohsuke.github.GHCommitStatus

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
class GitHubCommitStatusSubmitter implements Serializable {
	private boolean finalStatusSubmitted = false
	private final GitHubRepository rw
	private final String sha1
	private final String context
	private final String url
	private String lastPendingStatus

	GitHubCommitStatusSubmitter(GitHubRepository rw, String sha1, String context, String url) {
		this.rw = rw
		this.sha1 = sha1
		this.context = context
		this.url = url
	}

	@NonCPS
	void updatePending(final String description) {
		lastPendingStatus = description
		rw.repository.createCommitStatus(sha1, GHCommitState.PENDING, url, description, context)
	}

	@NonCPS
	void submitSuccess(final String description) {
		if(!finalStatusSubmitted) {
			finalStatusSubmitted = true
			rw.repository.createCommitStatus(sha1, GHCommitState.SUCCESS, url, description, context)
		}
	}

	@NonCPS
	void submitFailure(final String description) {
		finalStatusSubmitted = true
		rw.repository.createCommitStatus(sha1, GHCommitState.FAILURE, url, description, context)
	}

	@NonCPS
	void destroy() {
		if(!finalStatusSubmitted) {
			rw.repository.createCommitStatus(sha1, GHCommitState.ERROR, url,
					"An internal error occurred during the build process. " +
							"Last status: ${lastPendingStatus}", context)
		}
	}
}
