package ru.spb.kry127.netbench.client

import javafx.event.ActionEvent
import javafx.fxml.FXML;
import javafx.scene.text.Text

class FXMLExampleController {
    @FXML
    private val actiontarget: Text? = null
    @FXML
    protected fun handleSubmitButtonAction(event: ActionEvent?) {
        actiontarget?.setText("Sign in button pressed")
    }
}