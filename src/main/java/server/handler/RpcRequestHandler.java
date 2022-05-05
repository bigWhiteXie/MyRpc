package server.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import message.RpcRequestMessage;
import message.RpcResponseMessage;
import service.ServiceFactory;

import java.lang.reflect.Method;

public class RpcRequestHandler extends SimpleChannelInboundHandler<RpcRequestMessage> {
    /**
     * 跟据request传来的信息找到实现类并执行对应方法，将结果封装到RpcResponseMessage中再写入客户端
     * @param ctx
     * @param message
     * @throws Exception
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcRequestMessage message) throws Exception {
        int sequenceId = message.getSequenceId();
        //1.设置sequenceId
        RpcResponseMessage responseMessage = new RpcResponseMessage();
        responseMessage.setSequenceId(sequenceId);
        //2.根据Class获取实现类实例
        Class<?> clazz = Class.forName(message.getInterfaceName());
        Object serviceImpl = ServiceFactory.getServiceImpl(clazz);
        //3.获取该方法
        Method method = clazz.getMethod(message.getMethodName(), message.getParameterTypes());
        //4.执行该方法获取结果
        Object result = method.invoke(serviceImpl, message.getParameters());
        //5.将结果写入response
        responseMessage.setReturnValue(result);

        //6.将response传输到客户端
        ctx.channel().writeAndFlush(responseMessage);
    }
}
