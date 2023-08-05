package com.confinitum.pdfview.base

import com.confinitum.pdfview.PDFView
import javafx.scene.control.ListView
import javafx.scene.control.skin.VirtualFlow
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.max

private var executor: Executor? = null
fun getExecutor(multi: Boolean): Executor {
    if (executor == null) {
        executor = if (multi) {
            Executors.newCachedThreadPool { r: Runnable? ->
                val thread = Thread(r)
                thread.isDaemon = true
                thread
            }
        } else {
            Executors.newSingleThreadExecutor { r: Runnable? ->
                val thread = Thread(r, PDFView::class.java.getSimpleName() + " Thread")
                thread.isDaemon = true
                thread
            }

        }
    }
    return executor!!
}

fun <T> maybeScrollTo(listView: ListView<T>, item: T) {
    /*
         * We want to make sure that the selected result will be visible within the list view,
         * but we do not want to scroll every time the selected search result changes. We really
         * only want to perform a scrolling if the newly selected search result is not within the
         * currently visible rows of the list view.
         */
    val virtualFlow = listView.lookup("VirtualFlow") as VirtualFlow<*>?
    if (virtualFlow != null) {
        val firstVisibleCell = virtualFlow.firstVisibleCell
        val lastVisibleCell = virtualFlow.lastVisibleCell
        if (firstVisibleCell != null && lastVisibleCell != null) {

            /*
                 * Adding 1 to start and subtracting 1 from the end as the calculations of the
                 * currently visible cells doesn't seem to work perfectly. Also, if only a fraction
                 * of a cell is visible then it requires scrolling, too.
                 */
            val start = max(0, firstVisibleCell.index + 1)
            val end = max(1, lastVisibleCell.index - 1)
            val index = listView.items.indexOf(item)
            if (index < start || index > end) {
                listView.scrollTo(item)
            }
        }
    }
}

