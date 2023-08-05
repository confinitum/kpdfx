package com.confinitum.pdfview

/**
 * No idiomatic kotlin here because of javafx conventions
 */

import com.confinitum.pdfview.base.Document
import com.confinitum.pdfview.base.PDFBoxDocument
import com.confinitum.pdfview.base.ToolbarConfig
import com.confinitum.pdfview.base.getExecutor
import com.confinitum.pdfview.skin.PDFViewSkin
import javafx.beans.property.*
import javafx.scene.control.Control
import javafx.scene.control.Skin
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executor
import java.util.function.Supplier
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A PDF viewer based on Apache PDFBox. The view shows thumbnails
 * on the left and the full page on the right. The user can zoom in,
 * rotate, fit size, etc...
 */
class PDFView : Control() {
    override fun createDefaultSkin(): Skin<*> {
        return PDFViewSkin(this)
    }

    override fun getUserAgentStylesheet(): String {
        return javaClass.getResource("/pdf-view.css")?.toExternalForm() ?: throw Exception("not found")
    }

    /**
     * Configuration for Toolbar
     */
    private val toolbarConfig: ObjectProperty<ToolbarConfig> =
        SimpleObjectProperty(this, "toolbarConfig", ToolbarConfig())

    fun getToolbarConfig(): ToolbarConfig {
        return toolbarConfig.get()
    }

    fun toolbarConfigProperty(): ObjectProperty<ToolbarConfig> {
        return toolbarConfig
    }

    fun setToolbarConfig(config: ToolbarConfig) {
        toolbarConfig.set(config)
    }

    /**
     * A flag used to control whether the view will display a thumbnail version of the pages
     * on the left-hand side.
     */
    private val showThumbnails: BooleanProperty = SimpleBooleanProperty(this, "showThumbnails", true)
    fun isShowThumbnails(): Boolean {
        return showThumbnails.get()
    }

    fun showThumbnailsProperty(): BooleanProperty {
        return showThumbnails
    }

    fun setShowThumbnails(showThumbnails: Boolean) {
        this.showThumbnails.set(showThumbnails)
    }

    /**
     * A flag used to control whether the view will include a toolbar with zoom, search, rotation
     * controls.
     */
    private val showToolBar: BooleanProperty = SimpleBooleanProperty(this, "showToolBar", true)
    fun isShowToolBar(): Boolean {
        return showToolBar.get()
    }

    fun showToolBarProperty(): BooleanProperty {
        return showToolBar
    }

    fun setShowToolBar(showToolBar: Boolean) {
        this.showToolBar.set(showToolBar)
    }

    /**
     * Caching thumbnails can be useful for low powered systems with enough memory. The default value
     * is "true". When set to "true" each thumbnail image will be added to a hashmap cache, hence making it
     * necessary to only render once.
     */
    private val cacheThumbnails: BooleanProperty = SimpleBooleanProperty(this, "cacheThumbnails", true)
    fun isCacheThumbnails(): Boolean {
        return cacheThumbnails.get()
    }

    fun cacheThumbnailsProperty(): BooleanProperty {
        return cacheThumbnails
    }

    fun setCacheThumbnails(cacheThumbnails: Boolean) {
        this.cacheThumbnails.set(cacheThumbnails)
    }

    /**
     * Sets the lower bounds for zoom operations. The default value is "0.2" (or 20%)
     */
    private val minZoomFactor: DoubleProperty = SimpleDoubleProperty(this, "minZoomFactor", 0.2)
    fun getMinZoomFactor(): Double {
        return minZoomFactor.get()
    }

    fun minZoomFactorProperty(): DoubleProperty {
        return minZoomFactor
    }

    fun setMinZoomFactor(minZoomFactor: Double) {
        this.minZoomFactor.set(minZoomFactor)
    }

    /**
     * Sets the upper bounds for zoom operations. The default value is 3.0 (or 300%)
     */
    private val maxZoomFactor: DoubleProperty = SimpleDoubleProperty(this, "maxZoomFactor", 3.0)
    fun getMaxZoomFactor(): Double {
        return maxZoomFactor.get()
    }

    fun maxZoomFactorProperty(): DoubleProperty {
        return maxZoomFactor
    }

