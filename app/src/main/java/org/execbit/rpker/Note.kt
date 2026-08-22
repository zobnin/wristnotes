package org.execbit.rpker

internal data class Note(
    val id: String,
    val markdown: String,
)

internal fun notePreview(markdown: String): String = markdown
    .lineSequence()
    .filter(String::isNotBlank)
    .take(2)
    .joinToString("\n")

internal fun <T> List<T>.moved(fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex == toIndex) return this
    require(fromIndex in indices && toIndex in indices)
    return toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}
