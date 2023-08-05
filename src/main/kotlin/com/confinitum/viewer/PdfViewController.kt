package com.confinitum.viewer

import com.confinitum.pdfview.PDFView
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.layout.BorderPane
import javafx.stage.FileChooser

class PdfViewController {

    private val chooser = FileChooser()
    private val pdfView = PDFView()

    @FXML
    private lateinit var borderPane: BorderPane

    fun initialize() {
        //setup file open dialog
        chooser.setTitle("Load PDF File")
        val filter = FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        chooser.getExtensionFilters().add(filter)
        chooser.setSelectedExtensionFilter(filter)

        //open sample.pdf
        try {
            this.javaClass.getResourceAsStream("/sample.pdf")?.let { pdfView.load(it) }
        } catch (e: Exception) {
            //ignore
        }
        //setup ui
        borderPane.center = pdfView

    }

    @FXML
    fun openAction() {
        chooser.showOpenDialog(pdfView.getScene().getWindow())?.let {
            pdfView.load(it)
        }
    }

    @FXML
    fun closeAction() {
        pdfView.unload()
        Platform.exit()
    }
}