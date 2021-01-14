package ru.spb.kry127.netbench.client.gui

import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox

import javafx.event.EventHandler
import javafx.fxml.FXMLLoader
import javafx.fxml.Initializable
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.RadioButton
import javafx.scene.control.TextField
import ru.spb.kry127.netbench.client.net.ClientAsyncImpl
import java.lang.NumberFormatException
import java.net.InetSocketAddress
import java.net.URL
import java.util.*
import javafx.stage.FileChooser
import javafx.stage.Stage
import ru.spb.kry127.netbench.client.PropLoader.connectionRetryDelayMs
import ru.spb.kry127.netbench.client.PropLoader.dropdownArchArgvKeys
import ru.spb.kry127.netbench.client.PropLoader.dropdownArchOptions
import ru.spb.kry127.netbench.client.PropLoader.errorIntInputCssPseudoclass
import ru.spb.kry127.netbench.client.PropLoader.maximumConnectionRetries
import java.net.Socket
import java.nio.file.Path
import java.nio.file.Paths
import javafx.scene.Scene
import ru.spb.kry127.netbench.client.*
import kotlin.concurrent.thread


class StartupParametersController: Initializable {

    @FXML private var dropdownArch: ChoiceBox<String>? = null

    @FXML private var radioN: RadioButton? = null
    @FXML private var radioM: RadioButton? = null
    @FXML private var radioDelta: RadioButton? = null

    // Integer inputs:
    @FXML private var inputX: TextField? = null
    @FXML private var inputN: TextField? = null
    @FXML private var inputM: TextField? = null
    @FXML private var inputDelta: TextField? = null

    @FXML private var inputFrom: TextField? = null
    @FXML private var inputTo: TextField? = null
    @FXML private var inputStep: TextField? = null

    // Path inputs:
    @FXML private var inputPathToServerExec: TextField? = null


    @FXML private var buttonLaunch: Button? = null
    @FXML private var buttonPathToServerExec: Button? = null

    private fun TextField.setError() {
        if (!styleClass.contains(errorIntInputCssPseudoclass)) {
            styleClass.add(errorIntInputCssPseudoclass)
        }
    }
    private fun TextField.resetError() {
        this.styleClass.removeAll(errorIntInputCssPseudoclass)
    }

    @Throws(NumberFormatException::class)
    private fun TextField.getInt() : Int {
        try {
            val ret = this.text.toInt()
            resetError()
            return ret
        } catch (nfe : NumberFormatException) {
            setError()
            throw nfe
        }
    }

    private fun Path.isExecutable() : Boolean {
        return toFile().let { it.canExecute() && !it.isDirectory }
    }

    private fun resolveExecPath() : Path? {
        val execPathStr = inputPathToServerExec?.text ?: PropLoader.defaultServerPath
        var execPath = Paths.get(execPathStr)
        if (!execPath.isExecutable()) {
            // try to resolve locally
            execPath = PropLoader.homeRelativize(execPathStr)
        }

        if (!execPath.isExecutable()) {
            // still not exists -- colorify
            inputPathToServerExec?.setError()
            return null
        } else {
            inputPathToServerExec?.resetError()
            return execPath
        }
    }

    private fun launchServerArchitecture() : Pair<Process, InetSocketAddress>? {
        val arch = dropdownArch?.value
        val id = dropdownArchOptions.indexOf(arch)
        if (id == -1) return null

        val argvKeys = dropdownArchArgvKeys[id]

        val address = InetSocketAddress(argvKeys.portKey)

        // launch external app
        var execPath = resolveExecPath() ?: return null

        val processPrep = ProcessBuilder(
            execPath.toString(),
            "--arch", argvKeys.archKey,
            "--port", argvKeys.portKey.toString(),
            "--workers", argvKeys.workersKey.toString(),
        )
        val process = processPrep.start()

        var retries = 0
        while (process?.isAlive == true) {
            try {
                if (retries > 0) {
                    Thread.sleep(connectionRetryDelayMs)
                }
                // check that server is functioning
                Socket(address.hostString, address.port).use { }
                return process to address // success
            } catch (thr : Throwable) { }

            retries++
            if (retries > maximumConnectionRetries) {
                return null // no success :(
            }
        }

        return null
    }

