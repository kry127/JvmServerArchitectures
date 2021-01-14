package ru.spb.kry127.netbench.client

import kotlin.random.Random

/**
 * Use this object to generate some random lists of integers
 */
object IntGenerator {
    /**
     * Generate array of size `size` with function `randf` that takes index of generated element
     */
    fun generateArray(size: Int, randf : (Int) -> Int) = (1..size).map { randf(it) }

    /**
     * Generate array of size `size` with uniformly distributed elements
     */
    fun generateUniformArray(size : Int) = generateArray(size) { Random.nextInt() }

    /**
     * Generate array of size `size` with uniformly distributed elements
     */
    fun generateUniformArray(size : Int, from : Int, until : Int)
      = generateArray(size) { Random.nextInt(from, until) }
}