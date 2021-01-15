package ru.spb.kry127.netbench.server

import ru.spb.kry127.netbench.proto.ArraySorter
import java.math.BigInteger
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

class ThreadedServer(private val port : Int, workersCount : Int) : Server {

    // thread pool for sorting arrays
    val sortingThreadPool = Executors.newFixedThreadPool(workersCount) {
        Thread(it).apply { isDaemon = true }
    }

    override fun start() {
        val serverSocket = ServerSocket(port, PropLoader.serverBacklogAmount)
        while (true) {
            val clientSocket = serverSocket.accept()
            thread {
                clientSocket.use {
                    // single thread pool for responses
                    val responseSingleThreadExecutor = Executors.newSingleThreadExecutor() {
                        Thread(it).apply { isDaemon = true }
                    }
                    try {
                        // communicate with client in separate thread
                        communicateWithClient(clientSocket, responseSingleThreadExecutor)
                    } finally {
                        responseSingleThreadExecutor.shutdown()
                        responseSingleThreadExecutor.awaitTermination(PropLoader.gracefulStopThreadPoolTimeoutSeconds, TimeUnit.SECONDS)
                    }
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
            val avail = inputStream.available()
            if (byteArray.size != 4) {
//                println("Client sent invalid message size: ${byteArray.size}, but available to read: ${avail}, terminate connection")
                return
            }
            val inputSize = ByteBuffer.wrap(byteArray).getInt()

//            println("Reading message of size: ${inputSize}, available bytes: ${avail}")
            val inputMsgBuf = ByteBuffer.wrap(inputStream.readNBytes(inputSize))


            // receive task for array sorting from client
            // https://developers.google.com/protocol-buffers/docs/reference/java/com/google/protobuf/Parser.html#parseDelimitedFrom-java.io.InputStream-
            val reqMessage = ArraySorter.SortArray.parseFrom(inputMsgBuf)
                ?: return // the tasks for sorting has been ended if null has been returned (see link above)

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
                    val protoMsg = rspMessageBuilder.setClientProcessingTime(endOfTheProcessing - startOfTheProcessing)
                        .build()
                    val bytes = protoMsg.toByteArray()
                    outputStream.write(ByteBuffer.allocate(4).putInt(bytes.size).array())
                    outputStream.write(bytes)
                }
            }
        }
    }

    override fun close() {
        sortingThreadPool.shutdownNow()
        sortingThreadPool.awaitTermination(PropLoader.gracefulStopThreadPoolTimeoutSeconds, TimeUnit.SECONDS)
    }
}