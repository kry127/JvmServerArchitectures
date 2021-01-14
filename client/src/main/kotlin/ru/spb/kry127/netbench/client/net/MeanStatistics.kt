package ru.spb.kry127.netbench.client.net

/**
 * This data class represents three points of the plot at a certain measure
 */
data class MeanStatistics(
    /**
     * the time of sorting array
     */
    val requestProcessingTime: Long,
    /**
     * The time of client processing on the server
     */
    val clientProcessingTime: Long,
    /**
     * The time that client spent when communicating with server (from sending request until receiving it)
     */
    val overallDelay: Long
) {

    companion object {
        fun List<MeanStatistics>.mean(): MeanStatistics =
            MeanStatistics(
                this.map { it.requestProcessingTime } .average().toLong(),
                this.map { it.clientProcessingTime  } .average().toLong(),
                this.map { it.overallDelay          } .average().toLong()
            )
    }
}
