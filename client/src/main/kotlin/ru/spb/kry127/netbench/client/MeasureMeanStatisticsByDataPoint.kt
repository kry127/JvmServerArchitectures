package ru.spb.kry127.netbench.client

import ru.spb.kry127.netbench.client.net.Client
import ru.spb.kry127.netbench.client.MeanStatistics.Companion.mean
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis
import kotlin.time.measureTime


/**
 * Perform measure on the chosen parameters
 * You should specify factory for clients
 *
 * This is the most easy way to get measure for all specified parameters.
 * If you need to vary some parameter, do it in some kind of loop
 */
fun measureStatistics(withParameters: InputDataPoint, clientFactory: (Int) -> Client): MeanStatistics {
    val executor = Executors.newCachedThreadPool() {
        Thread(it).apply { isDaemon = true }
    }
    val clientResults = (0 until withParameters.m).map {
        executor.submit(Callable {
            val begin = System.currentTimeMillis()
            val client = clientFactory(it)
            val statistics = client.communicate(withParameters)
            // override client time with real value
            val (sort, server) = statistics
            MeanStatistics(sort, server, System.currentTimeMillis() - begin)
        })
    }
    return clientResults.map { it.get() }.mean()
}