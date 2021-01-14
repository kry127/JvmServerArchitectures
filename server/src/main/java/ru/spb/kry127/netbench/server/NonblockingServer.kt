package ru.spb.kry127.netbench.server

import com.google.protobuf.InvalidProtocolBufferException
import ru.spb.kry127.netbench.proto.ArraySorter
import java.lang.Thread.sleep
import java.math.BigInteger
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

class NonblockingServer(private val port: Int, workersCount: Int) : Server {

    // thread pool for sorting arrays
    private val sortingThreadPool = Executors.newFixedThreadPool(workersCount) {
        Thread(it).apply { isDaemon = true }
    }

    // unique ID generator for clients
    private val uniqueIdGen = AtomicLong()

    // hashmap of id to client state
    private val clientBundles = ConcurrentHashMap<Long, ClientBundle>()

    // create selectors
    private val readSelector = Selector.open() // open new selector for reading from clients
    private val writeSelector = Selector.open() // open new selector for writing to clients

    // declare threads for processing readSelector and writeSelector
    private var readingThreadSelector : Thread
    private var writingThreadSelector : Thread

    init {
        // separate thread for reading selector
        readingThreadSelector = thread {
            while (true) {
                // make try-catch block for InterruptedExceptions
                try {
                    // if no keys are now for selection, do not block on selector
                    if (readSelector.keys().isEmpty()) {
                        sleep(5000)
                        continue
                    }

                    readSelector.select() // selecting with nonempty
                    val iter = readSelector.selectedKeys().iterator()
                    while (iter.hasNext()) {
                        val key = iter.next()

                        if (key.isReadable) {
                            val clientId = key.attachment() as Long // get clientId from attachment
                            val clientBundle = clientBundles[clientId] ?: error("Illegal client state")
                            val channel = key.channel() as SocketChannel // get channel
                            when (clientBundle.state) {
                                ClientState.READY -> {
                                    val code = channel.read(clientBundle.sizeBuf)
                                    if (code == -1) {
                                        // the communication is over
                                        key.cancel()
                                        clientBundles.remove(clientId)
                                    }
                                    if (!clientBundle.sizeBuf.hasRemaining()) {
                                        val size = BigInteger(clientBundle.sizeBuf.array()).toInt()
                                        clientBundle.state = ClientState.READING
                                        clientBundle.msgBuf = ByteBuffer.allocate(size)
                                        clientBundle.sizeBuf.clear()
                                    }
                                }
                                ClientState.READING -> {
                                    channel.read(clientBundle.msgBuf)
                                    if (!clientBundle.msgBuf.hasRemaining()) {
                                        // message is ready to parse and send for computation
                                        try {
                                            val sortArrayTask = ArraySorter.SortArray.parseFrom(clientBundle.msgBuf)

                                            // unregister read selector (should be re-registered later)
                                            key.cancel()

                                            // process task
                                            processTask(clientId, sortArrayTask)
                                        } catch (ex : InvalidProtocolBufferException) {
                                            // invalid message found
                                            ex.printStackTrace(System.out)
                                            // try to read next message again
                                            clientBundle.state = ClientState.READY
                                        }
                                    }
                                }
                                else -> error ("Wrong client state")
                            }
                            iter.remove() // remove processed key from the set
                        } else {
                            error ("Illegal interest key in readSelector")
                        }
                    }
                } catch (ex : InterruptedException) { }
            }
        }


        // separate thread for writing selector
        writingThreadSelector = thread {
            while (true) {
                try {
                    if (writeSelector.keys().isEmpty()) {
                        sleep(5000)
                        continue
                    }

                    writeSelector.select()
                    val iter = writeSelector.selectedKeys().iterator()
                    while (iter.hasNext()) {
                        val key = iter.next()

                        if (key.isWritable) {
                            val clientId = key.attachment() as Long
                            val clientBundle = clientBundles[clientId] ?: error("Illegal client state")
                            val channel = key.channel() as SocketChannel // get channel
                            when (clientBundle.state) {
                                ClientState.SENDING -> {
                                    channel.write(clientBundle.msgBuf)
                                    if (!clientBundle.msgBuf.hasRemaining()) {

                                        // unregister read selector (should be re-registered later)
                                        key.cancel()

                                        // register read selector once again
                                        clientBundle.state = ClientState.READY
                                        registerSelector(
                                            readSelector,
                                            readingThreadSelector,
                                            clientBundle.clientSocket,
                                            SelectionKey.OP_READ,
                                            clientId
                                        )
                                    }
                                }
                                else -> error ("Wrong client state")
                            }
                            iter.remove() // remove processed key from the set
                        } else {
                            error ("Illegal interest key in writeSelector")
                        }
                    }
                } catch (ex : InterruptedException) { }
            }
        }
    }

