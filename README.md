#SockJS RPC for Spring Boot

## Remote Procedure Call like library with use of and Spring Boot Starter WebSockets

Library can be used with ng2-sockjs-rpc plugin for angular 2 for calling methods from client to server and from server to client

## Configuration
```java
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Autowired;
import cz.hofmanix.wsrpc.WsRpcHandler;
public class WebSocketsConfig extends WebSocketConfigurer {
    
    @Autowired
    public WsRpcHandler wsRpcHandler;
    
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(wsRpcHandler, "/site").withSockJS();
    }
    
    @Bean
    public WsRpcHandler wsRpcHandler() {
        return new WsRpcHandler();
    }
}
```

## Controller
```java
import cz.hofmanix.wsrpc.WsRpcController;
@WsRpcController
public class SomeController {
    
    public String someMethod(String name) {
        return "Hello " + name;
    }
}
```

Function will now accepts argument from given call from client and returns Hello argument to the client;