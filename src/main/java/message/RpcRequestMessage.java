package message;

import lombok.Data;

@Data
public class RpcRequestMessage extends Message{
    private String interfaceName;
    private String methodName;
    private Class[] parameterTypes;
    private Class<?> returnType;
    private Object[] parameters;

    public RpcRequestMessage(int sequenceId,String interfaceName, String methodName, Class[] parameterTypes, Class<?> returnType, Object[] parameters) {
        super.setSequenceId(sequenceId);
        this.interfaceName = interfaceName;
        this.methodName = methodName;
        this.parameterTypes = parameterTypes;
        this.returnType = returnType;
        this.parameters = parameters;
    }

    @Override
    public int getMessageType() {
        return RPC_MESSAGE_TYPE_REQUEST;
    }
}
