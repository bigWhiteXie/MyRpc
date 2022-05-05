package message;

import lombok.Data;

@Data
public class RpcResponseMessage extends Message{
    private Object returnValue;
    private Exception exception;
    @Override
    public int getMessageType() {
        return RPC_MESSAGE_TYPE_RESPONSE;
    }
}
