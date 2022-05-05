# MyRpc
该demo底层通过netty实现远程调用功能，主要包括粘包、半包问题的解决、自定义协议、以及动态代理的使用，该案例可以帮助大家更深入的了解netty以及rpc的原理。

# 一、实体类与工具类
## Message
 该类作为所有类型消息的父类，是一个抽象类，用常量表示消息类型，利用Map可以通过常量获取该消息类型的class(在对消息进行反序列化时需要用到)
 ```
 @Data
public abstract class Message implements Serializable {

    public static Class<?> getMessageClass(int messageType) {
        return messageClasses.get(messageType);
    }

    private int sequenceId;

    private int messageType;

    public abstract int getMessageType();

   
    public static final int RPC_MESSAGE_TYPE_REQUEST = 101;
    public static final int  RPC_MESSAGE_TYPE_RESPONSE = 102;

    private static final Map<Integer, Class<?>> messageClasses = new HashMap<>();
    static {
        messageClasses.put(RPC_MESSAGE_TYPE_REQUEST, RpcRequestMessage.class);
        messageClasses.put(RPC_MESSAGE_TYPE_RESPONSE, RpcResponseMessage.class);
    }
}
 ```
 **RpcRequestMessage**：请求调用消息，主要包括sequenceId(消息序号)、interfaceName、methodName、parameterTypes、parameters、returnType。
 该消息由客户端向服务器发送，服务端通过该消息的属性利用反射执行方法并返回结果。
 **RpcResponseMessage**：调用结果消息，主要包括returnValue、exception。服务端将方法调用的结果存储到该消息中，将其发给客户端。
 ## Config
 该类的功能是从配置文件中读取常规配置信息，像端口号、序列化方式等
 ```
 public class Config {
    private static Properties properties;
    static {
        InputStream resource = Config.class.getResourceAsStream("/application.properties");
        properties = new Properties();
        try {
            properties.load(resource);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static MySerializer.Algorithm getSeriliztionAlgortithm(){
        final String value = properties.getProperty("mySerializer.algorithm");
        if(value == null)
        {
            return MySerializer.Algorithm.Java;
        }else{
            // 拼接成  MySerializer.Algorithm.Java 或 MySerializer.Algorithm.Json
            return MySerializer.Algorithm.valueOf(value);
        }
    }
}

 ```
 ## ServiceFactory
 该类用来读取配置文件中支持远程调用的接口以及对应实现类，用map存储。该类提供通过接口类型得到对应实现类的方法
 ```
 public class ServiceFactory {
    private static Properties properties;
    private static Map<Class<?>,Object> map = new ConcurrentHashMap<>();

    static {
        InputStream resource = ServiceFactory.class.getResourceAsStream("/application.properties");
        properties = new Properties();
        try {
            properties.load(resource);
            Set<String> strings = properties.stringPropertyNames();
            for(String s:strings){
                if(s.endsWith("Service")){
                    Class<?> interfaceClass = Class.forName(s);
                    Class<?> instantClass = Class.forName(properties.getProperty(s));
                    map.put(interfaceClass,instantClass.newInstance());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public  static <T> T getServiceImpl(Class<T> clazz){
        return (T) map.get(clazz);
    }
}
```
# 二、自定义协议
所谓协议就是服务端与客户端约定好按照一定的格式对消息进行编解码，一个协议通常包含以下内容：
1. 魔数，用来在第一时间判定是否是无效数据包
2. 版本号，可以支持协议的升级
3. 序列化算法，消息正文到底采用哪种序列化反序列化方式，可以由此扩展，例如：json、protobuf、hessian、jdk
4. 指令类型，是登录、注册、单聊、群聊... 跟业务相关
5. 请求序号，为了双工通信，提供异步能力
6. 正文长度
7. 消息正文
```
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
```
# 三、客户端
## RpcResponseHandler
该类用来处理调用结果的响应，内部由属性map存储sequenceId--->Promise的键值对，客户端发送消息前会将sequenceId和Promise添加到该map中，并利用promise阻塞当前线程直到客户端收到响应结果，
将结果存储到promise中(当前线程指的是发送消息的线程，接收响应的线程是eventloop线程)，该线程才会被唤醒并从promise中获取结果
```
public class RpcResponseHandler extends SimpleChannelInboundHandler<RpcResponseMessage> {
    public static final Map<Integer, Promise> PROMISE = new ConcurrentHashMap<>();
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcResponseMessage msg) throws Exception {
        log.debug("response:{}",msg);
        Promise promise = PROMISE.get(msg.getSequenceId());
        Exception exception = msg.getException();
        if(exception != null){
            promise.setFailure(exception);
        }else{
          promise.setSuccess(msg.getReturnValue());
        }


    }
}
```

## ClientManager
使用ClientManager工具类让客户端能够调用远程服务端的方法并得到返回结果，底层使用动态代理，代理类中向服务端发送调用请求，得到调用结果响应后返回。
```
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
    /**
    使用双重检测锁，保证线程安全性的同时提高效率
    **/
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
            
            //将promise添加到responseHandler中
            RpcResponseHandler.PROMISE.put(sequenceId, promise);

            RpcRequestMessage rpcRequestMessage = new RpcRequestMessage(sequenceId, serviceClass.getName(), method.getName(), method.getParameterTypes(), method.getReturnType(), args);

            channel.writeAndFlush(rpcRequestMessage);


            //等待通信线程接收响应结果
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
```

# 四、服务端
服务端主要就是接收rpcRequestMessage，从它中获取interfaceName(利用它得到对应实现类的class)、methodName(找到对应method)等信息，使用反射来执行方法得到结果后
将其封装到rpcResponseMessage中再发回给客户端
```
public class RpcResponseHandler extends SimpleChannelInboundHandler<RpcResponseMessage> {
    public static final Map<Integer, Promise> PROMISE = new ConcurrentHashMap<>();
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcResponseMessage msg) throws Exception {
        log.debug("response:{}",msg);
        Promise promise = PROMISE.get(msg.getSequenceId());
        Exception exception = msg.getException();
        if(exception != null){
            promise.setFailure(exception);
        }else{
          promise.setSuccess(msg.getReturnValue());
        }
    }
}
```
 
