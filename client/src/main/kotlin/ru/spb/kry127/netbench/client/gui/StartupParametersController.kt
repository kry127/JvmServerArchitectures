package ru.spb.kry127.netbench.client.gui

import javafx.event.ActionEvent
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox
import javafx.scene.text.Text
import javafx.collections.FXCollections

import javafx.collections.ObservableList
import javafx.event.EventHandler
import javafx.fxml.Initializable
import javafx.scene.control.Button
import javafx.scene.control.RadioButton
import javafx.scene.control.TextField
import java.lang.NumberFormatException
import java.net.URL
import java.util.*


class StartupParametersController : Initializable {
    @FXML private var dropdownArch: ChoiceBox<String>? = null

    @FXML private var radioN: RadioButton? = null
    @FXML private var radioM: RadioButton? = null
    @FXML private var radioDelta: RadioButton? = null

    @FXML private var inputX: TextField? = null
    @FXML private var inputN: TextField? = null
    @FXML private var inputM: TextField? = null
    @FXML private var inputDelta: TextField? = null

    @FXML private var inputFrom: TextField? = null
    @FXML private var inputTo: TextField? = null
    @FXML private var inputStep: TextField? = null

    @FXML private val buttonLaunch: Button? = null

    private val errorIntInputCssPseudoclass = "error-int-input"

    @Throws(NumberFormatException::class)
    private fun TextField.getInt() : Int {
        try {
            val ret = this.text.toInt()
            this.styleClass.remove(errorIntInputCssPseudoclass)
            return ret
        } catch (nfe : NumberFormatException) {
            if (!this.styleClass.contains(errorIntInputCssPseudoclass)) {
                this.styleClass.add(errorIntInputCssPseudoclass)
            }
            throw nfe
        }
    }

    private fun enableInputs() {
        inputN?.isDisable = false
        inputM?.isDisable = false
        inputDelta?.isDisable = false
    }

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        val options = FXCollections.observableArrayList("Threaded", "Nonblocking", "Asynchronous")
        dropdownArch?.setItems(options) // this statement adds all values in choiceBox
        dropdownArch?.setValue("Threaded") // this statement shows default value
        println("dropdownArch=$dropdownArch")

        // make all input box red on invalid input
        listOf(inputX, inputN, inputM, inputDelta, inputFrom, inputTo, inputStep).map {
            textField ->
            textField?.onKeyTyped = EventHandler {
                try { textField?.getInt() } catch (nfe : NumberFormatException) { }
            }
        }

        radioN?.onAction = EventHandler {
            if (radioN?.isSelected == true) {
                enableInputs()
                inputN?.isDisable = true
            }
        }
        radioM?.onAction = EventHandler {
            if (radioM?.isSelected == true) {
                enableInputs()
                inputM?.isDisable = true
            }
        }
        radioDelta?.onAction = EventHandler {
            if (radioDelta?.isSelected == true) {
                enableInputs()
                inputDelta?.isDisable = true
            }
        }

        buttonLaunch?.onMouseClicked = EventHandler {
            TODO("Process parameters and launch emulation")
        }
    }
}