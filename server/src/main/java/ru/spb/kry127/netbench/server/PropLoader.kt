package ru.spb.kry127.netbench.server

object PropLoader {
    const val defaultWorkersCount = 8;
    const val serverBacklogAmount: Int = 32
    const val gracefulStopThreadPoolTimeoutSeconds: Long = 30

    const val availableArchitecturesAsString = "thread|nonblock|async"
    val availableArchitectures = availableArchitecturesAsString.split("|")
}