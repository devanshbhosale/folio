package com.folio.viewer.domain

/**
 * Every format Folio can open. `ext` is the lowercased dotless file extension.
 * Detection prefers MIME first, then extension.
 */
enum class DocumentFormat(val display: String, val exts: List<String>, val mimes: List<String>) {
    PDF("PDF", listOf("pdf"), listOf("application/pdf")),
    WORD("Word", listOf("doc", "docx"), listOf(
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    )),
    EXCEL("Excel", listOf("xls", "xlsx"), listOf(
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )),
    POWERPOINT("Slides", listOf("ppt", "pptx"), listOf(
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    )),
    CSV("CSV", listOf("csv", "tsv"), listOf("text/csv", "text/tab-separated-values")),
    TEXT("Text", listOf("txt", "log", "md"), listOf("text/plain", "text/markdown")),
    RTF("RTF", listOf("rtf"), listOf("application/rtf", "text/rtf")),
    ODT("ODT", listOf("odt"), listOf("application/vnd.oasis.opendocument.text")),
    NUMBERS("Numbers", listOf("numbers"), listOf("application/x-iwork-numbers-sffnumbers")),
    PAGES("Pages", listOf("pages"), listOf("application/x-iwork-pages-sffpages")),
    UNSUPPORTED("File", emptyList(), emptyList());

    companion object {
        fun fromMimeOrExtension(mime: String?, name: String?): DocumentFormat {
            val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
            val mimeLower = mime?.lowercase().orEmpty()
            entries.forEach { fmt ->
                if (mimeLower.isNotEmpty() && fmt.mimes.any { it.equals(mimeLower, true) }) return fmt
            }
            entries.forEach { fmt -> if (ext.isNotEmpty() && ext in fmt.exts) return fmt }
            return UNSUPPORTED
        }
    }
}
