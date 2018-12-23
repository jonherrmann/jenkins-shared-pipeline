#!groovy
import de.jonherrmann.jenkins.pipeline.lib.*
import hudson.AbortException

def call(body) {
    def pipelineParams= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

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

    String gitRepoUrl = 'https://github.com/' + pipelineParams.githubOrganisation + '/' + namingConvention.projectName()

    timestamps {
        node() {
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
                sh './gradlew assemble'
            }

            stage('test') {
                try {
                    sh './gradlew build test'
                } finally {
                    junit '**/build/test-results/test/**.xml'
                    archiveArtifacts '**/build/test-results/test/**'
                }
            }


            stage('SonarQube analysis') {
                if(pipelineParams.runSonar == true && env.DEPLOYMENT != 'DRY-RUN' && (buildType == 'RELEASE' || buildType == 'SNAPSHOT')) {
                    withSonarQubeEnv('SonarCloud') {
                        sh './gradlew --info sonarqube'
                    }
                }
            }


            stage('archive upload ' + '[' + buildType + ']') {
                if (buildType != 'RELEASE') {
                    if (env.DEPLOYMENT != 'DRY-RUN') {
                        if (new Git().noNewWorkingVersion() || env.DEPLOYMENT == 'FORCE') {
                            sh './gradlew uploadArchives'
                        } else {
                            echo 'Snapshot deployment was skipped due to the previous automatic commit.'
                        }
                    } else {
                        echo 'Snapshot deployment was skipped due to DRY-RUN mode.'
                    }
                } else {
                    echo 'Snapshot deployment was skipped due to the release.'
                }
            }

            stage('release') {
                if (buildType == 'RELEASE') {
                    if (env.DEPLOYMENT != 'DRY-RUN') {
                        if (new Git().noNewWorkingVersion() || env.DEPLOYMENT == 'FORCE') {
                            sh './gradlew release'
                        } else {
                            echo 'The release was skipped due to the previous automatic commit.'
                        }
                    } else {
                        echo 'Release was skipped due to DRY-RUN mode.'
                    }
                } else {
                    echo 'Release skipped.'
                }
            }
        }
    }
}
