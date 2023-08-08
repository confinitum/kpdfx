package de.confinitum.pdfview.base

import de.confinitum.pdfview.skin.PDFViewSkin
import javafx.beans.InvalidationListener
import javafx.beans.property.*
import javafx.concurrent.Service
import javafx.concurrent.Task
import javafx.embed.swing.SwingFXUtils
import javafx.scene.image.Image
import java.util.concurrent.Executor

/**
 * Service for rendering pdf pages.
 */
class RenderService(
    val pdfViewSkin: PDFViewSkin, val thumbnailRenderer: Boolean,
    exec: Executor = pdfViewSkin.skinnable.getRenderExecutor()
) : Service<Image>() {
    private val _scale: FloatProperty = SimpleFloatProperty(1f)
    fun scaleProperty() = _scale
    val scale
        get() = _scale.get()

    private val _rotation: DoubleProperty = SimpleDoubleProperty(0.0)
    fun rotationProperty() = _rotation
    var rotation
        get() = _rotation.get()
        set(value) {
            _rotation.set(value)
        }

    // initialize with -1 to ensure property fires
    private val _page: IntegerProperty = SimpleIntegerProperty(-1)
    fun pageProperty() = _page
    var page
        get() = _page.get()
        set(value) {
            _page.set(value)
        }

    init {
        executor = exec
        val restartListener = InvalidationListener { restart() }
        pageProperty().addListener(restartListener)
        scaleProperty().addListener(restartListener)
        rotationProperty().addListener(restartListener)
    }

    override fun createTask(): Task<Image> {
        return RenderTask(pdfViewSkin, thumbnailRenderer, page, scale, rotation)
    }

}

class RenderTask(
    val pdfViewSkin: PDFViewSkin,
    val thumbnail: Boolean,
    val page: Int,
    val scale: Float,
    val rotation: Double
) : Task<Image>() {

    override fun call(): Image? {
        val pdfView = pdfViewSkin.skinnable
        if (page >= 0 && page < (pdfView.getDocument()?.numberOfPages ?: 0)) {
            return renderPDFPage(page, scale, rotation, pdfView.isCacheThumbnails() && thumbnail)
        }
        return null
    }

    private fun renderPDFPage(pageNumber: Int, scale: Float, rotation: Double, useCache: Boolean): Image? {
        val bufferedImage = pdfViewSkin.skinnable.getDocument()?.renderPage(pageNumber, scale, rotation, useCache)
        if (isCancelled || bufferedImage == null) return null
        return SwingFXUtils.toFXImage(bufferedImage, null)
    }

}

