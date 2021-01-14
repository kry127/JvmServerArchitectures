package ru.spb.kry127.netbench.client.gui

import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox

import javafx.event.EventHandler
import javafx.fxml.Initializable
import javafx.scene.control.Button
import javafx.scene.control.RadioButton
import javafx.scene.control.TextField
import ru.spb.kry127.netbench.client.InputDataPoint
import ru.spb.kry127.netbench.client.measureStatistics
import ru.spb.kry127.netbench.client.net.ClientAsyncImpl
import java.lang.NumberFormatException
import java.net.InetSocketAddress
import java.net.URL
import java.util.*
import javafx.stage.FileChooser
import ru.spb.kry127.netbench.client.PropLoader
import ru.spb.kry127.netbench.client.PropLoader.connectionRetryDelayMs
import ru.spb.kry127.netbench.client.PropLoader.dropdownArchArgvKeys
import ru.spb.kry127.netbench.client.PropLoader.dropdownArchOptions
import ru.spb.kry127.netbench.client.PropLoader.errorIntInputCssPseudoclass
import ru.spb.kry127.netbench.client.PropLoader.maximumConnectionRetries
import java.net.Socket
import java.nio.file.Path
import java.nio.file.Paths


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

    private fun launchServerArchitecture() : InetSocketAddress? {
        val arch = dropdownArch?.value
        val id = dropdownArchOptions.indexOf(arch)
        if (id == -1) return null

        val argvKeys = dropdownArchArgvKeys[id]

        val address = InetSocketAddress(argvKeys.portKey)
        if (PropLoader.debug) {
            return address // for debug purposes
        }

        // launch external app
        var execPath = resolveExecPath() ?: return null

        val processPrep = ProcessBuilder(execPath.toString(),
            "--arch", argvKeys.archKey,
            "--port", argvKeys.portKey.toString(),
            "--workers", argvKeys.workersKey.toString(),
        )
        val process = processPrep.start()

        var retries = 0
        while (process.isAlive) {
            try {
                if (retries > 0) {
                    Thread.sleep(connectionRetryDelayMs)
                }
                // check that server is functioning
                Socket(address.hostString, address.port).use { }
                return address // success
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

        // make all integer input box red on invalid input
        val zeroTolerant = listOf(inputN, inputDelta, inputFrom, inputTo)
        listOf(inputX, inputN, inputM, inputDelta, inputFrom, inputTo, inputStep).map {
            textField ->
            textField?.onKeyTyped = EventHandler {
                try {
                    val x = textField?.getInt()
                    if (x == null || x <= 0 && !(textField in zeroTolerant)) {
                        textField?.setError()
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
            val connectTo = launchServerArchitecture() // check that server is alive and get credentials
            val parameters = InputDataPoint(3, 3, 3, 3) // TODO loop through parameters

            if (connectTo != null) {
                val measure = measureStatistics(parameters) {
                    ClientAsyncImpl(connectTo)
                }
                print("measure: $measure")
            }
        }
    }
}