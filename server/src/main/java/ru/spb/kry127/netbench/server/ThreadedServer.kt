package ru.spb.kry127.netbench.server

import ru.spb.kry127.netbench.proto.ArraySorter
import java.math.BigInteger
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

class ThreadedServer(private val port : Int, private val workersCount : Int) : Server {

    // thread pool for sorting arrays
    val sortingThreadPool = Executors.newFixedThreadPool(workersCount)

    override fun start() {
        val serverSocket = ServerSocket(port, PropLoader.serverBacklogAmount)
        while (true) {
            val clientSocket = serverSocket.accept()
            thread {
                // single thread pool for responses
                val responseSingleThreadExecutor = Executors.newSingleThreadExecutor()
                try {
                    // communicate with client in separate thread
                    communicateWithClient(clientSocket, responseSingleThreadExecutor)
                } finally {
                    responseSingleThreadExecutor.shutdownNow()
                    responseSingleThreadExecutor.awaitTermination(PropLoader.gracefulStopThreadPoolTimeoutSeconds, TimeUnit.SECONDS)
                }
            }
        }
    }

    private fun communicateWithClient(clientSocket : Socket, responseSingleThreadExecutor : ExecutorService) {
        // get input stream and ouptut stream for client
        val inputStream = clientSocket.getInputStream()
        val outputStream = clientSocket.getOutputStream()
        while (true) {
            // receive size of the message

            val byteArray = inputStream.readNBytes(4)
            if (byteArray.size != 4) {
                error("Client sent invalid message size")
            }
            val msgSize = BigInteger(byteArray).toInt() // actually, not used in threaded version of server

            // receive task for array sorting from client
            // https://developers.google.com/protocol-buffers/docs/reference/java/com/google/protobuf/Parser.html#parseDelimitedFrom-java.io.InputStream-
            val reqMessage = ArraySorter.SortArray.parseDelimitedFrom(inputStream)
                ?: break // the tasks for sorting has been ended if null has been returned (see link above)

            // register time of starting processing the client -- starts when task has been received from client (here)
            val startOfTheProcessing = System.currentTimeMillis()

            // extract array from message and make it immutable
            val arrayToSort = reqMessage.arrayList.toList()

            // submit task for sorting, and when ready make response
            sortingThreadPool.submit {
                var sortedArray: List<Int>
                val requestProcessingTime = measureTimeMillis {
                    sortedArray = sortO2(arrayToSort)
                }

                // start building response message, but not build it finally, because we are waiting for metrics
                val rspMessageBuilder = ArraySorter.SortArrayRsp.newBuilder()
                    .addAllSortedArray(sortedArray)
                    .setRequestProcessingTime(requestProcessingTime)
                responseSingleThreadExecutor.submit {
                    // assume here we end client processing (that's not pure truth)
                    val endOfTheProcessing = System.currentTimeMillis()
                    // embed metrics, build message and send it to client
                    rspMessageBuilder.setClientProcessingTime(endOfTheProcessing - startOfTheProcessing)
                        .build().writeDelimitedTo(outputStream)
                }
            }
        }
    }

    override fun close() {
        sortingThreadPool.shutdownNow()
        sortingThreadPool.awaitTermination(PropLoader.gracefulStopThreadPoolTimeoutSeconds, TimeUnit.SECONDS)
    }
}