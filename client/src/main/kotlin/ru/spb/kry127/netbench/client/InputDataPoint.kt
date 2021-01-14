package ru.spb.kry127.netbench.client

/**
 * This data class describes input parameters of the model.
 * One of the N, M or Delta can be variable parameter
 */
data class InputDataPoint(
    /**
     * Amount of remote sortings that client should request in one session with server
     */
    val x : Int,
    /**
     * Amount of elements in sorting array
     */
    val n : Int,
    /**
     * Amount of simultaneous clients to the server
     */
    val m : Int,
    /**
     * Delay between requests of sorting the array
     */
    val delta : Long
) {
}