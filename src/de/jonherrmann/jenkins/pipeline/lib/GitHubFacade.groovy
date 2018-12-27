package de.jonherrmann.jenkins.pipeline.lib

import com.cloudbees.groovy.cps.NonCPS
import hudson.AbortException
import org.kohsuke.github.*

import java.util.regex.Pattern

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
class GitHubFacade implements Serializable {

    private final GitHubRepository rw
    private final Pattern autoUpdateCommitMessagePattern
    private transient GHCommit lastCommit
    private final SemVersionBuilder versionBuilder

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

        assert autoUpdateCommitMessagePattern

        this.autoUpdateCommitMessagePattern = autoUpdateCommitMessagePattern
        this.rw = new GitHubRepository(githubLogin, githubPassword, githubOrganisationName, githubRepositoryName)
    }

    @NonCPS
    GitHubCommitStatusSubmitter createCommitStatusSubmitter(String context, String url) {
        final GitHubCommitStatusSubmitter s =
                new GitHubCommitStatusSubmitter(rw, getLastCachedCommit().getSHA1(), context, url)
        s.updatePending("Initializing...")
        return s
    }

    @NonCPS
    GHCommit getLastCachedCommit() {
        if(lastCommit == null) {
            lastCommit = rw.repository?.listCommits()?._iterator(1)?.next()
        }
        return lastCommit
    }

    /**
     * Return the last commit message
     *
     * @return
     */
    @NonCPS
    String getLastCommitMessage() {
        getLastCachedCommit()?.commitShortInfo?.message
    }

    /**
     * Check if the last commit was initiated by an automated job
     *
     * @return true if auto commit message has been found
     */
    @NonCPS
    boolean wasLastCommitInitiatedByUpdate() {
        autoUpdateCommitMessagePattern.matcher(getLastCommitMessage()).matches()
    }

    /**
     * Get the last tagged version from the tags
     *
     * @return semantic version or an initial version placeholder
     */
    @NonCPS
    SemVersion getLastTaggedVersionOrInitialVersion() {
        final String latestTagName = rw.repository?.listTags()?._iterator(1)?.next()?.name
        if(latestTagName != null) {
            try {
                return versionBuilder.create(latestTagName)
            }catch(AbortException | IllegalArgumentException e) { }
        }
        return versionBuilder.createInitialVersion()
    }

    /**
     * Get the last release version
     *
     * @return semantic version or an initial version placeholder
     */
    @NonCPS
    SemVersion getLastReleaseOrInitialVersion() {
        final String latestReleaseName = rw.repository?.latestRelease?.tagName
        if(latestReleaseName != null) {
            try {
                return versionBuilder.create(latestReleaseName)
            }catch(AbortException | IllegalArgumentException e) { }
        }
        return versionBuilder.createInitialVersion()
    }

    @NonCPS
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
    @NonCPS
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

        final GHRelease release = rw.repository.createRelease(versionStr)
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
    @NonCPS
    void attachDraftRelease(final SemVersion version, String...files) {
        assert version != null
        final String versionStr = version.toStringWithoutLabel()
        final GHRelease release = rw.repository.getReleaseByTagName(versionStr)
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
