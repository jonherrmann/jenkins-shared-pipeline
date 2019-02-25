#!groovy
import com.cloudbees.groovy.cps.NonCPS
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
        releaseVersion = new SemVersionBuilder().create(env.RELEASE_VERSION)
    }
    if(env.DEVELOPMENT_VERSION) {
        devVersion = new SemVersionBuilder().create(env.DEVELOPMENT_VERSION)
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
            env.AF_INT_ENV = buildType.toLowerCase()
        }
        env.FORCE_VERSION_TYPE = 'SNAPSHOT'
    }

    final GitHubFacade gitHubConnector
    withCredentials([usernamePassword(credentialsId: pipelineParams.gitCredentialsId, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
        gitHubConnector = new GitHubFacade(env.USERNAME, env.PASSWORD, checkOutBranches, pipelineParams.githubOrganisation, namingConvention.projectName())
    }

    final GitHubCommitStatusSubmitter statusSubmitter = gitHubConnector.createCommitStatusSubmitter(
            pipelineParams.commitStatusContext, pipelineParams.commitStatusUrl ? pipelineParams.commitStatusUrl : env.BUILD_URL)

    final String gitRepoUrl = 'https://github.com/' + pipelineParams.githubOrganisation + '/' + namingConvention.projectName()

    def artifactPattern
    def artifactLocalVersion

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
                    if (buildType == 'RELEASE' || pipelineParams.forceRefreshDependencies) {
                        sh './gradlew --refresh-dependencies init'
                    }else{
                        sh './gradlew init'
                    }
                    sh './gradlew clean'
                    statusSubmitter.updatePending("Building...")
                    try {
                        if (buildType == 'RELEASE' || pipelineParams.forceRefreshDependencies) {
                            sh './gradlew --refresh-dependencies build'
                        }else{
                            sh './gradlew assemble'
                        }
                    }catch(e) {
                        statusSubmitter.submitFailure("Build failed")
                    }
                    sh './gradlew -q dependencies > build-dependencies.txt'
                    archiveArtifacts 'build-dependencies.txt'
                }

                stage('test') {
                    statusSubmitter.updatePending("Testing...")
                    try {
                        sh './gradlew build check'
                    }catch(e) {
                        statusSubmitter.submitFailure("Testing failed")
                    } finally {
                        if(!pipelineParams.skipArchivingTests) {
                            // support old and new junit directory
                            def resultPattern = "**/build/test-results/test/**.xml, **/build/test-results/junit-platform/**.xml"
                            junit resultPattern
                            archiveArtifacts '**/build/test-results/**'
                        }
                    }
                }

                stage('archive') {
                    final String localVersionStr = sh(returnStdout: true, script: './gradlew properties -q | grep "version:" | awk \'NR==1 {print $2}\'').trim()
                    artifactLocalVersion = new SemVersionBuilder().create(localVersionStr)
                    artifactPattern = "**/build/libs/*.war, **/build/libs/*${localVersionStr}.war, **/build/libs/*${localVersionStr}.jar, **/build/libs/*${localVersionStr}-plugin.jar, **/build/libs/*.xar"
                    archiveArtifacts artifactPattern
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
                        if (env.DEPLOYMENT == 'ARTIFACTORY' || env.DEPLOYMENT == 'GITHUB') {
                            if (!gitHubConnector.wasLastCommitInitiatedByUpdate() || env.DEPLOYMENT == 'FORCE-REDEPLOYMENT') {
                                sh './gradlew uploadArchives'
                                statusSubmitter.submitSuccess("Deployed")
                            } else {
                                echo 'Snapshot deployment skipped due to the previous automatic commit.'
                                statusSubmitter.submitSuccess("dry run ok")
                            }
                        } else {
                            echo 'Snapshot deployment skipped.'
                            statusSubmitter.submitSuccess("dry run ok")
                        }
                    }
                } else {
                    stage('release') {
                        if (env.DEPLOYMENT == 'ARTIFACTORY' || env.DEPLOYMENT == 'GITHUB') {
                            if (!gitHubConnector.wasLastCommitInitiatedByUpdate() || env.DEPLOYMENT == 'FORCE-REDEPLOYMENT') {
                                sh './gradlew release'
                                statusSubmitter.submitSuccess("Released")
                            } else {
                                echo 'Release skipped due to the previous automatic commit.'
                                statusSubmitter.submitSuccess(null)
                            }
                        } else {
                            // DRY-RUN
                            echo 'Release skipped.'
                            if(!gitHubConnector.wasLastCommitInitiatedByUpdate()) {
                                def latestVersion = gitHubConnector.getLastVersionOrInitialVersion()
                                if(latestVersion.equals(artifactLocalVersion)) {
                                    echo "There is already a release '$latestVersion' with the same version number."
                                    statusSubmitter.submitFailure("check version")
                                }else if(latestVersion.isHigherThan(artifactLocalVersion)) {
                                    echo "There is already a release '$latestVersion' with a higher version number."
                                    statusSubmitter.submitFailure("check version")
                                }else{
                                    echo 'Versions OK'
                                    statusSubmitter.submitSuccess("dry run ok")
                                }
                            }else{
                                echo 'Version check skipped due to the previous automatic commit.'
                                statusSubmitter.submitSuccess("dry run ok")
                            }
                        }
                    }
                }

                stage('publish on GitHub') {
                    if (env.DEPLOYMENT == 'GITHUB') {
                        def path = "${env.JENKINS_HOME}/jobs/${jobDirectory(env.JOB_NAME)}/builds/${env.BUILD_NUMBER}/archive"
                        def files = new FileNameFinder().getFileNames(path, artifactPattern)

                        gitHubConnector.createDraftRelease(artifactLocalVersion, files)
                        echo "Draft release version ${artifactLocalVersion} at " +
                                "https://github.com/${pipelineParams.githubOrganisation}/${namingConvention.projectName()}/releases/${artifactLocalVersion}"
                    }else {
                        echo 'Publishing skipped.'
                    }
                }
            } finally {
                statusSubmitter.destroy()
            }
        }
    }
}

String jobDirectory(String jobName) {
    return jobName.replace('/','/jobs/')
}
