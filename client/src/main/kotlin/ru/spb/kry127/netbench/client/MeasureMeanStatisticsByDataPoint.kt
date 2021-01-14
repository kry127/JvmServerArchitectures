package ru.spb.kry127.netbench.client

import ru.spb.kry127.netbench.client.net.Client
import ru.spb.kry127.netbench.client.MeanStatistics.Companion.mean
import java.util.concurrent.Callable
import java.util.concurrent.Executors


/**
 * Perform measure on the chosen parameters
 * You should specify factory for clients
 *
 * This is the most easy way to get measure for all specified parameters.
 * If you need to vary some parameter, do it in some kind of loop
 */
fun measureStatistics(withParameters: InputDataPoint, clientFactory: (Int) -> Client): MeanStatistics {
    val executor = Executors.newFixedThreadPool(8)
    val clientResults = (0 until withParameters.m).map {
        executor.submit(Callable {
            val client = clientFactory(it)
            client.communicate(withParameters)
        })
    }
    return clientResults.map { it.get() }.mean()
}