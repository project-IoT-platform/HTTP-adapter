package org.example;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class HttpAdapter {

    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    private static final String CORE_URL = System.getenv().getOrDefault("IOT_CORE_URL", "http://localhost:9000/ingest");
    private static final int MAX_BODY = 1024 * 1024;

    private static final HttpClient CORE_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public static void main(String[] args) throws Exception {
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup workers = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap()
                    .group(boss, workers)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new HttpServerCodec())
                                    .addLast(new HttpObjectAggregator(MAX_BODY))
                                    .addLast(new DeviceRequestHandler());
                        }
                    });
            Channel server = b.bind(PORT).sync().channel();
            System.out.println("HTTP adapter listening on :" + PORT + ", core = " + CORE_URL);
            server.closeFuture().sync();
        } finally {
            boss.shutdownGracefully();
            workers.shutdownGracefully();
        }
    }

    static class DeviceRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
            boolean keepAlive = HttpUtil.isKeepAlive(req);

            if (!req.method().equals(HttpMethod.POST)) {
                reply(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, "only POST", keepAlive);
                return;
            }
            String[] parts = req.uri().split("/");
            if (parts.length != 4 || !parts[1].equals("devices") || !parts[3].equals("telemetry")) {
                reply(ctx, HttpResponseStatus.NOT_FOUND, "unknown path", keepAlive);
                return;
            }
            String deviceId = parts[2];
            byte[] body = new byte[req.content().readableBytes()];
            req.content().readBytes(body);

            java.net.http.HttpRequest coreReq = java.net.http.HttpRequest.newBuilder(URI.create(CORE_URL))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("X-Device-Id", deviceId)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            CORE_CLIENT.sendAsync(coreReq, HttpResponse.BodyHandlers.ofString())
                    .whenComplete((resp, err) -> {
                        // Come back to the channel's event loop before writing the response.
                        ctx.executor().execute(() -> {
                            if (err != null) {
                                reply(ctx, HttpResponseStatus.BAD_GATEWAY, "core unavailable", keepAlive);
                            } else if (resp.statusCode() / 100 == 2) {
                                reply(ctx, HttpResponseStatus.ACCEPTED, "ok", keepAlive);
                            } else {
                                reply(ctx, HttpResponseStatus.BAD_GATEWAY,
                                        "core returned " + resp.statusCode(), keepAlive);
                            }
                        });
                    });
        }

        private static void reply(ChannelHandlerContext ctx, HttpResponseStatus status, String text, boolean keepAlive) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            FullHttpResponse resp = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
            resp.headers()
                    .set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8")
                    .setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
            if (keepAlive) {
                resp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                ctx.writeAndFlush(resp);
            } else {
                ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }
}
