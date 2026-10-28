package com.lumera.app.livetv

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.parsers.SAXParserFactory

/**
 * Reads an XMLTV guide as it streams in. A provider's guide covers every channel for days and
 * can be a hundred megabytes, so only the channels in the list and the next hours are kept.
 */
object XmlTvParser {

    const val MAX_PER_CHANNEL = 16

    fun parse(
        input: InputStream,
        wanted: Set<String>,
        from: Long,
        until: Long
    ): Map<String, List<Programme>> {
        val out = HashMap<String, MutableList<Programme>>()
        val handler = object : DefaultHandler() {
            private val format = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
            private val bare = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            private var channel: String? = null
            private var start = 0L
            private var stop = 0L
            private var field: StringBuilder? = null
            private var title = ""
            private var desc = ""

            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                when (qName) {
                    "programme" -> {
                        val id = attributes.getValue("channel")
                        channel = null
                        if (id == null || id !in wanted) return
                        start = time(attributes.getValue("start")) ?: return
                        stop = time(attributes.getValue("stop")) ?: (start + 30 * 60_000L)
                        if (stop <= from || start >= until) return
                        if ((out[id]?.size ?: 0) >= MAX_PER_CHANNEL) return
                        channel = id
                        title = ""
                        desc = ""
                    }
                    "title" -> if (channel != null && title.isEmpty()) field = StringBuilder()
                    "desc" -> if (channel != null && desc.isEmpty()) field = StringBuilder()
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                field?.appendRange(ch, start, start + length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (qName) {
                    "title" -> field?.let { title = it.toString().trim(); field = null }
                    "desc" -> field?.let { desc = it.toString().trim().take(400); field = null }
                    "programme" -> {
                        val id = channel ?: return
                        out.getOrPut(id) { ArrayList() } += Programme(start, stop, title.ifBlank { "No information" }, desc)
                        channel = null
                    }
                }
            }

            private fun time(value: String?): Long? {
                val text = value?.trim() ?: return null
                return runCatching {
                    if (text.length > 14) format.parse(text)?.time else bare.parse(text.take(14))?.time
                }.getOrNull()
            }
        }
        val factory = SAXParserFactory.newInstance()
        // A guide has no business reaching out for other files
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        factory.newSAXParser().parse(input, handler)
        out.values.forEach { list -> list.sortBy { it.start } }
        return out
    }
}
