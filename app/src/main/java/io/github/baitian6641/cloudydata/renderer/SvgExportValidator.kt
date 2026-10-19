package io.github.baitian6641.cloudydata.renderer

import android.util.Xml
import java.io.StringReader
import org.xmlpull.v1.XmlPullParser

/**
 * Validates a renderer-produced SVG before it leaves the app. Text content is
 * intentionally not treated as a URL-bearing context; only elements and
 * attributes that can load or execute resources are checked.
 */
object SvgExportValidator {
    private val forbiddenElements = setOf(
        "script",
        "foreignobject",
        "style",
        "image",
        "iframe",
        "object",
        "embed",
    )

    private val urlAttributes = setOf(
        "fill",
        "stroke",
        "filter",
        "clip-path",
        "mask",
        "marker",
        "marker-start",
        "marker-mid",
        "marker-end",
        "cursor",
    )

    fun validate(svg: String): String? {
        if (svg.isBlank()) return "The exported SVG is empty."
        return runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(StringReader(svg))
            var rootSeen = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val element = parser.name.lowercase().substringAfter(':')
                    if (!rootSeen) {
                        if (element != "svg") return "The exported document is not an SVG."
                        rootSeen = true
                    }
                    if (element in forbiddenElements) {
                        return "The exported SVG contains active or embedded content."
                    }
                    for (index in 0 until parser.attributeCount) {
                        val name = parser.getAttributeName(index).lowercase().substringAfter(':')
                        val value = parser.getAttributeValue(index).orEmpty().trim()
                        if (name.startsWith("on")) {
                            return "The exported SVG contains an event handler."
                        }
                        if ((name == "href" || name == "src") &&
                            value.isNotEmpty() &&
                            !value.startsWith("#")
                        ) {
                            return "The exported SVG contains an external reference."
                        }
                        if (name in urlAttributes && hasExternalUrl(value)) {
                            return "The exported SVG contains an external URL."
                        }
                        if (name == "style" && hasExternalUrl(value)) {
                            return "The exported SVG contains an external style URL."
                        }
                    }
                }
                event = parser.next()
            }
            if (!rootSeen) "The exported SVG has no root element." else null
        }.getOrElse {
            "The exported SVG is not well-formed XML."
        }
    }

    private fun hasExternalUrl(value: String): Boolean {
        var index = value.indexOf("url(", ignoreCase = true)
        while (index >= 0) {
            var start = index + 4
            while (start < value.length && value[start].isWhitespace()) start++
            if (start < value.length) {
                val quote = value[start]
                if (quote == '\'' || quote == '"') start++
                val end = value.indexOf(quote.takeIf { it == '\'' || it == '"' } ?: ')', start)
                val target = value.substring(start, if (end >= 0) end else value.length).trim()
                if (!target.startsWith("#")) return true
            }
            index = value.indexOf("url(", index + 4, ignoreCase = true)
        }
        return false
    }
}
