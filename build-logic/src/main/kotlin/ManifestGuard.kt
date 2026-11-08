import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** Checks a merged AndroidManifest.xml against the app's privacy non-negotiables. */
object ManifestGuard {
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    private val bannedPermissions = setOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.MANAGE_EXTERNAL_STORAGE",
    )
    private val bannedServicePermissions = setOf(
        "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
    )
    private val permissionTags = listOf("uses-permission", "uses-permission-sdk-23")
    private val componentTags = listOf("activity", "activity-alias", "service", "receiver", "provider")

    fun violations(manifestXml: String, allowedExported: Set<String>): List<String> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(manifestXml)))
        val found = mutableListOf<String>()

        for (tag in permissionTags) {
            for (el in doc.elements(tag)) {
                val name = el.androidAttr("name")
                if (name in bannedPermissions) found += "<$tag> $name is not allowed"
            }
        }
        for (tag in componentTags) {
            for (el in doc.elements(tag)) {
                val name = el.androidAttr("name")
                val permission = el.androidAttr("permission")
                if (tag == "service" && permission in bannedServicePermissions) {
                    found += "<service> $name uses $permission, which is not allowed"
                }
                if (el.androidAttr("exported") == "true" && name !in allowedExported) {
                    found += "<$tag> $name is exported but not in the allowed list"
                }
            }
        }
        return found
    }

    private fun org.w3c.dom.Document.elements(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.androidAttr(name: String): String = getAttributeNS(ANDROID_NS, name)
}
