package protocol;

import config.Config;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import lombok.extern.slf4j.Slf4j;
import message.Message;

import java.util.List;

/**
 * 消息编解码器，按照自定义的协议来进行编解码
 * * 魔数，用来在第一时间判定是否是无效数据包
 * * 版本号，可以支持协议的升级
 * * 序列化算法，消息正文到底采用哪种序列化反序列化方式，可以由此扩展，例如：json、protobuf、hessian、jdk
 * * 指令类型，是登录、注册、单聊、群聊... 跟业务相关
 * * 请求序号，为了双工通信，提供异步能力
 * * 正文长度
 * * 消息正文
 */
@Slf4j
public class MessageCodec extends ByteToMessageCodec<Message> {
    /**
     * 输出消息时候，对消息进行编码
     * @param ctx
     * @param message
     * @param buffer
     * @throws Exception
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, Message message, ByteBuf buffer) throws Exception {

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


    }

    /**
     * 消息入站的时候对消息进行解码
     * @param ctx
     * @param buffer
     * @param list
     * @throws Exception
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> list) throws Exception {
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
        log.debug("decode完成：{}",msg);
        list.add(msg);
    }
}
