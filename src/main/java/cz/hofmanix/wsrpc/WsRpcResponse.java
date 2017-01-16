package cz.hofmanix.wsrpc;

/**
 * Created by ondre on 28.09.2016.
 */
public class WsRpcResponse<T> {
    private String path;
    private String responseKey;
    private T data;
    private Throwable error;

    public WsRpcResponse(String path, T data) {
        this.path = path;
        this.data = data;
    }

    public WsRpcResponse(T data) {
        this.data = data;
    }

    public WsRpcResponse() {

    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getResponseKey() {
        return responseKey;
    }

    public void setResponseKey(String responseKey) {
        this.responseKey = responseKey;
    }

    public Throwable getError() {
        return error;
    }

    public void setError(Throwable error) {
        this.error = error;
    }
}
