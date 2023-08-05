package com.confinitum.viewer

import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.stage.Stage

class PdfViewerApplication : Application() {
    override fun start(stage: Stage) {
        val fxmlLoader = FXMLLoader(PdfViewerApplication::class.java.getResource("pdf-view.fxml"))
        val scene = Scene(fxmlLoader.load(), 1200.0, 1400.0)
        stage.title = "PDF Viewer"
        stage.scene = scene
        stage.show()
    }
}

fun main() {
    Application.launch(PdfViewerApplication::class.java)
}