package org.example.controller;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.example.service.DeviceService;

import java.nio.charset.StandardCharsets;

public class DeviceRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final DeviceService service;

    public DeviceRequestHandler(DeviceService service) {
        this.service = service;
    }

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

        service.forward(deviceId, body)
                .whenComplete((resp, err) -> {
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
