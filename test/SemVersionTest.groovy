package de.jonherrmann.jenkins.pipeline.lib

import org.junit.BeforeClass
import org.junit.Test

class SemVersionTest {

    /**
     * Use Groovy metaclass programming to add methods to the Jenkins pipeline shared library exposed class.
     * This allows for unit testing of classes that makes use of Jenkins pipeline steps, such as
     * 'sh', 'echo' or e.g. other steps available through the workflow-basic-steps-plugin
     */
    @BeforeClass
    static void setup() {
        SemVersion.metaClass.echo {
            println it
            return it
        }
    }

    @Test
    void testBasicVersion() {
        def version = new SemVersion("1.2.3")

        assert version.major == 1
        assert version.minor == 2
        assert version.patch == 3
        assert version.label == ""
        assert version.toString() == "1.2.3"
    }

    @Test
    void testVersionWithSnapshotLabel() {
        def version = new SemVersion("1.2.3-SNAPSHOT")

        assert version.major == 1
        assert version.minor == 2
        assert version.patch == 3
        assert version.label == "SNAPSHOT"
        assert version.toString() == "1.2.3-SNAPSHOT"
    }

    @Test
    void testVersionWithReleaseLabel() {
        def version = new SemVersion("1.2.3-RELEASE")

        assert version.major == 1
        assert version.minor == 2
        assert version.patch == 3
        assert version.label == ""
        assert version.toString() == "1.2.3"
    }

    @Test
    void testVersionBuildNumber() {
        def version = new SemVersion("1.2.3.4")

        assert version.major == 1
        assert version.minor == 2
        assert version.patch == 3
        assert version.label == ""
        assert version.toString() == "1.2.3"
    }

    @Test
    void testVersionBuildNumberSnapshot() {
        def version = new SemVersion("1.2.3.4-SNAPSHOT")

        assert version.major == 1
        assert version.minor == 2
        assert version.patch == 3
        assert version.label == "SNAPSHOT"
        assert version.toString() == "1.2.3-SNAPSHOT"
    }

    @Test
    void testBumpVersion() {
        def version = new SemVersion("1.2.3.4-SNAPSHOT")

        version = version.bump(VersionLevel.MAJOR)
        assert version.major == 2
        assert version.minor == 0
        assert version.patch == 0
        assert version.label == "SNAPSHOT"
        assert version.toString() == "2.0.0-SNAPSHOT"

        version = version.bump(VersionLevel.MINOR)
        assert version.major == 2
        assert version.minor == 1
        assert version.patch == 0
        assert version.label == "SNAPSHOT"
        assert version.toString() == "2.1.0-SNAPSHOT"

        version = version.bump(VersionLevel.PATCH)
        assert version.major == 2
        assert version.minor == 1
        assert version.patch == 1
        assert version.label == "SNAPSHOT"
        assert version.toString() == "2.1.1-SNAPSHOT"

        version = version.bump(VersionLevel.LABEL)
        assert version.major == 2
        assert version.minor == 1
        assert version.patch == 1
        assert version.label == ""
        assert version.toString() == "2.1.1"
    }

    @Test
    void testIsHigherThan() {
        def version1 = new SemVersion("1.2.3.4-SNAPSHOT")
        def version2 = new SemVersion("1.2.3.4-SNAPSHOT")

        assert !version1.isHigherThan(version2)
        assert !version2.isHigherThan(version1)

        def version3 = new SemVersion("1.2.3.4")

        assert !version2.isHigherThan(version3)
        assert version3.isHigherThan(version2)

        def version4 = new SemVersion("1.2.3")

        assert !version3.isHigherThan(version4)
        assert !version4.isHigherThan(version3)

        def version5 = new SemVersion("1.3.3")

        assert !version4.isHigherThan(version5)
        assert version5.isHigherThan(version4)

    }

    @Test
    void testIsBackwardsCompatibleToo() {
        def version1 = new SemVersion("1.2.3.4-SNAPSHOT")
        def version2 = new SemVersion("1.2.3.4-SNAPSHOT")

        assert version1.isBackwardsCompatibleToo(version2)

        def version3 = new SemVersion("1.2.3.4")

        assert version1.isBackwardsCompatibleToo(version3)

        def version4 = new SemVersion("1.2")

        assert version1.isBackwardsCompatibleToo(version4)

        def version5 = new SemVersion("1.3.3")

        assert version1.isBackwardsCompatibleToo(version5)

    }

}
