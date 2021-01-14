package ru.spb.kry127.netbench.client

import javafx.collections.FXCollections
import org.apache.commons.io.FileUtils
import java.nio.file.Path
import java.nio.file.Paths

object PropLoader {
    // server-related property
    private val defaultServerWorkersAmount: Int = 8

    val errorIntInputCssPseudoclass = "error-int-input"

    // describe dropdown and keys to launch each specific server architecture
    data class DropdownArchMeta(
        val archKey: String,
        val portKey: Int,
        val workersKey: Int = defaultServerWorkersAmount) {

    }

    val dropdownArchOptions = FXCollections.observableArrayList("Threaded", "Nonblocking", "Asynchronous")
    val dropdownArchArgvKeys = listOf(
        DropdownArchMeta("thread", 9661),
        DropdownArchMeta("nonblock", 9662),
        DropdownArchMeta("async", 9663)
    )

    val defaultServerPath: String = "./netbench-server.sh"
    fun homeRelativize(homePath : String) : Path {
        return Paths.get(FileUtils.getUserDirectoryPath(), homePath)
    }

    // server connection constants
    val connectionRetryDelayMs = 500L // in milliseconds
    val maximumConnectionRetries = 200

    val debug = true
}
