package ru.spb.kry127.netbench.server

import ru.spb.kry127.netbench.proto.ArraySorter
import java.math.BigInteger
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis


class AsynchronousServer(private val port : Int, workersCount : Int) : Server {

    // thread pool for sorting arrays
    private val sortingThreadPool = Executors.newFixedThreadPool(workersCount) {
        Thread(it).apply { isDaemon = true }
    }

    // unique ID generator for clients
    private val uniqueIdGen = AtomicLong()

    // hashmap of id to client state
    private val clientBundles = ConcurrentHashMap<Long, ClientBundle>()

    private val completionHandler = ReadSizeHandler()

    override fun start() {
        val group = AsynchronousChannelGroup.withThreadPool( Executors.newSingleThreadExecutor() {
            Thread(it).apply { isDaemon = true }
        })
        val asyncServerSocket = AsynchronousServerSocketChannel.open(group).bind(InetSocketAddress(port))
        // to preserve same pattern, keep this thread busy with accepting new clients
        while (true) {
            val clientSocketChannel = asyncServerSocket.accept().get()
            val uniqueClientId = uniqueIdGen.getAndIncrement()
            val clientBundle = ClientBundle(clientSocketChannel, ClientState.READY)
            clientBundles[uniqueClientId] = clientBundle
            // launch common handler to process client. Attachment is client identity
//            clientSocketChannel.read(clientBundle.sizeBuf, uniqueClientId, completionHandler)
            completionHandler.completed(0, uniqueClientId) // start handling
        }
    }

    override fun close() {
        sortingThreadPool.shutdownNow()
        sortingThreadPool.awaitTermination(PropLoader.gracefulStopThreadPoolTimeoutSeconds, TimeUnit.SECONDS)
    }

    /**
     * Handler for clients
     */
    inner class ReadSizeHandler : CompletionHandler<Int?, Long?> {
        override fun completed(result: Int?, id: Long?) {
            val clientBundle = clientBundles[id]
                ?: error("Corruption of user metadata: id=$id")
            if (result == -1) {
                // the communication with client is over
                println("Client #$id, status: COMMUNICATION_ENDED")
                clientBundles.remove(id)
                return
            }

            println("Client #$id, status: ${clientBundle.state}")
            when (clientBundle.state) {
                ClientState.READY -> {
                    clientBundle.state = ClientState.READING_SIZE
                    clientBundle.sizeBuf.clear()
                    clientBundle.clientSocket.read(clientBundle.sizeBuf, id, this)
                }
                ClientState.READING_SIZE -> {
                    clientBundle.sizeBuf.flip()
                    val size = clientBundle.sizeBuf.getInt()
                    clientBundle.state = ClientState.READING
                    clientBundle.msgBuf = ByteBuffer.allocate(size)
                    clientBundle.clientSocket.read(clientBundle.msgBuf, id, this)
                }
                ClientState.READING -> {
                    if (clientBundle.msgBuf.position() != clientBundle.msgBuf.capacity()) {
                        // continue reading
                        clientBundle.clientSocket.read(clientBundle.msgBuf, id, this)
                        return
                    }
                    clientBundle.msgBuf.flip()
                    val sortArrayTask = ArraySorter.SortArray.parseFrom(clientBundle.msgBuf.array())
                    clientBundle.state = ClientState.PROCESSING
                    val startOfTheProcessing = System.currentTimeMillis()
                    val arrayToSort = sortArrayTask.arrayList.toList()
                    // submit task for sorting, and when ready make response
                    sortingThreadPool.submit {
                        var sortedArray: List<Int>
                        val requestProcessingTime = measureTimeMillis {
                            sortedArray = sortO2(arrayToSort)
                        }

                        // assume here we end client processing (that's not pure truth)
                        val endOfTheProcessing = System.currentTimeMillis()
                        // start building response message
                        val rspMessage = ArraySorter.SortArrayRsp.newBuilder()
                            .addAllSortedArray(sortedArray)
                            .setRequestProcessingTime(requestProcessingTime)
                            .setClientProcessingTime(endOfTheProcessing - startOfTheProcessing)
                            .build()

                        // build merged message
                        val rspBuf = rspMessage.toByteArray()

                        // concat to single buffer
                        val mergeBuffer = ByteBuffer.allocate(4 + rspBuf.size)
                        mergeBuffer.putInt(rspBuf.size)
                        mergeBuffer.put(rspBuf)
                        mergeBuffer.flip()

                        // set message to write to client
                        clientBundle.state = ClientState.SENDING
                        clientBundle.msgBuf = mergeBuffer
                        clientBundle.clientSocket.write(clientBundle.msgBuf, id, this)
                    }
                }
                ClientState.SENDING -> {
                    // start over again
                    clientBundle.state = ClientState.READY
                    clientBundle.clientSocket.write(clientBundle.sizeBuf, id, this)
                }
                else -> error("Client illegal state when handler has been completed")
            }
        }

        override fun failed(exc: Throwable?, id: Long?) {
            System.err.println("Failed to communicate with client #$id")
        }

    }


    private enum class ClientState { READY, READING_SIZE, READING, PROCESSING, SENDING }
    private class ClientBundle(
        val clientSocket: AsynchronousSocketChannel,
        var state: ClientState,
        var msgBuf: ByteBuffer = ByteBuffer.allocate(0), // buffer for incoming and outcoming message, changes depending on state
        val sizeBuf: ByteBuffer = ByteBuffer.allocate(4), // buffer for message size, always presented
    )
}