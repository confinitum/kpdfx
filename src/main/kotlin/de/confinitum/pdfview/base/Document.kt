package de.confinitum.pdfview.base

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.geometry.Rectangle2D
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.rendering.RenderDestination
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream


class ToolbarConfig {
    var pageFit = true
    var zooming = true
    var pages = true
    var rotation = true
    var thumbnails = true
}


/**
 * The interface that needs to be implemented by any model object that
 * represents a PDF document and that wants to be displayed by the view.
 *
 * @see PDFBoxDocument
 */
interface Document {
    /**
     * List of pages with additional properties. Data source for thumbnail list
     */
    val pagesList: ObservableList<PageKey>?

    /**
     * Sets rotation for given page
     */
    fun setPageRotation(pageNumber: Int, rotationAngle: Double)

    /**
     * Gets rotation for given page
     */
    fun getPageRotation(pageNumber: Int): PageKey

    /**
     * Sets viewport for given page
     */
    fun setViewport(pageNumber: Int, viewport: Rectangle2D?)

    /**
     * Renders the page specified by the given number at the given scale.
     *
     * @param pageNumber the page number
     * @param scale      the scale
     * @param rotationAngle the rotation angle
     * @param useCache cache the page (used for thumbnails)
     * @return the generated buffered image
     */
    fun renderPage(pageNumber: Int, scale: Float, rotationAngle: Double = 0.0, useCache: Boolean): BufferedImage?

    /**
     * Returns the total number of pages inside the document.
     *
     * @return the total number of pages
     */
    val numberOfPages: Int

    /**
     * Determines if the given page has a landscape orientation.
     *
     * @param pageNumber the page
     * @return true if the page has to be shown in landscape mode
     */
    fun isLandscape(pageNumber: Int): Boolean

    /**
     * Closes the document.
     */
    fun close()

}

/**
 * Properties holder for each page
 */
data class PageKey(
    val pageNumber: Int,
    val rotationAngle: Double,
    val viewport: Rectangle2D? = null
)

/**
 * Implementation of [Document] for the Apache PDFBox library.
 */
class PDFBoxDocument(pdfInputStream: InputStream?) : Document {
    private val document: PDDocument
    private val imageCache = mutableMapOf<PageKey, BufferedImage>()

    override val pagesList: ObservableList<PageKey> = FXCollections.observableArrayList()

    constructor(file: File) : this(file.inputStream())

    init {
        document = PDDocument.load(pdfInputStream)
        updatePagesList()
    }

    companion object {
        const val DPI = 72.0 //default dpi for pdf
    }

    //no checks for index bounds here!
    override fun setPageRotation(pageNumber: Int, rotationAngle: Double) {
        val pageKey = pagesList[pageNumber]
        pagesList[pageNumber] = pageKey.copy(pageNumber = pageNumber, rotationAngle = rotationAngle)
    }

    override fun getPageRotation(pageNumber: Int): PageKey {
        return pagesList[pageNumber]
    }

    override fun setViewport(pageNumber: Int, viewport: Rectangle2D?) {
        pagesList.getOrNull(pageNumber)?.let {
            pagesList.set(pageNumber, it.copy(viewport = viewport))
        }
    }

    override val numberOfPages: Int
        get() = document.numberOfPages

    override fun isLandscape(pageNumber: Int): Boolean {
        val page = document.getPage(pageNumber)
        val rotationAngle = pagesList[pageNumber].rotationAngle
        val cropBox = page.cropBox
        return if (rotationAngle % 180 == 0.0) {
            cropBox.height < cropBox.width
        } else {
            cropBox.width < cropBox.height
        }
    }

    override fun renderPage(pageNumber: Int, scale: Float, rotationAngle: Double, useCache: Boolean): BufferedImage {
        //try cache first
        var image = if (useCache) {
            imageCache[PageKey(pageNumber, rotationAngle)]
        } else {
            null
        }
        if (image == null) { //render
            document.getPage(pageNumber).rotation = rotationAngle.toInt() //set rotation
            val renderer = PDFRenderer(document)
            image = renderer.renderImage(pageNumber, scale, ImageType.ARGB, RenderDestination.VIEW)
            if (useCache) {
                imageCache[PageKey(pageNumber, rotationAngle)] = image
            }
        }
        return image!!
    }

    override fun close() {
        document.close()
    }

    //create entries for all pages
    private fun updatePagesList() {
        val pages = 0..<(document.numberOfPages)
        val pageKeys = pages.map { PageKey(it, 0.0) }
        pagesList.addAll(pageKeys)
    }

}
