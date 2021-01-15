package ru.spb.kry127.netbench.client.gui

import javafx.application.Platform.runLater
import javafx.collections.ObservableList
import javafx.fxml.FXML
import javafx.fxml.Initializable
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




class ResultController: Initializable {

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

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        textAreaParameters?.text = "Initialization completed"
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
        val (process, connectTo, rangedDataPoint) = taskDescription
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

            val measure = measureStatistics(dataPoint) {
                ClientAsyncImpl(connectTo)
            }
            sortingTime += key to measure.requestProcessingTime
            serverProcessingTime += key to measure.clientProcessingTime // some nonsense here :)
            clientProcessingTime += key to measure.overallDelay
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
                sb.appendLine(rangedDataPoint.constDescription())
                text = sb.toString()
            }
        }
    }

}
