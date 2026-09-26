package com.Zero23.countdown

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared metrics for every top bar in the app.
 *
 * Each bar keeps a fixed [TOP_BAR_CONTENT_HEIGHT] of content and centres its children in it, so the
 * title pill and the 48dp action button always share one centre line:
 *
 *     centre line = statusBarInset + 8dp + TOP_BAR_CONTENT_HEIGHT / 2
 *                 = statusBarInset + 8dp + 24dp + TOP_BAR_ACTION_OFFSET
 *
 * [TOP_BAR_ACTION_OFFSET] is the extra top padding for the two elements that are *not* centred in
 * such a row: the home screen's button row (its own title pill is two lines tall, so that row's
 * height is content driven) and the pinned settings/back button that MainActivity keeps above the
 * NavHost so it can morph in place. Both come out on the same line as the centred bars.
 *
 * They are single shared values rather than a copy per screen, because that pinned button has to
 * stay in step with the pages underneath it: tuning [TOP_BAR_ACTION_OFFSET] moves the whole button
 * line everywhere at once.
 */
internal val TOP_BAR_ACTION_OFFSET = 10.dp

/**
 * Content height of every top bar. Fixing it makes the row's centre line deterministic: the title
 * pill and the 48dp button slot are both centred in it, so the title's centre line lands exactly on
 * the button's own centre line (statusBarInset + 8dp + 24dp) instead of depending on how tall the
 * title pill happens to be.
 */
internal val TOP_BAR_CONTENT_HEIGHT = 48.dp + TOP_BAR_ACTION_OFFSET * 2

/**
 * Font size of the top-bar titles, and the line height derived from it. Deriving the line height
 * keeps the title pill growing with the glyphs; inheriting body-large's fixed 24sp line height
 * would squeeze them into it instead.
 */
internal val TOP_BAR_TITLE_FONT_SIZE = 22.sp
internal val TOP_BAR_TITLE_LINE_HEIGHT = TOP_BAR_TITLE_FONT_SIZE * 1.3f

/**
 * Corner radii of the top-bar chrome: the title pill and the 48dp action buttons. Shared so that
 * every screen's bar agrees, including the two corners of the search field that fuses with the
 * search button on the home screen.
 */
internal val TOP_BAR_TITLE_CORNER = 24.dp
internal val TOP_BAR_ACTION_CORNER = 12.dp
