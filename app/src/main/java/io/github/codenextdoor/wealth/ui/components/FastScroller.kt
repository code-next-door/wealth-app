package io.github.codenextdoor.wealth.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A thumb on the right edge of a long list, like a photo gallery's: it shows while the list
 * scrolls, dragging it jumps through the list, and a bubble names where the finger is
 * ([label] of a visible item key, e.g. its month). Screen readers
 * scroll the list themselves, so the thumb isn't in the accessibility tree.
 */
@Composable
fun FastScroller(state: LazyListState, label: (key: Any) -> String?, modifier: Modifier = Modifier) {
    // Only worth it when the list is several screens long.
    val long by remember {
        derivedStateOf { state.layoutInfo.totalItemsCount > state.layoutInfo.visibleItemsInfo.size * 3 }
    }
    var dragging by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }
    val active = long && (dragging || state.isScrollInProgress)
    LaunchedEffect(active) {
        if (active) shown = true else { delay(1_500); shown = false }
    }
    AnimatedVisibility(shown, modifier.fillMaxHeight(), enter = fadeIn(), exit = fadeOut()) {
        BoxWithConstraints(Modifier.fillMaxHeight()) {
            val thumbHeight = 48.dp
            val track = with(LocalDensity.current) { (maxHeight - thumbHeight).toPx() }.coerceAtLeast(1f)
            // Where the thumb sits while dragged, as a fraction of the track; follows the list otherwise.
            var dragFraction by remember { mutableFloatStateOf(0f) }
            val listFraction by remember {
                derivedStateOf {
                    val info = state.layoutInfo
                    val last = (info.totalItemsCount - info.visibleItemsInfo.size).coerceAtLeast(1)
                    (state.firstVisibleItemIndex.toFloat() / last).coerceIn(0f, 1f)
                }
            }
            val fraction = if (dragging) dragFraction else listFraction
            val scope = rememberCoroutineScope()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.TopEnd).offset { IntOffset(0, (fraction * track).roundToInt()) },
            ) {
                if (dragging) {
                    // The visible row as far down the screen as the thumb is down the track:
                    // the top row at the start, the last one at the end.
                    val labelled = state.layoutInfo.visibleItemsInfo.mapNotNull { label(it.key) }
                    labelled.getOrNull((fraction * (labelled.size - 1)).roundToInt())?.let {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                }
                // A 48 dp touch area around a thin visible bar.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp, thumbHeight)
                        .testTag("fastScroller")
                        .pointerInput(state) {
                            detectVerticalDragGestures(
                                onDragStart = { dragFraction = listFraction; dragging = true },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false },
                            ) { change, dy ->
                                change.consume()
                                dragFraction = (dragFraction + dy / track).coerceIn(0f, 1f)
                                val info = state.layoutInfo
                                val last = (info.totalItemsCount - info.visibleItemsInfo.size).coerceAtLeast(0)
                                // The end of the track shows the list's end, not its last item at the top.
                                val index = if (dragFraction >= 1f) info.totalItemsCount - 1 else (dragFraction * last).roundToInt()
                                scope.launch { state.scrollToItem(index) }
                            }
                        },
                ) {
                    Box(
                        Modifier.size(6.dp, thumbHeight - 8.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
                    )
                }
            }
        }
    }
}
