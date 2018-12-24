/*
 * Copyright 2010-2018 interactive instruments GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
            github = GitHub.connectUsingPassword(githubLogin, githubPassword)
        }else{
            github = GitHub.connectAnonymously()
        }
        assert githubOrganisationName
        assert githubRepositoryName
        assert autoUpdateCommitMessagePattern
        println "Remaining API requests: ${github.getRateLimit().remaining}"
        repository = github.getRepository(githubOrganisationName+"/"+githubRepositoryName)
        this.autoUpdateCommitMessagePattern = autoUpdateCommitMessagePattern
    }

    static class CommitStatusSubmitter {
        private boolean finalStatusSubmitted = false
        private final GHRepository repository
        private final String sha1
        private final String context
        private final String url

        CommitStatusSubmitter(GHRepository repository, String sha1, String context, String url) {
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

    CommitStatusSubmitter createCommitStatusSubmitter(String context, String url) {
        return new CommitStatusSubmitter(repository, getLastCachedCommit().getSHA1(), context, url)
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
