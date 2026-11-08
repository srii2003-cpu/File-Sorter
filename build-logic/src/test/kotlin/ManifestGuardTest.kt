import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestGuardTest {
    private val launcher = "com.example.MainActivity"
    private val allowed = setOf(launcher)

    private fun manifest(body: String) = """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example">
            $body
            <application>
                <activity android:name="$launcher" android:exported="true"/>
            </application>
        </manifest>
    """.trimIndent()

    private fun manifestWithComponent(component: String) = """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example">
            <application>
                <activity android:name="$launcher" android:exported="true"/>
                $component
            </application>
        </manifest>
    """.trimIndent()

    @Test
    fun cleanManifest_hasNoViolations() {
        val xml = manifest("""<uses-permission android:name="android.permission.READ_MEDIA_IMAGES"/>""")
        assertEquals(emptyList<String>(), ManifestGuard.violations(xml, allowed))
    }

    @Test
    fun internet_isViolation() {
        val v = ManifestGuard.violations(manifest("""<uses-permission android:name="android.permission.INTERNET"/>"""), allowed)
        assertEquals(1, v.size)
        assertTrue(v[0].contains("android.permission.INTERNET"))
    }

    @Test
    fun accessNetworkState_isViolation() {
        val v = ManifestGuard.violations(manifest("""<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>"""), allowed)
        assertEquals(1, v.size)
        assertTrue(v[0].contains("android.permission.ACCESS_NETWORK_STATE"))
    }

    @Test
    fun sdk23Form_isViolation() {
        val v = ManifestGuard.violations(manifest("""<uses-permission-sdk-23 android:name="android.permission.INTERNET"/>"""), allowed)
        assertEquals(1, v.size)
        assertTrue(v[0].contains("uses-permission-sdk-23"))
    }

    @Test
    fun manageExternalStorage_isViolation() {
        val v = ManifestGuard.violations(manifest("""<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"/>"""), allowed)
        assertEquals(1, v.size)
        assertTrue(v[0].contains("android.permission.MANAGE_EXTERNAL_STORAGE"))
    }

    @Test
    fun accessibilityService_isViolation() {
        val xml = manifestWithComponent(
            """<service android:name="com.example.Spy" android:exported="false" android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"/>""",
        )
        val v = ManifestGuard.violations(xml, allowed)
        assertEquals(1, v.size)
        assertTrue(v[0].contains("BIND_ACCESSIBILITY_SERVICE"))
    }

    @Test
    fun notificationListener_isViolation() {
        val xml = manifestWithComponent(
            """<service android:name="com.example.Listener" android:exported="false" android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"/>""",
        )
        assertEquals(1, ManifestGuard.violations(xml, allowed).size)
    }

    @Test
    fun exportedReceiver_notAllowed_isViolation() {
        val xml = manifestWithComponent(
            """<receiver android:name="androidx.profileinstaller.ProfileInstallReceiver" android:exported="true"/>""",
        )
        val v = ManifestGuard.violations(xml, allowed)
        assertEquals(1, v.size)
        assertTrue(v[0].contains("androidx.profileinstaller.ProfileInstallReceiver"))
    }

    @Test
    fun exportedFalse_isFine() {
        val xml = manifestWithComponent("""<provider android:name="androidx.startup.InitializationProvider" android:exported="false"/>""")
        assertEquals(emptyList<String>(), ManifestGuard.violations(xml, allowed))
    }
}
