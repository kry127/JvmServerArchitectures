package ru.spb.kry127.netbench.client.gui

import ru.spb.kry127.netbench.client.InputDataPoint
import ru.spb.kry127.netbench.client.RangedDataPoint
import java.net.InetSocketAddress

/**
 * This data class is used to communicate between old and new window.
 * It contains:
 *  1. Process where server is working
 *  2. Connection information
 *  3. Constants parameters
 *  4. Selected range parameter
 */
data class ConnectionAndMeasurementDescription(
    val process: Process,
    val connectTo: InetSocketAddress,
    val rangedDataPoint: RangedDataPoint,
    val archDescription: String
)