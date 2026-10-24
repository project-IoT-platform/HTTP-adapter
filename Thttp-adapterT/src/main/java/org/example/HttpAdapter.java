package org.example;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import org.example.сonfig.AdapterConfig;
import org.example.controller.DeviceRequestHandler;
import org.example.repository.IotCoreRepository;
import org.example.service.DeviceService;
import org.example.codec.*;

public class HttpAdapter {

    public static void main(String[] args) throws Exception {
        DeviceService service = new DeviceService(new IotCoreRepository());

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
                                    .addLast(new HttpObjectAggregator(AdapterConfig.MAX_BODY))
                                    .addLast(new Base64BodyEncoder())
                                    .addLast(new Base64BodyDecoder())
                                    .addLast(new DeviceRequestHandler(service));
                        }
                    });
            Channel server = b.bind(AdapterConfig.PORT).sync().channel();
            System.out.println("HTTP adapter listening on :" + AdapterConfig.PORT + ", core = " + AdapterConfig.CORE_URL);
            server.closeFuture().sync();
        } finally {
            boss.shutdownGracefully();
            workers.shutdownGracefully();
        }
    }
}