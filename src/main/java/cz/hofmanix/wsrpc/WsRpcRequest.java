package cz.hofmanix.wsrpc;

/**
 * Created by ondre on 26.09.2016.
 */
public class WsRpcRequest<T> {
    private String path;
    private String responseKey;
    private T data;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getResponseKey() {
        return responseKey;
    }

    public void setResponseKey(String responseKey) {
        this.responseKey = responseKey;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
