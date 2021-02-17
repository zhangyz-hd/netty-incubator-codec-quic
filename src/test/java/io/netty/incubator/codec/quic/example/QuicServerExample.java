/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.incubator.codec.quic.example;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicServerCodecBuilder;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class QuicServerExample {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(QuicServerExample.class);
    private static final int CHUNK_SIZE = 2048;

    private QuicServerExample() { }

    public static void main(String[] args) throws Exception {
        SelfSignedCertificate selfSignedCertificate = new SelfSignedCertificate();
        QuicSslContext context = QuicSslContextBuilder.forServer(
                selfSignedCertificate.privateKey(), null, selfSignedCertificate.certificate())
                .applicationProtocols("perf").build();
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        ChannelHandler codec = new QuicServerCodecBuilder().sslContext(context)
                .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                // Configure some limits for the maximal number of streams (and the data) that we want to handle.
                .initialMaxData(100000)
                .initialMaxStreamDataBidirectionalLocal(10000)
                .initialMaxStreamDataBidirectionalRemote(10000)
                .initialMaxStreamsBidirectional(100)
                .initialMaxStreamsUnidirectional(100)
                .maxRecvUdpPayloadSize(1350)
                .maxSendUdpPayloadSize(1350)
                .activeMigration(false)
                .earlyData(true)
                // Setup a token handler. In a production system you would want to implement and provide your custom
                // one.
                .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                // ChannelHandler that is added into QuicChannel pipeline.
                .handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelInactive(ChannelHandlerContext ctx) {
                        ((QuicChannel) ctx.channel()).collectStats().addListener(f -> {
                            if (f.isSuccess()) {
                                LOGGER.info("Connection closed: {}", f.getNow());
                            }
                        });
                    }

                    @Override
                    public boolean isSharable() {
                        return true;
                    }
                })
                .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                    @Override
                    protected void initChannel(QuicStreamChannel ch)  {
                        ch.pipeline().addLast(new ByteToMessageDecoder() {
                            private long numBytesRequested = -1;

                            @Override
                            protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
                                if (in.readableBytes() < 8) {
                                    return;
                                }
                                if (numBytesRequested == -1) {
                                    numBytesRequested = in.readLong();
                                    writeData(ctx);
                                } else {
                                    in.skipBytes(in.readableBytes());
                                }
                            }

                            private void writeData(ChannelHandlerContext ctx) {
                                ByteBuf buffer = ctx.alloc().directBuffer(CHUNK_SIZE).writeZero(CHUNK_SIZE);
                                do {
                                    long size = Math.min(numBytesRequested, CHUNK_SIZE);
                                    buffer.writerIndex((int) size);

                                    ChannelFuture f = ctx.write(buffer.retainedDuplicate());
                                    numBytesRequested -= CHUNK_SIZE;
                                    if (numBytesRequested <= 0) {
                                        f.addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
                                        break;
                                    }
                                } while (ctx.channel().isWritable());
                                buffer.release();
                                ctx.flush();
                            }

                            @Override
                            public void channelWritabilityChanged(ChannelHandlerContext ctx) {
                                if (ctx.channel().isWritable()) {
                                    writeData(ctx);
                                }
                            }
                        });
                    }
                }).build();
        try {
            Bootstrap bs = new Bootstrap();
            Channel channel = bs.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(codec)
                    .option(ChannelOption.MAX_MESSAGES_PER_READ, Integer.MAX_VALUE)
                    .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(2048))
                    .option(ChannelOption.WRITE_BUFFER_WATER_MARK,
                            new WriteBufferWaterMark(Integer.MAX_VALUE, Integer.MAX_VALUE))
                    .bind(new InetSocketAddress(9999)).sync().channel();
            channel.closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}
