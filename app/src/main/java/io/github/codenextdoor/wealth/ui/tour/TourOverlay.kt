package io.github.codenextdoor.wealth.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.codenextdoor.wealth.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** The app's tour, provided by MainActivity (null in previews and screens without it). */
val LocalTour = staticCompositionLocalOf<Tour?> { null }

/** Where each tour target is on screen; provided by the screen that shows the overlay. */
val LocalTourTargets = staticCompositionLocalOf<SnapshotStateMap<TourTarget, Rect>?> { null }

/** Marks this element as what a tour stop points at. */
@Composable
fun Modifier.tourTarget(target: TourTarget): Modifier {
    val targets = LocalTourTargets.current ?: return this
    DisposableEffect(target) { onDispose { targets.remove(target) } }
    return onGloballyPositioned { targets[target] = it.boundsInRoot() }
}

/**
 * The tour over the home screen: dims everything except the current stop's
 * target, with a bubble pointing at it. Only Next / Skip / Back move or end it;
 * a tap on the dimmed area does nothing. A stop whose target doesn't appear is
 * skipped. Nothing behind is ever tapped.
 */
@Composable
fun TourOverlay(tour: Tour, targets: Map<TourTarget, Rect>) {
    val index by tour.step.collectAsStateWithLifecycle()
    val current = index ?: return
    val step = TourSteps.all[current]
    // A target that never appears (e.g. hidden on a small screen): go on to the next stop.
    LaunchedEffect(current) {
        if (withTimeoutOrNull(1_500) { snapshotFlow { targets[step.target] }.first { it != null } } == null) tour.next()
    }
    BackHandler { tour.stop() }

    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val hole = targets[step.target]?.translate(-origin)?.inflate(with(density) { 6.dp.toPx() })
    val dim = Color.Black.copy(alpha = 0.62f)
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            // Taps on the dimmed area are swallowed: a stray tap neither ends the tour nor reaches the app.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            drawRect(dim)
            hole?.let {
                val radius = 16.dp.toPx()
                drawRoundRect(Color.Black, it.topLeft, it.size, CornerRadius(radius), blendMode = BlendMode.Clear)
            }
        }
        if (hole != null) {
            PointingBubble(hole) {
                Bubble(
                    step = step,
                    number = current + 1,
                    count = TourSteps.all.size,
                    onNext = tour::next,
                    onSkip = tour::stop,
                )
            }
        }
    }
}

/** Places [bubble] below the target when it's in the top half of the screen, above it otherwise, with an arrow to it. */
@Composable
private fun PointingBubble(target: Rect, bubble: @Composable () -> Unit) {
    val arrowColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val density = LocalDensity.current
    Layout(
        content = {
            Box { bubble() }
            // An arrow pointing up at the target; flipped when the bubble is above it.
            Canvas(Modifier.size(20.dp, 10.dp)) {
                val path = Path().apply {
                    moveTo(0f, size.height)
                    lineTo(size.width / 2, 0f)
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(path, arrowColor)
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val margin = with(density) { 16.dp.roundToPx() }
        val gap = with(density) { 10.dp.roundToPx() }
        val width = minOf(constraints.maxWidth - 2 * margin, with(density) { 360.dp.roundToPx() })
        val bubblePlaceable = measurables[0].measure(Constraints(minWidth = width, maxWidth = width))
        val arrow = measurables[1].measure(Constraints())
        val below = target.center.y < constraints.maxHeight / 2
        val x = (target.center.x - width / 2f).roundToInt().coerceIn(margin, constraints.maxWidth - margin - width)
        val y = if (below) target.bottom.roundToInt() + gap else target.top.roundToInt() - gap - bubblePlaceable.height
        val arrowX = (target.center.x - arrow.width / 2f).roundToInt().coerceIn(x + margin, x + width - margin - arrow.width)
        layout(constraints.maxWidth, constraints.maxHeight) {
            bubblePlaceable.place(x, y)
            if (below) {
                arrow.place(arrowX, y - arrow.height)
            } else {
                // The same triangle, flipped to point down.
                arrow.placeWithLayer(arrowX, y + bubblePlaceable.height) { rotationZ = 180f }
            }
        }
    }
}

@Composable
private fun Bubble(step: TourStep, number: Int, count: Int, onNext: () -> Unit, onSkip: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        // Read out as each stop appears.
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(step.title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(step.text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(
                    stringResource(R.string.tour_progress, number, count),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (number < count) {
                    TextButton(onClick = onSkip) { Text(stringResource(R.string.tour_skip)) }
                }
                Button(onClick = onNext) {
                    Text(stringResource(if (number < count) R.string.tour_next else R.string.tour_done))
                }
            }
        }
    }
}
