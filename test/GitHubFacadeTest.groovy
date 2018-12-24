package de.jonherrmann.jenkins.pipeline.lib

import org.junit.Test

class GitHubFacadeTest {

    @Test
    void testInit() {
        final GitHubFacade facade = new GitHubFacade(
                null, null,
                "jonherrmann", "jenkins-shared-pipeline")
        assert facade.getLastCommitMessage()
        assert !facade.wasLastCommitInitiatedByUpdate()
        assert SemVersion.INITIAL_VERSION.toString() == facade.getLastReleaseOrInitialVersion().toString()
    }

    /*
    @Test
    void testCommitStatus() {
        final GitHubFacade facade = new GitHubFacade(
                null, null,
                "jonherrmann", "jenkins-shared-pipeline")
        GitHubFacade.CommitStatusSubmitter submitter = facade.createCommitStatusSubmitter(
                "Test", "https://github.com/jonherrmann/jenkins-shared-pipeline")
        assert submitter != null

        submitter.updatePending("Testing...")
        submitter.submitSuccess("OK")
        submitter.destroy()
    }

    @Test
    void testCommitStatus() {
        final GitHubFacade facade = new GitHubFacade(
                null, null,
                "jonherrmann", "jenkins-shared-pipeline")

        facade.createDraftRelease(new SemVersion("0.0.4"), "/Users/herrmann/Projects/LoD-Prüftool/src/etf-bsxtd/build/libs/etf-bsxtd-1.0.6.jar")
    }
    */
}
