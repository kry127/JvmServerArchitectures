package ru.spb.kry127.netbench.client

import javafx.application.Application
import javafx.scene.Scene

import javafx.fxml.FXMLLoader

import javafx.scene.Parent
import javafx.stage.Stage


class FXMLExample : Application() {
    override fun start(stage: Stage) {
        val root = FXMLLoader.load<Parent>(javaClass.getResource("Sample.fxml"))
        val scene = Scene(root, 300.0, 275.0)
        stage.setTitle("FXML Welcome")
        stage.setScene(scene)
        stage.show()
    }
}