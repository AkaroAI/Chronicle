package com.akaroai.chronicle.data

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

object ImportDocumentReader {
    fun read(input: InputStream): String {
        val bytes = input.use { it.readBytes() }
        require(bytes.isNotEmpty()) { "That campaign file is empty." }
        return when {
            bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() ->
                readDocx(bytes)
            bytes.size >= 4 && bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "%PDF" ->
                error("PDF text extraction is not available yet. Export this file as DOCX or UTF-8 text and import it again.")
            bytes.any { it == 0.toByte() } ->
                error("This binary document format is not supported. Export it as DOCX or UTF-8 text first.")
            else -> bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        }.replace("\r\n", "\n").replace('\r', '\n').trim()
    }

    private fun readDocx(bytes: ByteArray): String {
        var documentXml: String? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name == "word/document.xml") {
                    documentXml = zip.readBytes().toString(Charsets.UTF_8)
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val xml = documentXml ?: error("This ZIP file is not a readable Word DOCX document.")
        val text = xml
            .replace(Regex("""<w:tab\b[^>]*/>"""), "\t")
            .replace(Regex("""<w:br\b[^>]*/>"""), "\n")
            .replace(Regex("""</w:p>"""), "\n")
            .replace(Regex("""</w:tr>"""), "\n")
            .replace(Regex("""<[^>]+>"""), "")
            .replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'")
            .replace("&amp;", "&")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
        require(text.isNotBlank()) { "The Word document contains no readable text." }
        return text
    }
}
