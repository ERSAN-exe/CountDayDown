package com.Zero23.countdown.data

/**
 * How the pictures of a countdown card travel in an exported backup zip.
 *
 * A backup is a zip with a `backup.json` next to an `images/` folder. The JSON cannot keep the
 * absolute paths of the card images (they are private to the device and the install that wrote
 * them), so every image uri is replaced by the name of the entry that holds its bytes.
 */

/** Name prefix of every image entry inside an exported backup. */
internal const val BACKUP_IMAGE_ENTRY_PREFIX = "images/"

/**
 * Key under which the background image at [index] of the card with [eventId] is stored.
 * The first image keeps the legacy `{id}_bg` name so backups written by older versions stay
 * readable.
 */
internal fun bgImageEntryKey(eventId: String, index: Int): String =
    if (index == 0) "${eventId}_bg" else "${eventId}_bg_$index"

/** Key under which the square (1:1) crop at [index] of the card with [eventId] is stored. */
internal fun squareImageEntryKey(eventId: String, index: Int): String = "${eventId}_sq_$index"

/** Key under which the image shown by the 2x2 widget of the card with [eventId] is stored. */
internal fun widgetImageEntryKey(eventId: String): String = "${eventId}_widget"

/**
 * Export side: swaps the image uris of the card for the entry names that hold their bytes.
 *
 * [zipImages] maps the keys above to entry names; an image that is missing from it was not
 * exported (unreadable uri) and keeps its original uri, and the same goes for the font in
 * [zipFonts].
 *
 * Both background fields are written, not only the stacked list. [CountdownEvent.getBgImageUris]
 * prefers the list over the single uri, so a card with a single picture used to keep the old
 * absolute path there and came back pointing at a file that does not exist on the device the
 * backup is applied to - which is what makes such a card render black.
 */
internal fun CountdownEvent.zipEntryUris(
    zipImages: Map<String, String>,
    zipFonts: Map<String, String>
): CountdownEvent {
    val zippedBgUris = getBgImageUris().mapIndexed { index, uriStr ->
        zipImages[bgImageEntryKey(id, index)] ?: uriStr
    }
    return copy(
        backgroundImageUri = zippedBgUris.firstOrNull() ?: backgroundImageUri,
        backgroundImageUris = zippedBgUris.ifEmpty { null },
        backgroundSquareImageUris = backgroundSquareImageUris?.mapIndexed { index, uriStr ->
            zipImages[squareImageEntryKey(id, index)] ?: uriStr
        },
        widgetImageUri = zipImages[widgetImageEntryKey(id)] ?: widgetImageUri,
        customFontPath = customFontPath?.let { zipFonts[it] } ?: customFontPath
    )
}

/**
 * Import side: swaps every entry reference of the card for the local file its bytes were written
 * to.
 *
 * [imageFiles] maps entry names to the bytes stored in the backup and [restoreImage] writes one
 * entry to disk and returns the uri of the copy. It is only called for entries the backup really
 * carries; a uri the backup has no bytes for is kept as it is, except for square crops, which are
 * dropped so the grid falls back to the background image instead of a dangling reference.
 */
internal fun CountdownEvent.fromZipEntryUris(
    imageFiles: Map<String, ByteArray>,
    restoreImage: (entryName: String, fileName: String) -> String?
): CountdownEvent {
    fun restore(uriStr: String?, fileName: String, legacyKeys: List<String> = emptyList()): String? {
        if (uriStr == null || !uriStr.startsWith(BACKUP_IMAGE_ENTRY_PREFIX)) return null
        val entryName = (listOf(uriStr) + legacyKeys).firstOrNull { it in imageFiles } ?: return null
        return restoreImage(entryName, fileName)
    }

    val stackedUris = getBgImageUris()
    val restoredStackedUris = stackedUris.mapIndexed { index, uriStr ->
        // Legacy backups stored the first image only as "images/{id}_bg"
        val legacyKeys = if (index == 0) {
            listOf(BACKUP_IMAGE_ENTRY_PREFIX + bgImageEntryKey(id, 0))
        } else {
            emptyList()
        }
        restore(uriStr, "${id}_bg_$index", legacyKeys) ?: uriStr
    }

    // The single uri carries an entry of its own only when the stacked list does not. Older
    // backups left the original paths in the list while the picture itself was referenced from
    // here only; restoring it is what brings those cards back with their picture.
    val restoredSingleUri = backgroundImageUri
        ?.takeIf { it !in stackedUris }
        ?.let { restore(it, "${id}_bg_0") }
    val bgUris = if (restoredSingleUri != null && restoredStackedUris.size <= 1) {
        listOf(restoredSingleUri)
    } else {
        restoredStackedUris
    }

    return copy(
        backgroundImageUri = bgUris.firstOrNull() ?: backgroundImageUri,
        backgroundImageUris = bgUris.ifEmpty { null },
        backgroundSquareImageUris = backgroundSquareImageUris?.mapIndexedNotNull { index, uriStr ->
            restore(uriStr, "${id}_sq_$index")
                ?: uriStr.takeIf { !it.startsWith(BACKUP_IMAGE_ENTRY_PREFIX) }
        }?.ifEmpty { null },
        widgetImageUri = restore(widgetImageUri, "${id}_widget") ?: widgetImageUri
    )
}