    private fun registerSelector(selector : Selector, selectorThread : Thread,
                                 clientSocket: SocketChannel, ops : Int, clientId: Long) {
        var needsInterrupt = false
        if (selector.keys().isEmpty()) {
            needsInterrupt = true
            // wakeup selectors
            selectorThread.interrupt()
        }
        // register client in selectors with generated client id
        clientSocket.register(selector, ops, clientId) // may block
        needsInterrupt = needsInterrupt || selector.keys().size == 1
        if (needsInterrupt) {
            // wakeup selector in case of sleeping
            selectorThread.interrupt()
        }
    }

    override fun start() {
        val serverSocket = ServerSocketChannel.open()
        serverSocket.configureBlocking(true) // make serversocket blocking
        serverSocket.bind(InetSocketAddress(port)) // bind server to dedicated port

        while (true) {
            val clientSocket = serverSocket.accept() // blocking accept client
            val clientId = uniqueIdGen.getAndIncrement() // generate client id
            clientSocket.configureBlocking(false) // set client in nonblocking mode

            // create client's state
            clientBundles[clientId] = ClientBundle(clientSocket, ClientState.READY)

            // register selector for read
            registerSelector(readSelector, readingThreadSelector, clientSocket, SelectionKey.OP_READ, clientId)
        }
    }

    private fun processTask(clientId : Long, task : ArraySorter.SortArray) {

        val clientBundle = clientBundles[clientId] ?: error("Illegal client state")
        clientBundle.state = ClientState.PROCESSING

        // register time of starting processing the client -- starts when task has been received from client (here)
        val startOfTheProcessing = System.currentTimeMillis()

        // extract array from message and make it immutable
        val arrayToSort = task.arrayList.toList()

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

            val rspBuf = rspMessage.toByteArray()

            // make buffer with size of passing message

            // concat to single buffer
            val mergeBuffer = ByteBuffer.allocate(4 + rspBuf.size)
            mergeBuffer.putInt(rspBuf.size)
            mergeBuffer.put(rspBuf)
            mergeBuffer.flip()

            // set message to write to client
            clientBundle.state = ClientState.SENDING
            clientBundle.msgBuf = mergeBuffer // no need to flip: https://www.mindprod.com/jgloss/bytebuffer.html#SAMPLECODE
            // register write selector
            registerSelector(
                writeSelector,
                writingThreadSelector,
                clientBundle.clientSocket,
                SelectionKey.OP_WRITE,
                clientId
            )
        }
    }

    override fun close() {
        sortingThreadPool.shutdownNow()
        sortingThreadPool.awaitTermination(PropLoader.gracefulStopThreadPoolTimeoutSeconds, TimeUnit.SECONDS)
    }


    private enum class ClientState { READY, READING, PROCESSING, SENDING }
    private class ClientBundle(
        val clientSocket: SocketChannel,
        var state: ClientState,
        var msgBuf: ByteBuffer = ByteBuffer.allocate(0), // buffer for incoming and outcoming message, changes depending on state
        val sizeBuf: ByteBuffer = ByteBuffer.allocate(4), // buffer for message size, always presented
    )
}