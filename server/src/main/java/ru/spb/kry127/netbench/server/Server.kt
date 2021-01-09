package ru.spb.kry127.netbench.server

import java.io.Closeable

/**
 * This interface represents a server. An implementation of this
 * interface is architecture-dependent:
 *  1. Spawn thread per every new user
 *     + single thread pool for response
 *     + thread pool for tasks
 *  2. Nonblocking input and output socket
 *     + thread pool for tasks
 *  3. Asynchronous sockets
 *     + thread pool for tasks
 */
interface Server : Closeable {

    /**
     * Starts server
     */
    fun start()
}