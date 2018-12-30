package de.jonherrmann.jenkins.pipeline.lib

import com.cloudbees.groovy.cps.NonCPS
import org.kohsuke.github.GHCommitQueryBuilder
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
class GitHubRepository implements Serializable {
    private transient GHRepository r
    private final String githubLogin
    private final String githubPassword
    private final String githubOrganisationName
    private final String githubRepositoryName
    final String branch

    GitHubRepository(String githubLogin, String githubPassword,
                     String branch,
                     String githubOrganisationName, String githubRepositoryName) {
        this.githubLogin = githubLogin
        this.githubPassword = githubPassword
        this.branch = branch ? branch : 'master'
        this.githubOrganisationName = githubOrganisationName
        this.githubRepositoryName = githubRepositoryName

        assert githubOrganisationName
        assert githubRepositoryName
    }

    @NonCPS
    GHRepository getRepository() {
        if(this.r == null) {
            final GitHub github
            if(githubLogin && githubPassword) {
                github = GitHub.connectUsingPassword(githubLogin, githubPassword)
            }else{
                github = GitHub.connectAnonymously()
            }
            // echo "Remaining API requests: ${github.getRateLimit().remaining}"
            this.r = github.getRepository(githubOrganisationName+"/"+githubRepositoryName)
        }
        return this.r
    }

    GHCommitQueryBuilder queryCommits() {
        return getRepository().queryCommits().from(branch)
    }
}
