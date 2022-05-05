package protocol;

import config.Config;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import message.Message;

import java.util.List;

@ChannelHandler.Sharable
public class SharblyMessageCodec extends MessageToMessageCodec<ByteBuf, Message> {
    @Override
    protected void encode(ChannelHandlerContext ctx, Message message, List<Object> list) throws Exception {
        ByteBuf buffer = ctx.alloc().buffer();
        //1.魔数，4字节
        buffer.writeBytes(new byte[]{1,2,3,4});
        //2.版本号 1字节
        buffer.writeByte(1);
        //3. 序列化算法   jdk 0，json 1   1字节
        MySerializer.Algorithm algorithm = Config.getSeriliztionAlgortithm();
        buffer.writeByte(algorithm.ordinal());
        //4. 消息类型   1字节
        buffer.writeByte(message.getMessageType());
        //5. 请求序号 4字节
        buffer.writeInt(message.getSequenceId());
        //对齐填充 1字节
        buffer.writeByte(0xff);
        //6.正文长度,对象--->byte[]
        byte[] content = algorithm.serializ(message);
        buffer.writeInt(content.length);
        //7.写入内容,
        buffer.writeBytes(content);
        list.add(buffer);
    }

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf buffer, List<Object> list) throws Exception {
        //1.读取魔术
        int magicNum = buffer.readInt();
        //2.读取版本号
        byte version = buffer.readByte();
        //3.读取序列化算法
        byte sequence = buffer.readByte();
        //4.读取消息类型
        byte msgType = buffer.readByte();
        //5.读取请求序号
        int sequenceId = buffer.readInt();
        //读取对齐字节
        buffer.readByte();
        //6.读取消息长度
        int size = buffer.readInt();
        byte[] bytes = new byte[size];
        buffer.readBytes(bytes,0,size);

        //7.将byte转化为对象
        Class<?> clazz = Message.getMessageClass(msgType); //一定要传入准确的Class，否则反序列化容易出问题
        MySerializer.Algorithm algortithm = Config.getSeriliztionAlgortithm();
        Object msg = algortithm.deserializ(clazz, bytes);
        list.add(msg);
    }
}
