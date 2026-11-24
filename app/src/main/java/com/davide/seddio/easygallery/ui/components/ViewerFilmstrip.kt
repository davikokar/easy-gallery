package com.davide.seddio.easygallery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.davide.seddio.easygallery.data.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.math.max

private val FILMSTRIP_ITEM_SIZE = 44.dp
private val FILMSTRIP_ITEM_SPACING = 4.dp
private val FILMSTRIP_HEIGHT = 56.dp
private val FILMSTRIP_BORDER_WIDTH = 2.dp
private val FILMSTRIP_CORNER_RADIUS = 4.dp

/**
 * A row of small thumbnails of [items], the one at the centre being the page [mainState] shows.
 * Swiping it drives [mainState] live; tapping a thumbnail jumps [mainState] to it.
 *
 * [mainState] is the source of truth and [stripState] only follows it, except while the user is
 * driving the strip. [onUserDrivenChange] reports that window so the viewer can keep ExoPlayer unbound
 * until the strip has settled.
 */
@Composable
fun ViewerFilmstrip(
    items: List<MediaItem>,
    mainState: PagerState,
    stripState: PagerState,
    onUserDrivenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    FilmstripPagerSync(mainState, stripState, onUserDrivenChange)

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    // One symmetric inset keeps the centre of the strip on the centre of the media above it.
    val sideInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).let { insets ->
        with(density) { max(insets.getLeft(density, layoutDirection), insets.getRight(density, layoutDirection)).toDp() }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(FILMSTRIP_HEIGHT)
            .testTag("viewer_filmstrip")
            .padding(horizontal = sideInset)
    ) {
        val edgePadding = filmstripSideInset(maxWidth.value, FILMSTRIP_ITEM_SIZE.value).dp
        HorizontalPager(
            state = stripState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = edgePadding),
            pageSize = PageSize.Fixed(FILMSTRIP_ITEM_SIZE),
            pageSpacing = FILMSTRIP_ITEM_SPACING,
            // The default snap distance is one page, under which a fling would move a single thumbnail.
            flingBehavior = PagerDefaults.flingBehavior(
                state = stripState,
                pagerSnapDistance = PagerSnapDistance.atMost(items.size)
            ),
            key = { index -> items.getOrNull(index)?.uri?.toString() ?: index },
            snapPosition = SnapPosition.Center
        ) { page ->
            val item = items.getOrNull(page) ?: return@HorizontalPager
            FilmstripCell(
                item = item,
                page = page,
                stripState = stripState,
                onClick = {
                    if (page != stripState.currentPage) {
                        scope.launch { mainState.scrollToPageIgnoringInterruption(page) }
                    }
                }
            )
        }
    }
}

@Composable
private fun FilmstripCell(
    item: MediaItem,
    page: Int,
    stripState: PagerState,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val shape = RoundedCornerShape(FILMSTRIP_CORNER_RADIUS)
        AsyncImage(
            model = item.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(FILMSTRIP_ITEM_SIZE)
                // Read in the layer and draw phases, so a scroll changes the look without recomposing.
                .graphicsLayer {
                    val scale = filmstripItemScale(stripState.getOffsetDistanceInPages(page))
                    scaleX = scale
                    scaleY = scale
                }
                .clip(shape)
                .background(Color.White.copy(alpha = 0.12f))
                .drawWithContent {
                    drawContent()
                    val fraction = filmstripSelectionFraction(stripState.getOffsetDistanceInPages(page))
                    if (fraction > 0f) {
                        val stroke = FILMSTRIP_BORDER_WIDTH.toPx()
                        drawRoundRect(
                            color = Color.White.copy(alpha = fraction),
                            topLeft = Offset(stroke / 2f, stroke / 2f),
                            size = Size(size.width - stroke, size.height - stroke),
                            cornerRadius = CornerRadius(FILMSTRIP_CORNER_RADIUS.toPx()),
                            style = Stroke(width = stroke)
                        )
                    }
                }
                .testTag("viewer_filmstrip_item_$page")
                .semantics {
                    contentDescription = item.name
                    selected = page == stripState.currentPage
                }
                .clickable(role = Role.Button, onClick = onClick)
        )
    }
}

/**
 * Keeps the two pagers on one page without a loop. The strip is "user driven" from the first drag on
 * it until its scroll, fling and snap have all stopped; only then does it hand control back.
 */
@Composable
internal fun FilmstripPagerSync(
    mainState: PagerState,
    stripState: PagerState,
    onUserDrivenChange: (Boolean) -> Unit
) {
    val reportDriven by rememberUpdatedState(onUserDrivenChange)
    val dragged by stripState.interactionSource.collectIsDraggedAsState()
    val stripScrolling = stripState.isScrollInProgress
    var driven by remember { mutableStateOf(false) }
    var placed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { reportDriven(false) }
    }

    LaunchedEffect(dragged, stripScrolling) {
        when {
            dragged -> if (!driven) {
                driven = true
                reportDriven(true)
            }
            stripScrolling -> Unit
            driven -> {
                // A release hands over from the drag to the fling in a gap that reads as idle for a frame.
                repeat(2) { withFrameNanos { } }
                if (mainState.currentPage != stripState.currentPage) {
                    mainState.scrollToPageIgnoringInterruption(stripState.currentPage)
                }
                driven = false
                reportDriven(false)
            }
        }
    }

    // Keyed on the driven flag and not a long-lived collector: a user drag cancels an animateScrollToPage,
    // and that cancellation would silently end a collector that never restarts.
    LaunchedEffect(driven) {
        if (driven) {
            snapshotFlow { stripState.currentPage }.collect { stripPage ->
                if (filmstripSync(mainState.currentPage, stripPage, stripUserDriven = true) == FilmstripSync.MAIN_FOLLOWS_STRIP) {
                    mainState.scrollToPageIgnoringInterruption(stripPage)
                }
            }
        }
    }

    LaunchedEffect(mainState.currentPage, driven) {
        val mainPage = mainState.currentPage
        if (filmstripSync(mainPage, stripState.currentPage, driven) == FilmstripSync.STRIP_FOLLOWS_MAIN) {
            // The first sync snaps, so a strip that was off screen while the bar was hidden does not slide in from afar.
            if (placed) stripState.animateScrollToPage(mainPage) else stripState.scrollToPage(mainPage)
        }
        placed = true
    }
}

/** A scroll cut short by the user touching the pager is not an error; only our own cancellation propagates. */
private suspend fun PagerState.scrollToPageIgnoringInterruption(page: Int) {
    try {
        scrollToPage(page)
    } catch (_: CancellationException) {
        currentCoroutineContext().ensureActive()
    }
}
