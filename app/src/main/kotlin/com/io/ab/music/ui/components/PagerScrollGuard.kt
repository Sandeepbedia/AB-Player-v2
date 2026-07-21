package com.io.ab.music.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

/**
 * FIX: The Home / Video / Explore tabs live as pages of one outer
 * `HorizontalPager` (see NavGraph.kt). Several of those pages also contain
 * their own horizontal-scrolling card rows (Home's Recently Played,
 * Explore's row, Video's rows). Because both the page-swipe gesture and the
 * card-row scroll are on the SAME axis, dragging a card row could also
 * partially drag the outer Pager — so scrolling the cards visibly "switched
 * the tab page" underneath them.
 *
 * `PagerScrollGuard.pagerScrollEnabled` is read by NavGraph as the tab
 * Pager's `userScrollEnabled`, and is flipped off for the duration of a
 * touch on any row using [Modifier.guardPagerScroll]. That alone still left
 * a one-frame gap: flipping a boolean read via `userScrollEnabled` only
 * takes effect on Pager's NEXT recomposition, so a fast flick could get one
 * or two pixels of unwanted page-drag in before it kicked in. To close that
 * gap completely, guardPagerScroll ALSO watches the gesture on Compose's
 * Main pointer pass (which runs leaf-to-root, i.e. strictly AFTER the row's
 * own LazyRow/scrollable has already consumed whatever delta it needed) and
 * consumes any horizontal movement still left over, so the outer Pager
 * (further up the tree, processed even later on that same pass) can never
 * see it — no recomposition required, so there's no timing window left.
 */
object PagerScrollGuard {
    var pagerScrollEnabled by mutableStateOf(true)
}

/** Apply to any horizontal-scrolling row (LazyRow, Row+scroll, etc.) that
 *  lives inside a page of the main bottom-nav Pager, so swiping it doesn't
 *  also flip the tab underneath. The row's own scrolling still works exactly
 *  as before — only leftover horizontal movement that would otherwise leak
 *  into the outer Pager gets swallowed. */
fun Modifier.guardPagerScroll(): Modifier = composed {
    this.pointerInput(Unit) {
        awaitEachGesture {
            // Observe only — requireUnconsumed = false so we still see the
            // down event even if a sibling gesture detector reacts to it too.
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            PagerScrollGuard.pagerScrollEnabled = false
            try {
                var pressed = true
                while (pressed) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Main)
                    event.changes.forEach { change ->
                        if (change.pressed && change.positionChange() != Offset.Zero) {
                            // FIX: this used to consume ANY leftover movement
                            // here — horizontal AND vertical — which meant
                            // starting a vertical scroll with a finger down on
                            // one of these card rows swallowed that vertical
                            // drag too, so the page underneath couldn't be
                            // scrolled down while touching a card. Only guard
                            // the horizontal axis (the one that actually
                            // conflicts with the outer Pager's swipe); let a
                            // predominantly-vertical drag pass through
                            // untouched so the page keeps scrolling normally.
                            val (dx, dy) = change.positionChange()
                            if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                                // The row itself (a deeper node) already had
                                // its turn earlier in this same Main-pass
                                // dispatch — consuming here only stops it from
                                // bubbling further up to the tab Pager.
                                change.consume()
                            }
                        }
                    }
                    pressed = event.changes.any { it.pressed }
                }
            } finally {
                PagerScrollGuard.pagerScrollEnabled = true
            }
        }
    }
}