    private fun enableInputs() {
        inputN?.isDisable = false
        inputM?.isDisable = false
        inputDelta?.isDisable = false
    }

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        // add all values in dropdownArch
        dropdownArch?.setItems(dropdownArchOptions)
        dropdownArchOptions.getOrNull(0).let { dropdownArch?.setValue(it) }

        // describe all integers fields and their restriction classes
        val intFields = listOf(inputX, inputN, inputM, inputDelta, inputFrom, inputTo, inputStep)
        val intFieldPositive = listOf(inputX, inputM)
        val intFieldNonNegative = listOf(inputN, inputDelta)
        val intFieldNonZero = listOf(inputStep)

        // make all integer input box red on invalid input
        intFields.map {
            textField ->
            textField?.onKeyTyped = EventHandler {
                if (textField == null) return@EventHandler // if there is no component, nothing to do at all :)
                try {
                    val x = textField.getInt()
                    if (textField in intFieldPositive && x <= 0
                        || textField in intFieldNonNegative && x < 0
                        || textField in intFieldNonZero && x == 0) {
                        textField.setError()
                    }
                } catch (nfe : NumberFormatException) { }
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

        buttonPathToServerExec?.onMouseClicked = EventHandler {
            val fileChooser = FileChooser()
            val file = fileChooser.showOpenDialog(buttonPathToServerExec?.scene?.window)
            if (file != null) {
                inputPathToServerExec?.resetError()
                inputPathToServerExec?.text = file.toString()
            }
        }

        inputPathToServerExec?.onKeyTyped = EventHandler { resolveExecPath() }

        buttonLaunch?.onMouseClicked = EventHandler {
            val (process, connectTo) = launchServerArchitecture()  // check that server is alive and get credentials
                ?: return@EventHandler

            // create range description
            val rangeByM = radioM?.isSelected ?: false
            val rangeByN = radioN?.isSelected ?: false
            val rangeByDelta = radioDelta?.isSelected ?: false
            if (rangeByM && rangeByN || rangeByM && rangeByDelta || rangeByN && rangeByDelta) {
                error ("Multiple range axis detected")
            }
            val rangeBy = when {
                rangeByM     -> RangeBy.M
                rangeByN     -> RangeBy.N
                rangeByDelta -> RangeBy.DELTA
                else         -> error ("You should specify at least one ranging parameter")
            }

            // this big 'when' purpose is not to trigger unnecessary checks, red coloring and exception throws
            // if GUI component is missing, fall with NPE
            val inputDataPoint = when (rangeBy) {
                RangeBy.X -> InputDataPoint(
                    1,
                    inputN!!.getInt(),
                    inputM!!.getInt(),
                    inputDelta!!.getInt().toLong())
                RangeBy.N -> InputDataPoint(
                    inputX!!.getInt(),
                    0,
                    inputM!!.getInt(),
                    inputDelta!!.getInt().toLong())
                RangeBy.M -> InputDataPoint(
                    inputX!!.getInt(),
                    inputN!!.getInt(),
                    1,
                    inputDelta!!.getInt().toLong())

                RangeBy.DELTA -> InputDataPoint(
                    inputX!!.getInt(),
                    inputN!!.getInt(),
                    inputM!!.getInt(),
                    0L)
            }

            val rangedDataPoint = RangedDataPoint(inputDataPoint, rangeBy,
                inputFrom!!.getInt(), inputTo!!.getInt(), inputStep!!.getInt())

            // when rangedDataPoint has been created, we can make task description
            // with such data: server process handle, server connection info and ranged point data
            val taskDescription = ConnectionAndMeasurementDescription(process, connectTo, rangedDataPoint)

            // replace with result window
            val fxmlLoader = FXMLLoader()
            val root = fxmlLoader.load<Parent>(javaClass.getResourceAsStream("show_results.fxml"))
            val controller = fxmlLoader.getController<ResultController>()

            val stage = buttonLaunch?.scene?.window as Stage
            val scene = Scene(root)
            stage.scene = scene
            stage.hide()

            stage.onShown = EventHandler {
                thread {
                    // do not launch this code in UI thread, we should close process definitely
                    try {
                        // send task description in the controller
                        controller.connectToServerAndProcessData(taskDescription)
                    } finally {
                        // do not forget to destroy server process if it is alive
                        process.toHandle().destroy()
                    }
                }
            }
            stage.show()

        }
    }
}