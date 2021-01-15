package ru.spb.kry127.netbench.client.net

import ru.spb.kry127.netbench.client.InputDataPoint
import ru.spb.kry127.netbench.client.IntGenerator
import ru.spb.kry127.netbench.client.MeanStatistics
import ru.spb.kry127.netbench.client.MeanStatistics.Companion.mean
import ru.spb.kry127.netbench.proto.ArraySorter
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.concurrent.*

/**
 * Client is something that should be able to receive InputDataPoint,
 * perform communication, and then give out mean statistics of the most interesting magnitudes
 */
interface Client {
    fun communicate(withParameters: InputDataPoint): MeanStatistics
}


/**
 * A stateless object of this class connects to the server and performs operation
 * of sorting some array. Should only know where to connect to give out info.
 * This client is based on asynchronous sockets.
 */
class ClientAsyncImpl(val connectTo: InetSocketAddress) : Client {

    override fun communicate(withParameters: InputDataPoint): MeanStatistics {
        return communicateAsync(withParameters).get()
    }

    private fun communicateAsync(withParameters: InputDataPoint): Future<MeanStatistics> {

        val completableFuture = CompletableFuture<MeanStatistics>()
        val asynchronousSocketChannel = AsynchronousSocketChannel.open()

        asynchronousSocketChannel.connect(connectTo, asynchronousSocketChannel,
            object : CompletionHandler<Void, AsynchronousSocketChannel> {
                override fun completed(result: Void?, channel: AsynchronousSocketChannel) {
                    // A little bit artificial, but it should work
                    AsyncHandler.completed(
                        0,
                        AsyncHandlerAttachment(
                            channel, completableFuture, withParameters,
                            timeToLive = withParameters.x
                        )
                    )
                }

                override fun failed(exc: Throwable?, channel: AsynchronousSocketChannel) {
                    completableFuture.completeExceptionally(exc)
                }

            })

        return completableFuture
    }


    enum class ClientState { START_SENDING, SENDING, RECEIVING_SIZE, RECEIVING_MSG }

    data class AsyncHandlerAttachment(
        val channel: AsynchronousSocketChannel,
        val completableFuture: CompletableFuture<MeanStatistics>,
        val inputDataPoint: InputDataPoint,
        var timeToLive: Int,

        var state: ClientState = ClientState.START_SENDING,
        var buf: ByteBuffer = ByteBuffer.allocate(0),
        var msgSize: Int = 0,
        var startProcessingTime: Long = 0,
        val resultList: MutableList<MeanStatistics> = mutableListOf(),

        // for validation
        var toSort: List<Int> = listOf()

        )

    object AsyncHandler : CompletionHandler<Int, AsyncHandlerAttachment> {
        override fun completed(result: Int, attachment: AsyncHandlerAttachment) {
            when (attachment.state) {
                ClientState.START_SENDING -> {
                    attachment.startProcessingTime = System.currentTimeMillis()
                    // we'd like to send some array
                    val toSort = IntGenerator.generateUniformArray(attachment.inputDataPoint.n)
                    attachment.toSort = toSort // TODO save for validation
                    val msgBytes = ArraySorter.SortArray.newBuilder().addAllArray(toSort).build().toByteArray()
                    val bigBuffer = ByteBuffer.allocate(4 + msgBytes.size)
                    bigBuffer.putInt(msgBytes.size)
                    bigBuffer.put(msgBytes)
                    bigBuffer.flip()

                    attachment.state = ClientState.SENDING
                    attachment.channel.write(
                        bigBuffer,
                        attachment,
                        AsyncHandler
                    )
                }
                ClientState.SENDING -> {
                    attachment.state = ClientState.RECEIVING_SIZE
                    attachment.buf = ByteBuffer.allocate(4)
                    attachment.channel.read(attachment.buf, attachment, AsyncHandler)
                }
                ClientState.RECEIVING_SIZE -> {
                    attachment.buf.flip()
                    val sz = attachment.buf.getInt()
                    println("toSort.size=${attachment.toSort.size}, sz=$sz")
                    if (sz > 100000000) {
                        print(".")
                    }
                    attachment.msgSize = sz
                    attachment.buf = ByteBuffer.allocate(sz)
                    attachment.state = ClientState.RECEIVING_MSG
                    attachment.channel.read(attachment.buf, attachment, AsyncHandler)
                }
                ClientState.RECEIVING_MSG -> {
                    if (attachment.buf.hasRemaining()) {
                        // continue reading
                        attachment.channel.read(attachment.buf, attachment, AsyncHandler)
                        return
                    }
                    attachment.buf.flip()
                    val rsp = ArraySorter.SortArrayRsp.parseFrom(attachment.buf)
                    val meanResult = MeanStatistics(
                        rsp.requestProcessingTime,
                        rsp.clientProcessingTime,
                        System.currentTimeMillis() - attachment.startProcessingTime
                    )

                    // make some validation
                    if(attachment.toSort.size != rsp.sortedArrayList.size) {
                        error("Size of sorted list has changed!")
                    }
                    for((v1, v2) in attachment.toSort.sorted().zip(rsp.sortedArrayList)) {
                        if (v1 != v2) {
                            error("Sorted arrays not equal")
                        }
                    }

                    attachment.resultList.add(meanResult)
                    attachment.timeToLive--
                    if (attachment.timeToLive <= 0) {
                        // That's all, lets provide the result!
                        attachment.completableFuture.complete(attachment.resultList.mean())
                        // and also close connection
                        attachment.channel.close()
                    } else {
                        // That's not all, start over again! But with delay of delta
                        waitUntil(System.currentTimeMillis() + attachment.inputDataPoint.delta)
                        attachment.state = ClientState.START_SENDING
                        completed(0, attachment)
                    }

                }
            }
        }

        private fun waitUntil(l: Long) {
            while(true) {
                val sleepFor = l - System.currentTimeMillis()
                if (sleepFor <= 0) break;
                try {
                    Thread.sleep(sleepFor)
                } catch (ex : InterruptedException) { }
            }
        }

        override fun failed(exc: Throwable, attachment: AsyncHandlerAttachment) {
            attachment.completableFuture.completeExceptionally(exc)
        }

    }
}