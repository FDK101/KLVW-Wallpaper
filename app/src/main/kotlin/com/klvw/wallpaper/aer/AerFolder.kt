package com.klvw.wallpaper.aer

data class AerFolder(val name: String, val relPath: String)

object AerUri {
    const val SCHEME = "klvw-aer://folder/"
    const val ROOT = "klvw-aer://folder/root"

    fun of(relPath: String?): String = if (relPath.isNullOrEmpty()) ROOT else "$SCHEME$relPath"

    /** Returns null for root, relative path string for sub-folders */
    fun relPath(uri: String): String? {
        val suffix = uri.removePrefix(SCHEME)
        return if (suffix == "root" || suffix.isEmpty()) null else suffix
    }
}
