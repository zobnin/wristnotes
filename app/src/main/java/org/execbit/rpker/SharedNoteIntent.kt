package org.execbit.rpker

import android.content.Intent

internal fun Intent.sharedNoteText(): String? {
    if (action != Intent.ACTION_SEND || type != "text/plain") return null
    return getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
}
