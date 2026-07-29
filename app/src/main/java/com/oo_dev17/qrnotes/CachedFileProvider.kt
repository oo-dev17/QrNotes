package com.oo_dev17.qrnotes

/**
 * Temporary holder for passing a CachedFileHandler between components when starting an Activity.
 *
 * NOTE: This should only be used for short-lived handoff (set -> start Activity -> consume -> clear)
 * because it holds a reference to a Context via CachedFileHandler. The consumer Activity must clear
 * the reference after retrieving it to avoid leaks.
 */
object CachedFileProvider {
    var handler: CachedFileHandler? = null
}