    fun setMaxZoomFactor(maxZoomFactor: Double) {
        this.maxZoomFactor.set(maxZoomFactor)
    }

    /**
     * The current zoom factor. The default value is 1.0 (100%).
     */
    private val zoomFactor: DoubleProperty = object : SimpleDoubleProperty(this, "zoomFactor", 1.0) {
        override fun set(newValue: Double) {
            super.set(min(max(newValue, getMinZoomFactor()), getMaxZoomFactor()))
        }
    }

    fun getZoomFactor(): Double {
        return zoomFactor.get()
    }

    fun zoomFactorProperty(): DoubleProperty {
        return zoomFactor
    }

    fun setZoomFactor(zoomFactor: Double) {
        this.zoomFactor.set(zoomFactor)
    }

    /**
     * The page rotation in degrees. Supported values are only "0", "90", "180", "270", "360", ...
     * multiples of "90".
     */
    private val pageRotation: DoubleProperty = object : SimpleDoubleProperty(this, "pageRotation", 0.0) {
        override fun set(newValue: Double) {
            var rotation = floor(newValue / 90) * 90
            if (rotation < 0) rotation += 360
            if (rotation >= 360) rotation -= 360
            super.set(rotation % 360.0)

            getDocument()?.setPageRotation(getPage(), rotation)
        }
    }

    fun getPageRotation(): Double {
        return pageRotation.get()
    }

    fun pageRotationProperty(): DoubleProperty {
        return pageRotation
    }

    fun setPageRotation(pageRotation: Double) {
        this.pageRotation.set(pageRotation)
    }

    /**
     * Convenience method to rotate the generated image by -90 degrees.
     */
    fun rotateLeft() {
        setPageRotation(getPageRotation() - 90)
    }

    /**
     * Convenience method to rotate the generated image by +90 degrees.
     */
    fun rotateRight() {
        setPageRotation(getPageRotation() + 90)
    }

    /**
     * Stores the number of the currently showing page.
     */
    private val page: IntegerProperty = object : SimpleIntegerProperty(this, "page") {
        override fun set(newValue: Int) {
            super.set(newValue)

            getDocument()?.getPageRotation(newValue)?.let {
                setPageRotation(it.rotationAngle)
            }
        }
    }

    fun getPage(): Int {
        return pageProperty().get()
    }

    fun pageProperty(): IntegerProperty {
        return page
    }

    fun setPage(page: Int) {
        this.pageProperty().set(page)
    }

    /**
     * Convenience method to show the next page. This simply increases the [.pageProperty] value
     * by 1.
     *
     * @return true if the operation actually did cause a page change
     */
    fun gotoNextPage(): Boolean {
        val currentPage = getPage()
        setPage(min(getDocument()!!.numberOfPages - 1, getPage() + 1))
        return currentPage != getPage()
    }

    /**
     * Convenience method to show the previous page. This simply decreases the [.pageProperty] value
     * by 1.
     *
     * @return true if the operation actually did cause a page change
     */
    fun gotoPreviousPage(): Boolean {
        val currentPage = getPage()
        setPage(max(0, getPage() - 1))
        return currentPage != getPage()
    }

    /**
     * Convenience method to show the last page.
     *
     * @return true if the operation actually did cause a page change
     */
    fun gotoLastPage(): Boolean {
        val currentPage = getPage()
        setPage(getDocument()!!.numberOfPages - 1)
        return currentPage != getPage()
    }

    /**
     * A flag that controls whether we always want to show the entire height of page. If "true" then the page
     * will be constantly resized to fit the viewport of the scroll pane in which it is showing.
     */
    private val fitVertical: BooleanProperty = SimpleBooleanProperty(this, "fitVertical", false)
    fun isFitVertical(): Boolean {
        return fitVertical.get()
    }

    fun fitVerticalProperty(): BooleanProperty {
        return fitVertical
    }

    fun setFitVertical(fitVertical: Boolean) {
        this.fitVertical.set(fitVertical)
    }

    /**
     * A flag that controls whether we always want to show the entire width of page. If "true" then the page
     * will be constantly resized to fit the viewport of the scroll pane in which it is showing.
     */
    private val fitHorizontal: BooleanProperty = SimpleBooleanProperty(this, "fitHorizontal", false)
    fun isFitHorizontal(): Boolean {
        return fitHorizontal.get()
    }

