package org.execbit.rpker

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

internal class NotesDragState(
    val listState: LazyListState,
    private val move: (Int, Int) -> Unit,
) {
    var draggedItemIndex by mutableStateOf<Int?>(null)
        private set

    private var initialItem by mutableStateOf<LazyListItemInfo?>(null)
    private var draggedDistance by mutableFloatStateOf(0f)

    val draggedItemOffset: Float
        get() {
            val index = draggedItemIndex ?: return 0f
            val initialOffset = initialItem?.offset ?: return 0f
            val currentOffset = visibleItem(index)?.offset ?: return 0f
            return initialOffset + draggedDistance - currentOffset
        }

    fun start(index: Int) {
        val item = visibleItem(index) ?: return
        draggedItemIndex = index
        initialItem = item
        draggedDistance = 0f
    }

    fun dragBy(deltaY: Float): Float {
        val currentIndex = draggedItemIndex ?: return 0f
        val initial = initialItem ?: return 0f
        draggedDistance += deltaY

        val draggedStart = initial.offset + draggedDistance
        val draggedEnd = draggedStart + initial.size
        val draggedCenter = (draggedStart + draggedEnd) / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.index != currentIndex && draggedCenter >= item.offset && draggedCenter <= item.offset + item.size
        }

        if (target != null) {
            move(currentIndex, target.index)
            draggedItemIndex = target.index
        }

        val layout = listState.layoutInfo
        return when {
            draggedEnd > layout.viewportEndOffset ->
                (draggedEnd - layout.viewportEndOffset).coerceAtMost(MAX_SCROLL_STEP)
            draggedStart < layout.viewportStartOffset ->
                (draggedStart - layout.viewportStartOffset).coerceAtLeast(-MAX_SCROLL_STEP)
            else -> 0f
        }
    }

    fun end() {
        draggedItemIndex = null
        initialItem = null
        draggedDistance = 0f
    }

    private fun visibleItem(index: Int): LazyListItemInfo? =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }

    private companion object {
        const val MAX_SCROLL_STEP = 28f
    }
}

@Composable
internal fun rememberNotesDragState(
    listState: LazyListState,
    onMove: (Int, Int) -> Unit,
): NotesDragState {
    val currentOnMove by rememberUpdatedState(onMove)
    return remember(listState) {
        NotesDragState(listState) { fromIndex, toIndex ->
            currentOnMove(fromIndex, toIndex)
        }
    }
}
