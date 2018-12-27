package de.jonherrmann.jenkins.pipeline.lib

import com.cloudbees.groovy.cps.NonCPS
import hudson.AbortException

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
class SemVersionBuilder implements Serializable {

    @NonCPS
    SemVersion create(final String versionStr) {
        /**
         * MAJRO.MINOR.BUGFIX.BUILD_VERSION-LABEL
         * where BUGFIX is optional,
         * BUILD_VERSION is ignored
         * and LABEL is optional
         */
        final Matcher versionMatcher = Pattern.compile("(\\d+)\\.(\\d+)(\\.\\d+)?(\\.\\d+)?(-[A-Z|a-z]+(\\.\\d+)?)?").matcher(versionStr)
        if (!versionStr) throw new IllegalArgumentException("Version number not set")
        if (!versionMatcher.matches()) {
            throw new AbortException(
                    "Invalid version '$versionStr' format. The expected format is MAJOR.MINOR.PATCH .")
        }
        try {
            int major = Integer.parseInt(versionMatcher.group(1))
            int minor = Integer.parseInt(versionMatcher.group(2))
            final String bugfixStr = versionMatcher.group(3)
            final int patch
            if (bugfixStr) {
                patch = Integer.parseInt(bugfixStr.substring(1))
            }else{
                patch = 0
            }
        } catch (final NumberFormatException e) {
            throw new AbortException(
                    "Invalid version '$versionStr' format. The expected format is MAJOR.MINOR.PATCH.")
        }

        final String label
        if (versionMatcher.group(5) != null) {
            final String labelStr = versionMatcher.group(5)
            label = labelStr == "-RELEASE" ? "" : labelStr?.substring(1)
        }else{
            label = ""
        }
        return new SemVersion(major,minor,patch,label)
    }

    SemVersion createInitialVersion() {
        return new SemVersion(0,0,1,"SNAPSHOT")
    }
}
