package com.akaroai.chronicle.data

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ImportDocumentReaderTest {
    @Test fun `docx package yields document text not zip bytes`() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write("""<w:document><w:body><w:p><w:r><w:t>Asira &amp; Yuki</w:t></w:r></w:p><w:p><w:r><w:t>Moonfall</w:t></w:r></w:p></w:body></w:document>""".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        assertEquals("Asira & Yuki\nMoonfall", ImportDocumentReader.read(ByteArrayInputStream(bytes)))
    }

    @Test fun `plain utf8 text is preserved`() {
        val text = "Player: Yuki 🌙\n\nAssistant: Asira"
        assertEquals(text, ImportDocumentReader.read(ByteArrayInputStream(text.toByteArray())))
    }
}
