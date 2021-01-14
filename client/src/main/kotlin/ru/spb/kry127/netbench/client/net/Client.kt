package ru.spb.kry127.netbench.client.net

import ru.spb.kry127.netbench.client.InputDataPoint
import ru.spb.kry127.netbench.client.IntGenerator
import ru.spb.kry127.netbench.client.net.MeanStatistics.Companion.mean
import ru.spb.kry127.netbench.proto.ArraySorter
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

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
                    AsyncHandlerLong.completed(
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

        var state: ClientState = ClientState.SENDING,
        var buf: ByteBuffer = ByteBuffer.allocate(0),
        var msgSize: Int = 0,
        var startProcessingTime: Long = 0,
        val resultList: MutableList<MeanStatistics> = mutableListOf(),

        )

    object AsyncHandlerLong : CompletionHandler<Long, AsyncHandlerAttachment> {
        override fun completed(result: Long, attachment: AsyncHandlerAttachment) {
            when (attachment.state) {
                ClientState.START_SENDING -> {
                    attachment.startProcessingTime = System.currentTimeMillis()
                    // we'd like to send some array
                    val toSort = IntGenerator.generateUniformArray(attachment.inputDataPoint.n)
                    val msgBytes = ArraySorter.SortArray.newBuilder().addAllArray(toSort).build().toByteArray()
                    val msgHeader = ByteBuffer.allocate(4).putInt(msgBytes.size).array()
                    val buffers = listOf(msgHeader, msgBytes).map { ByteBuffer.wrap(it) }.toTypedArray()

                    attachment.state = ClientState.SENDING
                    attachment.channel.write(
                        buffers,
                        0,
                        buffers.size,
                        20L,
                        TimeUnit.SECONDS,
                        attachment,
                        AsyncHandlerLong
                    )
                }
                ClientState.SENDING -> {
                    attachment.state = ClientState.RECEIVING_SIZE
                    attachment.buf = ByteBuffer.allocate(4)
                    attachment.channel.read(attachment.buf, attachment, AsyncHandlerInt)
                }
                else -> error("Wrong state at Long handler")
            }
        }

        override fun failed(exc: Throwable, attachment: AsyncHandlerAttachment) {
            attachment.completableFuture.completeExceptionally(exc)
        }

    }

    object AsyncHandlerInt : CompletionHandler<Int, AsyncHandlerAttachment> {
        override fun completed(result: Int, attachment: AsyncHandlerAttachment) {
            when (attachment.state) {
                ClientState.RECEIVING_SIZE -> {
                    val sz = attachment.buf.getInt()
                    attachment.msgSize = sz
                    attachment.buf = ByteBuffer.allocate(sz)
                    attachment.state = ClientState.RECEIVING_MSG
                    attachment.channel.read(attachment.buf, attachment, AsyncHandlerInt)
                }
                ClientState.RECEIVING_MSG -> {
                    val rsp = ArraySorter.SortArrayRsp.parseFrom(attachment.buf)
                    val meanResult = MeanStatistics(
                        rsp.requestProcessingTime,
                        rsp.clientProcessingTime,
                        System.currentTimeMillis() - attachment.startProcessingTime
                    )
                    attachment.resultList.add(meanResult)
                    attachment.timeToLive--
                    if (attachment.timeToLive <= 0) {
                        // That's all, lets provide the result!
                        attachment.completableFuture.complete(attachment.resultList.mean())
                    } else {
                        // That's not all, start over again!
                        attachment.state = ClientState.START_SENDING
                        AsyncHandlerLong.completed(0, attachment)
                    }

                }
                else -> error("Wrong state at Int handler")
            }
        }

        override fun failed(exc: Throwable, attachment: AsyncHandlerAttachment) {
            attachment.completableFuture.completeExceptionally(exc)
        }

    }
}