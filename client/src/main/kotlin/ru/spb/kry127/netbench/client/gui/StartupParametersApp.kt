package ru.spb.kry127.netbench.client.gui

import javafx.application.Application
import javafx.scene.Scene

import javafx.fxml.FXMLLoader

import javafx.scene.Parent
import javafx.stage.Stage


class StartupParametersApp : Application() {
    override fun start(stage: Stage) {
        val root = FXMLLoader.load<Parent>(javaClass.getResource("startup_parameters.fxml"))
        val scene = Scene(root, 600.0, 400.0)
        stage.setTitle("NetBench utility")
        stage.setScene(scene)
        stage.show()
    }
}