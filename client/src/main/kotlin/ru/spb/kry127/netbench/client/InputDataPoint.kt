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


/**
 * Selector to specify what parameter to range
 */
enum class RangeBy {X, M, N, DELTA}
/**
 * Describes data point by chosen range (x, m, n or delta): from; to; step
 */
data class RangedDataPoint(
    val dataPoint: InputDataPoint,

    val rangeBy: RangeBy, // number of component of InputDataPoint to range
    val from: Int,
    val to: Int,
    val step: Int
) {
    init {
        if (step == 0) {
            error("You cannot make zero step iterator")
        }
    }
    /**
     * Creates an iterator object which generate sequence of datapoints to measure.
     * Also, it returns key as first component component.
     * Be careful with 'from', 'to' and 'step' parameters, you can catch overflow.
     * Also, there is check for step != 0
     */
    fun getDatapoints() : Iterator<Pair<Int, InputDataPoint>>
            = object : Iterator<Pair<Int, InputDataPoint>> {
        var i = from
        override fun hasNext() = (i <= to) && (step > 0) || (i >= to) && (step < 0)
        override fun next(): Pair<Int, InputDataPoint> {
            val (x, n, m, delta) = dataPoint
            val ret = i to when(rangeBy) {
                RangeBy.X -> InputDataPoint(i, n, m, delta     )
                RangeBy.N     -> InputDataPoint(x, i, m, delta     )
                RangeBy.M     -> InputDataPoint(x, n, i, delta     )
                RangeBy.DELTA -> InputDataPoint(x, n, m, i.toLong())
            }
            i += step
            return ret
        }
    }

    fun constDescription(): String {
        val (x, n, m, delta) = dataPoint
        val stepDescription = if (step != 1) "(step=$step)" else ""
        val r = "$from..$to $stepDescription" // range description
        return when(rangeBy) {
            RangeBy.X     -> "X=$r, N=$n, M=$m, Delta=$delta"
            RangeBy.N     -> "X=$x, N=$r, M=$m, Delta=$delta"
            RangeBy.M     -> "X=$x, N=$n, M=$r, Delta=$delta"
            RangeBy.DELTA -> "X=$x, N=$n, M=$m, Delta=$r"
        }
    }

    fun iterLabel(): String = when(rangeBy) {
        RangeBy.X     -> "X"
        RangeBy.N     -> "N"
        RangeBy.M     -> "M"
        RangeBy.DELTA -> "Delta"
    }
}
