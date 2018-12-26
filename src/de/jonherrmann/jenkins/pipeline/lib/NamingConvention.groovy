package de.jonherrmann.jenkins.pipeline.lib

import com.cloudbees.groovy.cps.NonCPS

class NamingConvention implements Serializable {

    final String jobName
    final String prefix

    NamingConvention(String prefix, String jobName) {
        this.prefix = prefix
        this.jobName = jobName
    }

    @NonCPS
    def pipelineRegMatcher() {
        def JOB_REGEX = /($prefix-.*)-([A-Z]*)-pipeline$/
        def m = (jobName =~ JOB_REGEX)
        return m
    }

    @NonCPS
    def projectName() {
        def pipelineRegMatcher = pipelineRegMatcher()
        assert pipelineRegMatcher != null
        pipelineRegMatcher[0][1]
    }

    @NonCPS
    def buildType() {
        def pipelineRegMatcher = pipelineRegMatcher()
        assert pipelineRegMatcher != null
        pipelineRegMatcher[0][2]
    }

}
