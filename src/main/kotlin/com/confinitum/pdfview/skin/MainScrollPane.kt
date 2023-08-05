package com.confinitum.pdfview.skin

import com.confinitum.pdfview.base.PDFBoxDocument
import com.confinitum.pdfview.base.RenderService
import javafx.animation.RotateTransition
import javafx.beans.property.SimpleDoubleProperty
import javafx.geometry.Point2D
import javafx.geometry.Rectangle2D
import javafx.scene.control.ScrollPane
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.Pane
import javafx.stage.Screen
import javafx.util.Duration
import kotlin.math.max


internal class MainScrollPane(private val pdfViewSkin: PDFViewSkin) : ScrollPane() {
    private val mainAreaRenderService: RenderService = RenderService(pdfViewSkin, false)

    //Nodes
    private val imageView: ImageView
    private val waitImage: ImageView
    private val pane: Pane

    //scaling constants
    private val screenScale: Double
    private val imageScale = SimpleDoubleProperty(1.0)

    //amount of scrolling by pushing keys
    private val keyScroll: Double

    //mouse pointer when zooming
    private var zoomMousePointer: Point2D? = null

    companion object {
        private const val ZOOM_DELTA = 0.1
    }

    init {
        val pdfView = pdfViewSkin.skinnable
        isPannable = true

        //calculate scaling
        val screenDpi = Screen.getPrimary().dpi
        screenScale = screenDpi / PDFBoxDocument.DPI
        imageScale.bind(pdfView.pageRenderDpiProperty().divide(PDFBoxDocument.DPI))

        keyScroll = 0.5 * screenDpi

        // setup imageview
        imageView = ImageView().also {
            it.isPreserveRatio = true
            it.isCache = false
            it.imageProperty().addListener { _, _, n ->
                n?.let { image -> layoutImage(image) }
            }
        }
        //setup processing indicator
        waitImage = ImageView().also {
            it.image = Image(javaClass.getResource("/wait12t.gif")?.toExternalForm())
            it.isPreserveRatio = true
            it.isManaged = false
            it.isVisible = false
        }

        // setup rendering service
        with(mainAreaRenderService) {
            scaleProperty().bind(imageScale)
            pageProperty().bind(pdfView.pageProperty())
            rotationProperty().bind(pdfView.pageRotationProperty())
            valueProperty().addListener { _, _, n ->
                n?.let { image ->
                    imageView.image = image
                }
            }
            //indicate processing
            val wait = RotateTransition(Duration.millis(1800.0), waitImage).also {
                it.byAngle = 360.0
                it.cycleCount = 12
                it.isAutoReverse = false
            }
            //rendering is starting
            setOnScheduled { _ ->
                val mid = Point2D(viewportBounds.width / 2 - 30, viewportBounds.height / 2 - 30)
                val offset = getViewportOffset().add(mid)
                waitImage.relocate(offset.x, offset.y)
                waitImage.isVisible = true
                imageView.opacity = 0.3
                wait.play()
            }
            //rendering is done
            setOnSucceeded { _ ->
                // when scrolling by keyboard, set scrollbar position on new page
                when (pdfViewSkin.requestedVValue) {
                    PostScroll.UP -> vvalue = 1.0
                    PostScroll.DOWN -> vvalue = 0.0
                    else -> {}
                }
                pdfViewSkin.requestedVValue = PostScroll.NONE
                //remove indicator
                wait.stop()
                waitImage.isVisible = false
                imageView.opacity = 1.0
            }
        }

        // wrap imageView in Pane
        pane = Pane(imageView, waitImage)
        pane.styleClass.addAll("image-view-wrapper")
        content = pane

        //bind properties
        pdfView.fitVerticalProperty().addListener { _, _, n ->
            if (n) {
                pdfView.setFitHorizontal(false)
                fitHeight()
            }
        }

        pdfView.fitHorizontalProperty().addListener { _, _, n ->
            if (n) {
                pdfView.setFitVertical(false)
                fitWidth()
            }
        }

        pdfView.zoomFactorProperty().addListener { _, o, n ->
            val mousePointer = zoomMousePointer ?: Point2D(width / 2, height / 2)
            zoom(o.toDouble(), n.toDouble(), mousePointer)
        }

        //mouse scroll-wheel
        content.setOnScroll {
            if (it.isInertia) return@setOnScroll
            if (it.isShortcutDown) { //zoom
                if (isNotZoomable) {
                    pdfView.setFitVertical(false)
                    pdfView.setFitHorizontal(false)
                }
                val delta = if (it.deltaY > 0) ZOOM_DELTA else -ZOOM_DELTA // zoom delta
                zoomMousePointer = sceneToLocal(Point2D(it.sceneX, it.sceneY)) //where is the mouse pointing to
                val old = pdfView.getZoomFactor()
                pdfView.setZoomFactor(old + delta)
                it.consume()
            } else { // just scroll
                val deltaY = it.deltaY * 6
                vvalue -= deltaY / content.layoutBounds.height
                it.consume()
            }
        }
        //scroll with keyboard
        setOnKeyPressed { evt: KeyEvent ->
            when (evt.code) {
                KeyCode.PAGE_UP -> if (vvalue > 0.0) {
                    vvalue -= height
                } else {
                    pdfViewSkin.requestedVValue = PostScroll.UP
                    pdfView.gotoPreviousPage()
                }

                KeyCode.PAGE_DOWN -> if (vvalue < vmax) {
                    vvalue += height
                } else {
                    pdfViewSkin.requestedVValue = PostScroll.DOWN
                    pdfView.gotoNextPage()
                }

                KeyCode.HOME -> {
                    vvalue = 0.0
                }

                KeyCode.END -> {
                    vvalue = vmax
                }

                KeyCode.LEFT -> {
                    if (hvalue > 0.0) hvalue -= keyScroll
                }

                KeyCode.RIGHT -> {
                    if (hvalue < hmax) hvalue += keyScroll
                }

                KeyCode.DOWN -> {
                    if (vvalue < vmax) vvalue += keyScroll
                }

                KeyCode.UP -> {
                    if (vvalue > 0) vvalue -= keyScroll
                }

                else -> Unit
            }
        }

        //window size changed
        viewportBoundsProperty().addListener { _, _, _ ->
            this.fitWidthOrHeight()
            this.calculateViewport()
        }
        //scroll changed
        vvalueProperty().addListener { _, _, _ -> calculateViewport() }
        hvalueProperty().addListener { _, _, _ -> calculateViewport() }
    }

