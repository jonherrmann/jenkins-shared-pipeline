#!groovy
import de.jonherrmann.jenkins.pipeline.lib.*
import hudson.AbortException

def call(body) {
    def pipelineParams= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    assert pipelineParams.projectPrefix
    assert pipelineParams.gitCredentialsId
    assert pipelineParams.githubOrganisation
    assert pipelineParams.commitStatusContext

    def namingConvention = new NamingConvention(pipelineParams.projectPrefix, env.JOB_NAME)
    def buildType = namingConvention.buildType()

    if(env.RELEASE_VERSION) {
        releaseVersion = new SemVersion(env.DEVELOPMENT_VERSION)
    }
    if(env.DEVELOPMENT_VERSION) {
        devVersion = new SemVersion(env.DEVELOPMENT_VERSION)
        if(!devVersion.label) {
            throw new AbortException(
                    "Invalid development version '$env.DEVELOPMENT_VERSION'")
        }
    }

    if (buildType == 'RELEASE') {
        checkOutBranches = 'master'
        remBranches = [[name: '*/master']]
        env.FORCE_VERSION_TYPE = 'RELEASE'
    } else {
        checkOutBranches = 'next'
        remBranches = [[name: '*/next']]
        if (buildType != 'SNAPSHOT') {
            env.AF_INT_ENV = buildType
            env.FORCE_VERSION_TYPE = 'SNAPSHOT'
        }
    }

    final GitHubFacade gitHubConnector
    withCredentials([usernamePassword(credentialsId: pipelineParams.gitCredentialsId, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
        gitHubConnector = new GitHubFacade(env.USERNAME, env.PASSWORD, pipelineParams.githubOrganisation, namingConvention.projectName())
    }

    final GitHubCommitStatusSubmitter statusSubmitter = gitHubConnector.createCommitStatusSubmitter(
            pipelineParams.commitStatusContext, pipelineParams.commitStatusUrl ? pipelineParams.commitStatusUrl : env.BUILD_URL)

    final String gitRepoUrl = 'https://github.com/' + pipelineParams.githubOrganisation + '/' + namingConvention.projectName()

    timestamps {
        node() {
            try {
                stage('checkout') {
                    checkout([$class                           : 'GitSCM',
                              branches                         : remBranches,
                              doGenerateSubmoduleConfigurations: false,
                              extensions                       : [
                                      [$class: 'LocalBranch', localBranch: checkOutBranches],
                                      [$class: 'CleanBeforeCheckout']
                              ],
                              submoduleCfg                     : [],
                              userRemoteConfigs                : [[credentialsId: pipelineParams.gitCredentialsId, url: gitRepoUrl]]
                    ])
                }

                stage('build') {
                    sh './gradlew init'
                    sh './gradlew clean'
                    statusSubmitter.updatePending("Building...")
                    try {
                        sh './gradlew assemble'
                    }catch(e) {
                        statusSubmitter.submitFailure("Build failed")
                    }
                }

                stage('test') {
                    statusSubmitter.updatePending("Testing...")
                    try {
                        sh './gradlew build test'
                    }catch(e) {
                        statusSubmitter.submitFailure("Testing failed")
                    } finally {
                        junit '**/build/test-results/test/**.xml'
                        archiveArtifacts '**/build/test-results/test/**'
                    }
                }


                stage('analyse with SonarQube') {
                    if (pipelineParams.runSonar == true && env.DEPLOYMENT != 'DRY-RUN' && (buildType == 'RELEASE' || buildType == 'SNAPSHOT')) {
                        withSonarQubeEnv('SonarCloud') {
                            sh './gradlew --info sonarqube'
                        }
                    } else {
                        echo 'SonarQube analysis skipped.'
                    }
                }


                if (buildType != 'RELEASE') {
                    stage('upload archive ' + '[' + buildType + ']') {
                        if (env.DEPLOYMENT != 'DRY-RUN') {
                            if (!gitHubConnector.wasLastCommitInitiatedByUpdate() || env.DEPLOYMENT == 'FORCE') {
                                sh './gradlew uploadArchives'
                                statusSubmitter.submitSuccess("Deployed")
                            } else {
                                echo 'Snapshot deployment was skipped due to the previous automatic commit.'
                            }
                        } else {
                            echo 'Snapshot deployment was skipped due to DRY-RUN mode.'
                        }
                    }
                } else {
                    stage('release') {
                        if (env.DEPLOYMENT != 'DRY-RUN') {
                            if (!gitHubConnector.wasLastCommitInitiatedByUpdate() || env.DEPLOYMENT == 'FORCE') {
                                sh './gradlew release'
                                statusSubmitter.submitSuccess("Released")
                            } else {
                                echo 'The release was skipped due to the previous automatic commit.'
                            }
                        } else {
                            echo 'Release was skipped due to DRY-RUN mode.'
                        }
                    }
                }

                statusSubmitter.submitSuccess(null)

                if (env.DEPLOYMENT != 'DRY-RUN') {
                    stage('publish on GitHub') {
                        def releasedVersion = gitHubConnector.getLastReleaseOrInitialVersion()
                        final String pattern = "**/build/libs/*${releasedVersion}.war **/build/libs/*${releasedVersion}.jar"
                        def files = new FileNameFinder().getFileNames(env.WORKSPACE, pattern)

                        gitHubConnector.createDraftRelease(releasedVersion, files)
                    }
                }
            } finally {
                statusSubmitter.destroy()
            }
        }
    }
}
