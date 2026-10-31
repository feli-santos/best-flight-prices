package bfp.ui

/** The only things the shared UI needs from the host platform. Implemented once per app shell. */
interface Platform {
    fun openUrl(url: String)
    fun nowMs(): Long
    /** BCP-47 tag of the device language, e.g. "pt-BR". */
    val languageTag: String
}
