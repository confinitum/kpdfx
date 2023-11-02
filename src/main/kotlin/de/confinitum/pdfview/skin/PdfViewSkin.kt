package de.confinitum.pdfview.skin

import de.confinitum.pdfview.PDFView
import de.confinitum.pdfview.base.PageKey
import de.confinitum.pdfview.base.maybeScrollTo
import javafx.beans.binding.Bindings
import javafx.beans.property.DoubleProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ChangeListener
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.geometry.Rectangle2D
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.*
import org.kordamp.ikonli.javafx.FontIcon
import org.kordamp.ikonli.materialdesign2.MaterialDesignA
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import kotlin.math.max
import kotlin.math.min

enum class PostScroll {
    NONE, UP, DOWN
}

class PDFViewSkin(private val view: PDFView) : SkinBase<PDFView>(view) {
    private var mainArea: MainScrollPane
    var requestedVValue = PostScroll.NONE
    private val thumbnailWidth: DoubleProperty = SimpleDoubleProperty(0.0) //minimum TN width
    private var resetThumbnailWidth = false

    //current viewport bounds relative to image size eg. all values between 0.0 and 1.0
    val currentViewport: ObjectProperty<Rectangle2D> = SimpleObjectProperty(Rectangle2D(0.0, 0.0, 1.0, 1.0))

    init {
        val thumbnailListView = ListView<PageKey>()
        with(thumbnailListView) {
            styleClass.add("thumbnail-list-view")
            placeholder = null
            setCellFactory { _ ->
                ThumbnailListCell(this@PDFViewSkin)
            }
            prefWidthProperty().bind(thumbnailWidth.add(40))
            minWidth = Region.USE_PREF_SIZE

            selectionModel.selectedItemProperty().addListener { _, _, s ->
                s?.let {
                    view.setPage(it.pageNumber)
                }
            }
            view.pageProperty().addListener { _ ->
                selectionModel.select(view.getPage())
                maybeScrollTo(this, selectionModel.selectedItem)
            }
            //new document -> reset selection
            itemsProperty().addListener { _, _, n ->
                n?.let {
                    selectionModel.select(0)
                }
            }

            requestFocus()
        }

        val toolBar = createToolBar().also {
            it.stylesheets.add(PDFView::class.java.getResource("/pdf-view.css")?.toExternalForm() ?: "")
            it.visibleProperty().bind(view.showToolBarProperty())
            it.managedProperty().bind(view.showToolBarProperty())
        }

        mainArea = MainScrollPane(this).also {
            VBox.setVgrow(it, Priority.ALWAYS)
        }

        val rightSide = VBox(mainArea).also {
            it.isFillWidth = true
        }

        val leftSide = HBox(thumbnailListView).also {
            it.isFillHeight = true
            it.visibleProperty().bind(view.showThumbnailsProperty())
            it.managedProperty().bind(view.showThumbnailsProperty())
        }

        val borderPane = BorderPane().also {
            it.top = toolBar
            it.left = leftSide
            it.center = rightSide
            it.isFocusTraversable = false
        }
        children.add(borderPane)

        //new document opened
        view.documentProperty().addListener { _, _, n ->
            n?.let {
                resetThumbnailWidth = true
                thumbnailListView.items = it.pagesList
                thumbnailListView.refresh()
                mainArea.reload()
            }
        }

        //preloaded image
        if (thumbnailListView.items.isEmpty()) {
            view.getDocument()?.pagesList.let {
                thumbnailListView.items = it
            }
        }

        //move thumbnail viewport indicator to selected page
        view.pageProperty().addListener { _, o, n ->
            switchViewport(o.toInt(), n.toInt())
        }
        //push viewport changes to thumbnail
        currentViewport.addListener { _, _, _ ->
            switchViewport(null, view.getPage())
        }
    }

    //set viewport
    fun setViewport(rectangle: Rectangle2D) {
        mainArea.setViewport(rectangle)
    }

