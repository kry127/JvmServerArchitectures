package ru.spb.kry127.netbench.client.gui

import javafx.application.Platform.runLater
import javafx.collections.ObservableList
import javafx.embed.swing.SwingFXUtils
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.Node
import javafx.scene.chart.LineChart
import javafx.scene.chart.XYChart
import javafx.scene.control.Button
import javafx.scene.control.TextArea
import ru.spb.kry127.netbench.client.measureStatistics
import ru.spb.kry127.netbench.client.net.ClientAsyncImpl
import java.net.URL
import java.util.*
import javafx.scene.control.Alert.AlertType

import javafx.scene.control.Alert
import java.io.IOException

import javax.imageio.ImageIO

import javafx.scene.image.WritableImage

import javafx.scene.SnapshotParameters
import javafx.scene.layout.GridPane
import javafx.stage.FileChooser
import ru.spb.kry127.netbench.client.InputDataPoint
import ru.spb.kry127.netbench.client.MeanStatistics
import java.io.File
import java.nio.file.Paths


class ResultController: Initializable {
    @FXML
    private var gridPaneScreenshot: GridPane? = null

    @FXML
    private var buttonSaveImages: Button? = null
    @FXML
    private var buttonSaveCsv: Button? = null
    @FXML
    private var textAreaParameters: TextArea? = null


    @FXML
    private var lineChartSortingTime: LineChart<Int, Long>? = null
    @FXML
    private var lineChartServerDelay: LineChart<Int, Long>? = null
    @FXML
    private var lineChartClientDelay: LineChart<Int, Long>? = null

    var arch : String = "none"
    val measuredTime = mutableListOf<Pair<InputDataPoint, MeanStatistics>>()

    private fun saveAsPng(node: Node, file: File, ssp: SnapshotParameters = SnapshotParameters()) {
        val image: WritableImage = node.snapshot(ssp, null)
        try {
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file)
        } catch (e: IOException) {
            Alert(AlertType.ERROR).apply {
                title = "Save image error"
                headerText = "Error"
                contentText = "Couldn't save your file :(.\nReason: ${e.message}"
                showAndWait()
            }
        }
    }

    private fun saveAsCsv(file: File) {
        file.printWriter().use { out ->
            // put header
            out.println("arch,x,n,m,delta,sorting,server,client")
            for ((dataPoint, statistic) in measuredTime) {
                val (x, n, m, delta) = dataPoint
                val (sorting, server, client) = statistic
                out.println("$arch,$x,$n,$m,$delta,$sorting,$server,$client")
            }

        }
    }

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        textAreaParameters?.text = "Initialization completed"

        buttonSaveImages?.onMouseClicked = EventHandler {
            val button = buttonSaveImages ?: return@EventHandler
            val screenshotableNode = gridPaneScreenshot ?: return@EventHandler

            val fileChooser = FileChooser()
            val file = fileChooser.showSaveDialog(button.scene.window)
            val suffix = ".png"
            if (file != null) {
                if(file.absolutePath.endsWith(suffix)) {
                    saveAsPng(screenshotableNode, file)
                } else {
                    saveAsPng(screenshotableNode, Paths.get(file.absolutePath.toString() + suffix).toFile())
                }
            }
        }

        buttonSaveCsv?.onMouseClicked = EventHandler {
            val button = buttonSaveCsv ?: return@EventHandler

            val fileChooser = FileChooser()
            val file = fileChooser.showSaveDialog(button.scene.window)
            val suffix = ".csv"
            if (file != null) {
                if(file.absolutePath.endsWith(suffix)) {
                    saveAsCsv(file)
                } else {
                    saveAsCsv(Paths.get(file.absolutePath.toString() + suffix).toFile())
                }
            }
        }
    }

    /**
     * This function is called after `initialize` function.
     * It's input parameter is a taskDescription for perfoming range testing of the server.
     * Connection to the server and it's process handle are also attached to the `taskDescription`
     * object, as well as ranging data.
     *
     * This code is not launched in UI Thread!
     */
    internal fun connectToServerAndProcessData(taskDescription: ConnectionAndMeasurementDescription) {
        // destruct data class
        val (process, connectTo, rangedDataPoint, archDescription) = taskDescription
        arch = archDescription
        if (!process.isAlive) {
            runLater {
                Alert(AlertType.ERROR).apply {
                    title = "Process communication error"
                    headerText = "Error"
                    contentText = "The process with server is not alive"

                    showAndWait()
                }

                textAreaParameters?.text = "Process communication error: the process with server is not alive"
            }
            return
        }

        // notify user that computations is in process
        runLater { textAreaParameters?.apply {
            val sb = StringBuilder()
            sb.appendLine("Performing computations...")
            sb.appendLine("Architecture: ${arch}")
            sb.appendLine(rangedDataPoint.constDescription())
            text = sb.toString()
        } }

        val sortingTime = mutableListOf<Pair<Int, Long>>()
        val serverProcessingTime = mutableListOf<Pair<Int, Long>>()
        val clientProcessingTime = mutableListOf<Pair<Int, Long>>()

        for ((key, dataPoint) in rangedDataPoint.getDatapoints()) {
            runLater { textAreaParameters?.apply {
                text += "${rangedDataPoint.iterLabel()}=$key\n"
            } }

            try {
                val measure = measureStatistics(dataPoint) {
                    ClientAsyncImpl(connectTo)
                }

                measuredTime += dataPoint to measure

                sortingTime += key to measure.requestProcessingTime
                serverProcessingTime += key to measure.clientProcessingTime // some nonsense here :)
                clientProcessingTime += key to measure.overallDelay
            } catch (e: Throwable) {
                runLater {
                    Alert(AlertType.ERROR).apply {
                        title = "Network error"
                        headerText = "Error"
                        contentText = e.message

                        showAndWait()
                    }
                }
            }

        }

        // computation ended, work with GUI
        runLater {
            val sortingTimeSeries = XYChart.Series<Int, Long>()
            val serverProcessingTimeSeries = XYChart.Series<Int, Long>()
            val clientProcessingTimeSeries = XYChart.Series<Int, Long>()

            sortingTimeSeries.data.addAll(sortingTime.map { XYChart.Data(it.first, it.second) })
            serverProcessingTimeSeries.data.addAll(serverProcessingTime.map { XYChart.Data(it.first, it.second) })
            clientProcessingTimeSeries.data.addAll(clientProcessingTime.map { XYChart.Data(it.first, it.second) })

            lineChartSortingTime?.data?.add(sortingTimeSeries)
            lineChartServerDelay?.data?.add(serverProcessingTimeSeries)
            lineChartClientDelay?.data?.add(clientProcessingTimeSeries)

            textAreaParameters?.apply {
                val sb = StringBuilder()
                sb.appendLine("Computation done!")
                sb.appendLine("Architecture: ${taskDescription.archDescription}")
                sb.appendLine(rangedDataPoint.constDescription())
                text = sb.toString()
            }
        }
    }

}
