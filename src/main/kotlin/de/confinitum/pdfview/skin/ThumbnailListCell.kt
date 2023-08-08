package de.confinitum.pdfview.skin

import de.confinitum.pdfview.base.PDFBoxDocument
import de.confinitum.pdfview.base.PageKey
import de.confinitum.pdfview.base.RenderService
import javafx.beans.value.ChangeListener
import javafx.geometry.Point2D
import javafx.geometry.Pos
import javafx.geometry.Rectangle2D
import javafx.scene.Cursor
import javafx.scene.control.ContentDisplay
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.image.ImageView
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.shape.Rectangle
import kotlin.math.max
import kotlin.math.min


class ThumbnailListCell(private val pdfViewSkin: PDFViewSkin) : ListCell<PageKey?>() {
    private val imageView = ImageView()
    private val viewport = Rectangle()
    private val pageNumberLabel = Label()
    private val renderService = RenderService(pdfViewSkin, true)
    private var dragstart: Point2D = Point2D(0.0, 0.0)

    init {
        imageView.isPreserveRatio = true
        with(viewport) {
            isManaged = false
            isVisible = false
            styleClass.add("viewport-indicator")
            cursor = Cursor.OPEN_HAND
            setOnMousePressed { ev ->
                dragstart = sceneToLocal(ev.sceneX, ev.sceneY)
                cursor = Cursor.CLOSED_HAND
                ev.isDragDetect = true
            }
            setOnMouseDragged { ev ->
                val sceneToLocal = sceneToLocal(ev.sceneX, ev.sceneY)
                val drag = sceneToLocal.subtract(dragstart)
                dragstart = sceneToLocal
                moveViewPort(drag)
            }
            setOnMouseReleased { _ ->
                cursor = Cursor.OPEN_HAND
                val vp = Rectangle2D(
                    viewport.x / imageView.layoutBounds.width,
                    viewport.y / imageView.layoutBounds.height,
                    viewport.width / imageView.layoutBounds.width,
                    viewport.height / imageView.layoutBounds.height,
                )
                pdfViewSkin.setViewport(vp)
            }
        }
        val stackPane = StackPane(imageView, viewport).also {
            it.styleClass.add("image-view-wrapper")
            it.maxWidth = USE_PREF_SIZE
            it.visibleProperty().bind(imageView.imageProperty().isNotNull())
        }
        with(pageNumberLabel) {
            styleClass.add("page-number-label")
            visibleProperty().bind(imageView.imageProperty().isNotNull())
        }
        val vBox = VBox(5.0, stackPane, pageNumberLabel).also {
            it.alignment = Pos.CENTER
            it.isFillWidth = true
            it.visibleProperty().bind(emptyProperty().not())
        }
        graphic = vBox
        contentDisplay = ContentDisplay.GRAPHIC_ONLY
        alignment = Pos.CENTER

        //layout on new item
        val itemListener = ChangeListener<PageKey?> { _, _, pageKey ->
            if (pageKey == null) {
                renderService.page = -1
            } else {
                renderService.page = pageKey.pageNumber
                renderService.rotation = pageKey.rotationAngle
                if (pageKey.viewport != null) {
                    viewport.width = pageKey.viewport.width * imageView.layoutBounds.width
                    viewport.height = pageKey.viewport.height * imageView.layoutBounds.height
                    viewport.x = pageKey.viewport.minX * imageView.layoutBounds.width
                    viewport.y = pageKey.viewport.minY * imageView.layoutBounds.height
                    viewport.isVisible = true
                } else {
                    viewport.isVisible = false
                }
            }
        }
        itemProperty().addListener(itemListener)

        with(renderService) {
            scaleProperty().bind(pdfViewSkin.skinnable.thumbnailRenderDpiProperty().divide(PDFBoxDocument.DPI))
            valueProperty().addListener { _, _, image ->
                imageView.image = image
            }
        }
        //recalculate minimum width for thumbnails
        imageView.boundsInParentProperty().addListener { _, _, w ->
            w?.let {
                pdfViewSkin.thumbnailWidth.set(max(pdfViewSkin.thumbnailWidth.get(), w.width))
            }
        }
    }

    override fun updateItem(pageKey: PageKey?, empty: Boolean) {
        super.updateItem(pageKey, empty)
        if (pageKey != null && !empty) {
            //keep same proportion when rotating
            if (pdfViewSkin.skinnable.getDocument()?.isLandscape(pageKey.pageNumber) == true) {
                imageView.fitHeightProperty().unbind()
                imageView.fitWidthProperty().bind(pdfViewSkin.skinnable.thumbnailSizeProperty())
            } else {
                imageView.fitWidthProperty().unbind()
                imageView.fitHeightProperty().bind(pdfViewSkin.skinnable.thumbnailSizeProperty())
            }
            pageNumberLabel.text = (pageKey.pageNumber + 1).toString()
        }
    }

    private fun moveViewPort(offset: Point2D) {
        var x = viewport.x + offset.x
        var y = viewport.y + offset.y
        val mx = imageView.layoutBounds.width - viewport.width
        val my = imageView.layoutBounds.height - viewport.height

        x = min(max(0.0, x), mx)
        y = min(max(0.0, y), my)

        viewport.x = x
        viewport.y = y
    }
}
