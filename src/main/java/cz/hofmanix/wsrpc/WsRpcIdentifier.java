package cz.hofmanix.wsrpc;

/**
 * Created by hofmanix on 14.01.17.
 */
public abstract class WsRpcIdentifier {

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof WsRpcIdentifier && ((WsRpcIdentifier) obj).getId() == getId());
    }

    public abstract long getId();
}
