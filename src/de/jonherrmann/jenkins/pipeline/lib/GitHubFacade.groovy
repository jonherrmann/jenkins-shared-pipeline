package de.jonherrmann.jenkins.pipeline.lib

import hudson.AbortException
import org.kohsuke.github.*

import java.util.regex.Pattern

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
class GitHubFacade {

    private final GHRepository repository
    private final Pattern autoUpdateCommitMessagePattern
    private GHCommit lastCommit

    GitHubFacade(githubLogin, githubPassword, githubOrganisationName, githubRepositoryName) {
        this(githubLogin, githubPassword,
                githubOrganisationName, githubRepositoryName,
                (~':arrow_up: New working version .*'))
    }

    GitHubFacade(
            githubLogin,
            githubPassword,
            githubOrganisationName,
            githubRepositoryName,
            autoUpdateCommitMessagePattern) {
        final GitHub github
        if(githubLogin && githubPassword) {
            github  = new GitHubBuilder().withPassword(githubLogin, githubPassword).build()
        }else{
            github = new GitHubBuilder().build();
        }
        assert githubOrganisationName
        assert githubRepositoryName
        assert autoUpdateCommitMessagePattern
        println "Remaining API requests: ${github.getRateLimit().remaining}"
        repository = github.getRepository(githubOrganisationName+"/"+githubRepositoryName)
        this.autoUpdateCommitMessagePattern = autoUpdateCommitMessagePattern
    }

    GitHubCommitStatusSubmitter createCommitStatusSubmitter(String context, String url) {
        return new GitHubCommitStatusSubmitter(repository, getLastCachedCommit().getSHA1(), context, url)
    }

    GHCommit getLastCachedCommit() {
        if(lastCommit == null) {
            lastCommit = repository?.listCommits()?._iterator(1)?.next()
        }
        return lastCommit
    }

    /**
     * Return the last commit message
     *
     * @return
     */
    String getLastCommitMessage() {
        getLastCachedCommit()?.commitShortInfo?.message
    }

    /**
     * Check if the last commit was initiated by an automated job
     *
     * @return true if auto commit message has been found
     */
    boolean wasLastCommitInitiatedByUpdate() {
        autoUpdateCommitMessagePattern.matcher(getLastCommitMessage()).matches()
    }

    /**
     * Get the last tagged version from the tags
     *
     * @return semantic version or an initial version placeholder
     */
    SemVersion getLastTaggedVersionOrInitialVersion() {
        final String latestTagName = repository?.listTags()?._iterator(1)?.next()?.name
        try {
            return new SemVersion(latestTagName)
        }catch(ign) { }
        return SemVersion.INITIAL_VERSION
    }

    /**
     * Get the last release version
     *
     * @return semantic version or an initial version placeholder
     */
    SemVersion getLastReleaseOrInitialVersion() {
        final String latestReleaseName = repository?.latestRelease?.tagName
        try {
            return new SemVersion(latestReleaseName)
        }catch(ign) { }
        return SemVersion.INITIAL_VERSION
    }

    SemVersion getLastVersionOrInitialVersion() {
        final SemVersion taggedVersion = getLastTaggedVersionOrInitialVersion()
        final SemVersion releaseVersion = getLastReleaseOrInitialVersion()

        if(taggedVersion.isHigherThan(releaseVersion)) {
            return taggedVersion
        }
        return releaseVersion
    }

    /**
     * Draft a new release and upload files
     *
     * @param version to release
     * @param files to upload as key value map, where key is the file path
     *        and the mime type the value
     */
    void createDraftRelease(final SemVersion version, String...files) {
        assert version != null
        final String versionStr = version.toStringWithoutLabel()
        final SemVersion latestVersion = getLastVersionOrInitialVersion()
        if(latestVersion.isHigherThan(version)) {
            throw new AbortException(
                    "There is already a release '$latestVersion' with a higher version number.")
        }
        if(versionStr==latestVersion.toString()) {
            throw new AbortException(
                    "There is already a release '$latestVersion' with the same version number.")
        }

        final GHRelease release = repository.createRelease(versionStr)
                .name(versionStr+" release"+ (!version.isReleaseVersion() ? " candidate" : ""))
                .draft(true)
                .prerelease(!version.isReleaseVersion())
                .body("This release has been automatically created")
                .create()

        if (files) {
            files.each{ file ->
                println "Uploading $file"
                release.uploadAsset(new File(file), "application/zip")
            }
        }else{
            println "No files to upload"
        }
    }

    /**
     * Attach a release to an existing Tag
     *
     * @param version to release
     * @param files to upload as key value map, where key is the file path
     *        and the mime type the value
     */
    void attachDraftRelease(final SemVersion version, String...files) {
        assert version != null
        final String versionStr = version.toStringWithoutLabel()
        final GHRelease release = repository.getReleaseByTagName(versionStr)
        if(release == null) {
            throw new AbortException(
                    "Tag '$latestVersion' not found")
        }
        release.update()
                .name(versionStr+" release"+ (!version.isReleaseVersion() ? " candidate" : ""))
                .draft(true)
                .prerelease(!version.isReleaseVersion())
                .body("This release has been automatically created")
                .update()

        if (files) {
            files.each{ file ->
                println "Uploading $file"
                release.uploadAsset(new File(file), "application/zip")
            }
        }else{
            println "No files to upload"
        }
    }

}