    /**
     * rerender current page
     */
    fun reload() {
        mainAreaRenderService.restart()
    }

    /**
     * zoom image to given factor
     * @param oldZoom current/old zoom factor
     * @param newZoom new zoom factor
     * @param mousePointer where is the pointer; use this as zoom focal point
     */
    private fun zoom(oldZoom: Double, newZoom: Double, mousePointer: Point2D) {
        val mouseOffset = getViewportOffset().add(mousePointer)
        val imageOffset = mouseOffset.multiply(1 / oldZoom) //mouse in image

        val newMouseOffset = imageOffset.multiply(newZoom) //mouse in view after zoom
        val offset = newMouseOffset.subtract(mousePointer) //offset (top-left corner)

        val scrollValue = getViewportScroll(offset)
        layoutImage(scrollValue = scrollValue)
    }

    //calculate image bounds with regard to zoom and scaling
    private fun layoutImage(image: Image = imageView.image, scrollValue: Point2D = Point2D(this.hvalue, this.vvalue)) {
        imageView.let {
            it.fitWidth = (image.width / imageScale.get()) * screenScale * pdfViewSkin.skinnable.getZoomFactor()
            it.fitHeight = (image.height / imageScale.get()) * screenScale * pdfViewSkin.skinnable.getZoomFactor()

            this.hvalue = scrollValue.x
            this.vvalue = scrollValue.y
        }
        fitWidthOrHeight()
    }

    //recalculate zoom if fitWidth or fitHeight is enabled
    private fun fitWidthOrHeight() {
        if (pdfViewSkin.skinnable.isFitVertical()) {
            fitHeight()
        }
        if (pdfViewSkin.skinnable.isFitHorizontal()) {
            fitWidth()
        }
    }

    private var zoomLock = false //prevents reentry and stack overflow
    private fun fitWidth() {
        if (zoomLock) return
        zoomLock = true
        try {
            val width = imageView.fitWidth / pdfViewSkin.skinnable.getZoomFactor() + 10
            val zoom = this.width / width
            pdfViewSkin.skinnable.setZoomFactor(zoom)
        } finally {
            zoomLock = false
        }
    }

    private fun fitHeight() {
        if (zoomLock) return
        zoomLock = true
        try {
            val height = imageView.fitHeight / pdfViewSkin.skinnable.getZoomFactor() + 10
            val zoom = this.height / height
            pdfViewSkin.skinnable.setZoomFactor(zoom)
        } finally {
            zoomLock = false
        }
    }

    private val isNotZoomable: Boolean
        get() = pdfViewSkin.skinnable.isFitHorizontal() || pdfViewSkin.skinnable.isFitVertical()

    //calculate left-top offset from current scroll
    private fun getViewportOffset(): Point2D {
        val offContentWidth = max(0.0, content.layoutBounds.width - viewportBounds.width)
        val offContentHeight = max(0.0, content.layoutBounds.height - viewportBounds.height)
        return Point2D(
            offContentWidth * hvalue,
            offContentHeight * vvalue,
        )
    }

    //calculate scroll from given offset
    private fun getViewportScroll(offset: Point2D): Point2D {
        val offContentWidth = max(0.0, content.layoutBounds.width - viewportBounds.width)
        val offContentHeight = max(0.0, content.layoutBounds.height - viewportBounds.height)
        return Point2D(
            if (offContentWidth == 0.0) 0.0 else offset.x / offContentWidth,
            if (offContentHeight == 0.0) 0.0 else offset.y / offContentHeight
        )
    }

    // calculate viewport in relative coordinates (percent)
    private fun calculateViewport() {
        val offset = getViewportOffset()
        pdfViewSkin.currentViewport.set(
            Rectangle2D(
                offset.x / imageView.fitWidth,
                offset.y / imageView.fitHeight,
                viewportBounds.width / imageView.fitWidth,
                viewportBounds.height / imageView.fitHeight
            )
        )
    }

    // set viewport to given rectangle
    fun setViewport(vp: Rectangle2D) {
        val offset = Point2D(vp.minX * imageView.fitWidth, vp.minY * imageView.fitHeight)
        val scroll = getViewportScroll(offset)
        hvalue = scroll.x
        vvalue = scroll.y
    }
}

