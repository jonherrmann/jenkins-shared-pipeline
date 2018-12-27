package de.jonherrmann.jenkins.pipeline.lib

import com.cloudbees.groovy.cps.NonCPS
import hudson.AbortException
import java.util.regex.Matcher
import java.util.regex.Pattern

enum VersionLevel {
    MAJOR, MINOR, PATCH, LABEL
}

/**
 * @author Jon Herrmann
 */
class SemVersion implements Serializable {

    // final static SemVersion INITIAL_VERSION = new SemVersion(0,0,1,"SNAPSHOT")

    /**
     * MAJRO.MINOR.BUGFIX.BUILD_VERSION-LABEL
     * where BUGFIX is optional,
     * BUILD_VERSION is ignored
     * and LABEL is optional
     */

    final int major
    final int minor
    final int patch
    final String label

    SemVersion(String versionStr) {
        final Matcher versionMatcher = Pattern.compile("(\\d+)\\.(\\d+)(\\.\\d+)?(\\.\\d+)?(-[A-Z|a-z]+(\\.\\d+)?)?").matcher(versionStr)
        if (!versionStr) throw new IllegalArgumentException("Version number not set")
        if (!versionMatcher.matches()) {
            throw new AbortException(
                    "Invalid version '$versionStr' format. The expected format is MAJOR.MINOR.PATCH .")
        }
        try {
            major = Integer.parseInt(versionMatcher.group(1))
            minor = Integer.parseInt(versionMatcher.group(2))
            final String bugfixStr = versionMatcher.group(3)
            if (bugfixStr) {
                patch = Integer.parseInt(bugfixStr.substring(1))
            }else{
                patch = 0
            }
        } catch (final NumberFormatException e) {
            throw new AbortException(
                    "Invalid version '$versionStr' format. The expected format is MAJOR.MINOR.PATCH.")
        }

        if (versionMatcher.group(5) != null) {
            final String labelStr = versionMatcher.group(5)
            label = labelStr == "-RELEASE" ? "" : labelStr?.substring(1)
        }else{
            label = ""
        }
        check()
    }

    SemVersion(int major, int minor, int patch, String label) {
        check()
        this.major = major
        this.minor = minor
        this.patch = patch
        this.label = label == null || label == "RELEASE" ? "" : label
    }

    void check() {
        if (major < 0) throw new IllegalArgumentException("Major version number can not have a value less than 0.")
        if (minor < 0) throw new IllegalArgumentException("Minor version number can not have a value less than 0.")
        if (patch < 0) throw new IllegalArgumentException("Patch version number can not have a value less than 0.")
    }

    @NonCPS
    SemVersion bump(VersionLevel patchLevel) {
        switch (patchLevel) {
            case VersionLevel.MAJOR:
                return new SemVersion(major + 1, 0, 0, label)
                break
            case VersionLevel.MINOR:
                return new SemVersion(major, minor + 1, 0, label)
                break
            case VersionLevel.PATCH:
                return new SemVersion(major, minor, patch + 1, label)
                break
            case VersionLevel.LABEL:
                if("SNAPSHOT"==label) {
                    return new SemVersion(major, minor, patch, "RELEASE")
                }else{
                    throw new IllegalArgumentException("Version with '$label' can not be bumped")
                }
                break
        }
        throw new IllegalArgumentException("Unknown Version Level")
    }

    @NonCPS
    boolean isHigherThan(version) {
        if (!version) throw new IllegalArgumentException("The parameter 'version' can not be null.")
        return major > version.major || major == version.major &&
                ( minor > version.minor || minor == version.minor &&
                        ( patch > version.patch || patch == version.patch &&
                            label == "" && version.label != ""))
    }

    @NonCPS
    boolean isBackwardsCompatibleToo(version) {
        if (!version) throw new IllegalArgumentException("The parameter 'version' can not be null.")
        return this == version || major == version.major && this.minor <= version.minor
    }

    @NonCPS
    boolean isReleaseVersion() {
        return label==""
    }

    @NonCPS
    String toStringWithoutLabel() {
        return "$major.$minor.$patch"
    }

    @NonCPS
    @Override
    String toString() {
        if (this.label) {
            return "$major.$minor.$patch-$label"
        }
        return toStringWithoutLabel()
    }

    @NonCPS
    int compareTo(SemVersion other) {
        if (major != other.major) {
            return major - other.major
        }
        if (minor != other.minor) {
            return minor - other.minor
        }
        if (patch != other.patch) {
            return patch - other.patch
        }
        return label.toLowerCase() - other.label.toLowerCase()
    }

    @NonCPS
    boolean equals(Object other) {
        return other instanceof SemVersion &&
                compareTo((SemVersion) other) == 0
    }
}