    fun fitHorizontalProperty(): BooleanProperty {
        return fitHorizontal
    }

    fun setFitHorizontal(fitHorizontal: Boolean) {
        this.fitHorizontal.set(fitHorizontal)
    }

    /**
     * The resolution in dpi at which the thumbnails will be rendered. The default value is 72
     */
    private val thumbnailRenderDpi: FloatProperty = SimpleFloatProperty(this, "thumbnailScale", 72f)
    fun getThumbnailRenderDpi(): Float {
        return thumbnailRenderDpi.get()
    }

    fun thumbnailRenderDpiProperty(): FloatProperty {
        return thumbnailRenderDpi
    }

    fun setThumbnailRenderDpi(thumbnailRenderDpi: Float) {
        this.thumbnailRenderDpi.set(thumbnailRenderDpi)
    }

    /**
     * The resolution in dpi at which the main page will be rendered. The default value is 300.
     * The value has direct impact on the size of the images being generated and the memory requirements.
     * Keep low on low powered / low resolution systems and high on large systems with hires displays.
     */
    private val pageRenderDpi: FloatProperty = SimpleFloatProperty(this, "pageRenderDpi", 300f)
    fun getPageRenderDpi(): Float {
        return pageRenderDpi.get()
    }

    fun pageRenderDpiProperty(): FloatProperty {
        return pageRenderDpi
    }

    fun setPageRenderDpi(pageRenderDpi: Float) {
        this.pageRenderDpi.set(pageRenderDpi)
    }

    /**
     * The size used for the images displayed in the thumbnail view. The default value is "200".
     */
    private val thumbnailSize: DoubleProperty = SimpleDoubleProperty(this, "thumbnailSize", 200.0)
    fun getThumbnailSize(): Double {
        return thumbnailSize.get()
    }

    fun thumbnailSizeProperty(): DoubleProperty {
        return thumbnailSize
    }

    fun setThumbnailSize(thumbnailSize: Double) {
        this.thumbnailSize.set(thumbnailSize)
    }

    /**
     * The currently loaded and displayed PDF document.
     */
    private val document: ObjectProperty<Document?> = SimpleObjectProperty(this, "document")
    fun documentProperty(): ObjectProperty<Document?> {
        return document
    }

    fun getDocument(): Document? {
        return document.get()
    }

    fun setDocument(document: Document?) {
        this.document.set(document)
    }

    /**
     * Use multi threading for rendering the pdf files. Default is off
     * Caution: some parts of PDFBox's renderer aren't thread save (Images?).
     */
    private val multiThreadRendering: BooleanProperty = SimpleBooleanProperty(this, "multiThreadRendering", false)
    fun multiThreadRenderingProperty(): BooleanProperty {
        return multiThreadRendering
    }

    fun getMultiThreadRendering(): Boolean {
        return multiThreadRendering.get()
    }

    fun setMultiThreadRendering(value: Boolean) {
        multiThreadRendering.set(value)
    }

    fun getRenderExecutor(): Executor {
        return getExecutor(getMultiThreadRendering())
    }

    /**
     * Constructs a new view.
     */
    init {
        styleClass.add("pdf-view")
        isFocusTraversable = false
        documentProperty().addListener { _, oldDoc, _ ->
            oldDoc?.close()
        }
    }

    /**
     * Loads the given PDF file.
     *
     * @param file a file containing a PDF document
     * @throws Document.DocumentProcessingException if there is an error while reading/parsing a document.
     */
    fun load(file: File) {
        load { PDFBoxDocument(file) }
    }

    /**
     * Loads the given PDF file.
     *
     * @param stream a stream returning a PDF document
     * @throws Document.DocumentProcessingException if there is an error while reading/parsing a document.
     */
    fun load(stream: InputStream) {
        load { PDFBoxDocument(stream) }
    }

    /**
     * Sets the document retrieved from the given supplier.
     *
     * @param supplier Document supplier.
     * @throws Document.DocumentProcessingException if there is an error while reading/parsing of a document.
     */
    fun load(supplier: Supplier<Document>) {
        setDocument(supplier.get())
    }

    /**
     * Un-loads currently loaded document.
     */
    fun unload() {
        setDocument(null)
        setZoomFactor(1.0)
        rotate = 0.0
    }
}