    //recalculate minimum width for thumbnails
    fun registerThumbnailWidth(w: Double) {
        if (resetThumbnailWidth) {
            val diff = thumbnailWidth.get() - w
            if (diff > 4 || diff < 0) { // suppress micro adjustments
                thumbnailWidth.set(w + 2)
            }
            resetThumbnailWidth = false
        } else {
            thumbnailWidth.set(max(thumbnailWidth.get(), w))
        }
    }

    //move viewport indicator to newPage
    private fun switchViewport(oldPage: Int?, newPage: Int?) {
        oldPage?.let {
            view.getDocument()?.setViewport(it, null)
        }
        newPage?.let {
            val vp = currentViewport.get()
            val vpn = Rectangle2D(
                max(vp.minX, 0.0),
                max(vp.minY, 0.0),
                min(vp.width, 1.0),
                min(vp.height, 1.0)
            )
            if (vpn != Rectangle2D(0.0, 0.0, 1.0, 1.0)) {
                view.getDocument()?.setViewport(it, vpn)
            } else {
                //if viewport contains full image then disable it
                view.getDocument()?.setViewport(it, null)
            }
        }
    }

    private fun createToolBar(): ToolBar {
        val view = skinnable
        val config = view!!.getToolbarConfig()
        val nodes = mutableListOf<Node>()

        //thumbnails
        if (config.thumbnails) {
            val showThumbnails = ToggleButton().also {
                it.graphic = FontIcon(MaterialDesignA.ANIMATION_OUTLINE)
                it.styleClass.addAll("tool-bar-button", "show-thumbnails-button")
                it.tooltip = Tooltip("show/hide thumbnails")
                it.selectedProperty().bindBidirectional(view.showThumbnailsProperty())
            }
            nodes.add(showThumbnails)
            nodes.add(Separator(Orientation.VERTICAL))
        }

        // fit buttons
        if (config.pageFit) {
            val showVertical = ToggleButton().also {
                it.graphic = FontIcon(MaterialDesignA.ARROW_EXPAND_VERTICAL)
                it.styleClass.addAll("tool-bar-button", "show-all-button")
                it.tooltip = Tooltip("Show all / whole page")
                view.fitVerticalProperty().bindBidirectional(it.selectedProperty())
            }

            val showHorizontal = ToggleButton().also {
                it.graphic = FontIcon(MaterialDesignA.ARROW_EXPAND_HORIZONTAL)
                it.styleClass.addAll("tool-bar-button", "show-all-button")
                it.tooltip = Tooltip("fit horizontally")
                view.fitHorizontalProperty().bindBidirectional(it.selectedProperty())
            }
            nodes.add(showVertical)
            nodes.add(showHorizontal)
            nodes.add(Separator(Orientation.VERTICAL))
        }

        // zoom slider
        if (config.zooming) {
            val zoomSlider = Slider()
            with(zoomSlider) {
                minProperty().set(0.0)
                maxProperty().bind(view.maxZoomFactorProperty())
                value = view.getZoomFactor()
                isShowTickMarks = true
                majorTickUnit = 0.5
                isShowTickLabels = true

                var zoomListener: ChangeListener<Number> = ChangeListener { _, _, _ -> }
                val valueListener: ChangeListener<Number> = ChangeListener { _, _, newValue ->
                    view.zoomFactorProperty().removeListener(zoomListener)
                    view.setFitHorizontal(false)
                    view.setFitVertical(false)
                    view.setZoomFactor(newValue.toDouble())
                    view.zoomFactorProperty().addListener(zoomListener)
                }
                zoomListener = ChangeListener<Number> { _, _, n ->
                    valueProperty().removeListener(valueListener)
                    value = n.toDouble()
                    valueProperty().addListener(valueListener)
                }
                valueProperty().addListener(valueListener)
                view.zoomFactorProperty().addListener(zoomListener)
            }
            val zoomLabel = Label("Zoom")
            nodes.add(zoomLabel)
            nodes.add(zoomSlider)
            nodes.add(Separator(Orientation.VERTICAL))
        }

        // paging
        if (config.pages) {
            val goLeft = Button().also {
                it.graphic = FontIcon(MaterialDesignC.CHEVRON_LEFT)
                it.tooltip = Tooltip("Show previous page")
                it.onAction = EventHandler { _: ActionEvent? -> view.gotoPreviousPage() }
                it.styleClass.addAll("tool-bar-button", "previous-page-button")
                it.disableProperty().bind(
                    Bindings.createBooleanBinding(
                        { view.getPage() <= 0 },
                        view.pageProperty(),
                        view.documentProperty()
                    )
                )
            }
            val goRight = Button().also {
                it.graphic = FontIcon(MaterialDesignC.CHEVRON_RIGHT)
                it.tooltip = Tooltip("Show next page")
                it.onAction = EventHandler { _: ActionEvent? -> view.gotoNextPage() }
                it.styleClass.addAll("tool-bar-button", "next-page-button")
                it.disableProperty().bind(Bindings.createBooleanBinding({
                    view.getDocument() == null || view.getDocument()!!.numberOfPages <= view.getPage() + 1
                }, view.pageProperty(), view.documentProperty()))
            }
            val pageField = TextField().also {
                it.tooltip = Tooltip("Current page number")
                it.styleClass.add("page-field")
                it.maxHeightProperty().bind(goLeft.heightProperty())
                it.alignment = Pos.CENTER
                updateCurrentPageNumber(view, it)
            }
            view.pageProperty().addListener { _ -> updateCurrentPageNumber(view, pageField) }
            pageField.textProperty().addListener { _, _, n ->
                try {
                    n?.let {
                        view.setPage(it.toInt() - 1)
                    }
                } catch (ne: NumberFormatException) {
                    //ignore
                }
            }
            val totalPages = Button().also {
                it.tooltip = Tooltip("Total number of pages")
                it.styleClass.add("page-number-button")
                it.maxHeightProperty().bind(goLeft.heightProperty())
                it.alignment = Pos.CENTER
                it.onAction = EventHandler { _: ActionEvent? -> view.gotoLastPage() }
                it.isFocusTraversable = false
                updateTotalPagesNumber(it)
            }
            view.documentProperty().addListener { _, _, _ -> updateTotalPagesNumber(totalPages) }
            with(HBox(goLeft, pageField, totalPages, goRight)) {
                isFillHeight = true
                disableProperty().bind(view.documentProperty().isNull())
                styleClass.add("page-control")
                alignment = Pos.CENTER_LEFT
                nodes.add(this)
            }
            nodes.add(Separator(Orientation.VERTICAL))
        }

        // rotate buttons
        if (config.rotation) {
            val rotateLeft = Button().also {
                it.styleClass.addAll("tool-bar-button", "rotate-left")
                it.tooltip = Tooltip("Rotate page left")
                it.graphic = FontIcon(MaterialDesignR.ROTATE_LEFT)
                it.onAction = EventHandler { _: ActionEvent? -> view.rotateLeft() }
            }
            val rotateRight = Button().also {
                it.styleClass.addAll("tool-bar-button", "rotate-right")
                it.tooltip = Tooltip("Rotate page right")
                it.graphic = FontIcon(MaterialDesignR.ROTATE_RIGHT)
                it.onAction = EventHandler { _: ActionEvent? -> view.rotateRight() }
            }
            nodes.add(rotateLeft)
            nodes.add(rotateRight)
        }

        // toolbar
        return ToolBar(*nodes.toTypedArray())
    }

    private fun updateCurrentPageNumber(view: PDFView?, pageField: TextField) {
        view?.getDocument()?.let {
            pageField.text = (view.getPage() + 1).toString()
        } ?: {
            pageField.text = "0"
        }
    }

    private fun updateTotalPagesNumber(totalPagesButton: Button) {
        skinnable?.getDocument()?.let {
            totalPagesButton.text = "/ " + it.numberOfPages
        } ?: {
            totalPagesButton.text = "/ " + 0
        }
    }

}


