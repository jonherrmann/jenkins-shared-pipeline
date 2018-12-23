package de.jonherrmann.jenkins.pipeline.lib

class Git {

    static def getLastCommitMessage() {
        def commitMessage = sh(
                script: 'git log -1 --pretty=%B',
                returnStdout: true
        ).trim()
        assert commitMessage != null
        commitMessage
    }

    static def noNewWorkingVersion() {
        return !getLastCommitMessage().startsWith(':arrow_up: New working version')
    }
}
