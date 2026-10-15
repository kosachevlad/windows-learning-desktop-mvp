package ua.school.windowsdesktop

import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Small OOXML writer/reader used for Word documents. It keeps the editor's common spans. */
object DocxFormat {
    fun write(value: CharSequence): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entry(zip, "[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""")
            entry(zip, "_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            entry(zip, "word/document.xml", documentXml(value))
        }
        return out.toByteArray()
    }

    fun read(bytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.name == "word/document.xml") {
                    val xml = zip.readBytes().toString(Charsets.UTF_8)
                    return xml.replace(Regex("</w:p>"), "\n")
                        .replace(Regex("<w:tab[^>]*/>"), "\t")
                        .replace(Regex("<[^>]+>"), "")
                        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
                }
            }
        }
        return bytes.toString(Charsets.UTF_8)
    }

    private fun documentXml(value: CharSequence): String {
        val s = value as? Spanned
        val text = value.toString()
        val body = StringBuilder()
        var paragraph = StringBuilder()
        fun flush() { body.append("<w:p><w:pPr><w:spacing w:after=\"0\"/></w:pPr>").append(paragraph).append("</w:p>"); paragraph = StringBuilder() }
        for (i in text.indices) {
            if (text[i] == '\n') { flush(); continue }
            val end = i + 1
            val styles = s?.getSpans(i, end, Any::class.java).orEmpty()
            val props = StringBuilder()
            styles.filterIsInstance<StyleSpan>().forEach { if (it.style == android.graphics.Typeface.BOLD || it.style == android.graphics.Typeface.BOLD_ITALIC) props.append("<w:b/>"); if (it.style == android.graphics.Typeface.ITALIC || it.style == android.graphics.Typeface.BOLD_ITALIC) props.append("<w:i/>") }
            if (styles.any { it is UnderlineSpan }) props.append("<w:u w:val=\"single\"/>")
            styles.filterIsInstance<ForegroundColorSpan>().firstOrNull()?.let { props.append("<w:color w:val=\"%06X\"/>".format(it.foregroundColor and 0xFFFFFF)) }
            styles.filterIsInstance<AbsoluteSizeSpan>().firstOrNull()?.let { props.append("<w:sz w:val=\"${it.size * 2}\"/>") }
            styles.filterIsInstance<TypefaceSpan>().firstOrNull()?.family?.let { family -> props.append("<w:rFonts w:ascii=\"${xml(family)}\" w:hAnsi=\"${xml(family)}\"/>") }
            paragraph.append("<w:r>").append(if (props.isNotEmpty()) "<w:rPr>$props</w:rPr>" else "").append("<w:t xml:space=\"preserve\">").append(xml(text[i].toString())).append("</w:t></w:r>")
        }
        if (paragraph.isNotEmpty() || text.isEmpty()) flush()
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>$body</w:body></w:document>"
    }
    private fun entry(zip: ZipOutputStream, name: String, data: String) { zip.putNextEntry(ZipEntry(name)); zip.write(data.toByteArray()); zip.closeEntry() }
    private fun xml(v: String) = v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
