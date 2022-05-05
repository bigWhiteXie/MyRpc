package client;

import client.handler.RpcResponseHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.DefaultPromise;
import message.RpcRequestMessage;
import message.RpcResponseMessage;
import protocol.MessageCodec;
import protocol.SequenceGenerator;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;

/**
 *管理客户端channel：包括channel的初始化已经多线程状态下安全获取channel
 */
public class ClientManager {
    private static Channel channel;
    private static final Object LOCK = new Object();

    public static void init(){
        Bootstrap bootstrap = new Bootstrap();
        NioEventLoopGroup group = new NioEventLoopGroup();
        RpcResponseHandler responseHandler = new RpcResponseHandler();

        try {
            channel = bootstrap.group(group).
                    channel(NioSocketChannel.class).
                    handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) throws Exception {
                            ChannelPipeline pipeline = channel.pipeline();
                            //1.添加预设长度解码器，解决粘包半包问题
                            pipeline.addLast(new LengthFieldBasedFrameDecoder(1024, 12, 4, 0, 0));
                            pipeline.addLast(new LoggingHandler(LogLevel.DEBUG));
                            //2.添加自定义协议编解码器
                            pipeline.addLast(new MessageCodec());
                            //3.添加结果rpcResponse结果处理器
                            pipeline.addLast(responseHandler);
                        }
                    }).connect(new InetSocketAddress(8080)).sync().channel();
            channel.closeFuture().addListener(future -> {
               group.shutdownGracefully();
            });
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }

    public static Channel getChannel(){
        if(channel != null){
            return channel;
        }

        synchronized (LOCK){
            if(channel != null){
                return channel;
            }
            init();
            return channel;
        }
    }

    public static <T> T getServiceProxy(Class<T> serviceClass){

        ClassLoader loader = serviceClass.getClassLoader();
        Class<?>[] interfaces = new Class[]{serviceClass};
        Channel channel = getChannel();
        Object o = Proxy.newProxyInstance(loader, interfaces, (proxy, method, args) -> {

            DefaultPromise<Object> promise = new DefaultPromise<>(channel.eventLoop());
            int sequenceId = SequenceGenerator.nextId();
            RpcResponseHandler.PROMISE.put(sequenceId, promise);

            RpcRequestMessage rpcRequestMessage = new RpcRequestMessage(sequenceId, serviceClass.getName(), method.getName(), method.getParameterTypes(), method.getReturnType(), args);

            channel.writeAndFlush(rpcRequestMessage);



            promise.await();

            if (promise.isSuccess()) {
                Object now = promise.getNow();
                return now;
            } else {
                throw new RuntimeException(promise.cause());
            }
        });

        return (T) o;
    }
}
