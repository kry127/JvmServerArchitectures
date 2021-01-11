package ru.spb.kry127.netbench.server

import junit.framework.Assert.assertTrue
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.Is.`is`
import org.junit.Test
import kotlin.random.Random

class ArraySorterKtTest {

    @Test
    fun testSortO2() {
        repeat(1000) {
            val randomList = (1..Random.nextInt(50, 500)).map { Random.nextInt() }
            val sortExpected = randomList.sorted()
            val sortActual = sortO2(randomList)
            assertThat(sortActual, `is`(sortExpected))
        }
    }
}