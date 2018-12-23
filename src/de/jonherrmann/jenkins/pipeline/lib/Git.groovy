package de.jonherrmann.jenkins.pipeline.lib

def getLastCommitMessage() {
    def commitMessage = sh(
            script: 'git log -1 --pretty=%B',
            returnStdout: true
    ).trim()
    assert commitMessage != null
    commitMessage
}

def noNewWorkingVersion() {
    return !getLastCommitMessage().startsWith(':arrow_up: New working version')
}
