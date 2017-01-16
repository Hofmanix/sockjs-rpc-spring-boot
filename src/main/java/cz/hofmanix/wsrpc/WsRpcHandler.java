package cz.hofmanix.wsrpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by hofmanix on 30.12.16.
 */
@DependsOn
public class WsRpcHandler extends TextWebSocketHandler implements ApplicationListener<ContextRefreshedEvent> {

    public static final Logger LOGGER = LoggerFactory.getLogger(WsRpcHandler.class);

    private final HashMap<WebSocketSession, WsRpcIdentifier> sessions = new HashMap<>();
    private final HashMap<Class, Object> controllers = new HashMap<>();
    private final HashMap<String, Method> handlers = new HashMap<>();

    @Autowired
    private ListableBeanFactory listableBeanFactory;


    public WsRpcHandler() {
        super();
    }


    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
        LOGGER.info("Connection established with session " + session);
        synchronized (sessions) {
            sessions.put(session, null);
        }
        WsRpcResponse<String> responseKeyResponse = new WsRpcResponse<String>("createResponseKey", session.getId());
        sendTo(responseKeyResponse, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        super.handleTextMessage(session, message);
        LOGGER.info("Got message " + message.getPayload() + " from " + session);
        WsRpcRequest request = parseRequest(message.getPayload(), Object.class);
        if (handlers.containsKey(request.getPath())) {
            Method method = handlers.get(request.getPath());
            if (method.getParameterCount() == 0) {
                method.invoke(controllers.get(method.getDeclaringClass()));
            } else {
                callMethod(method, session, message, request);
            }
        } else {
            throw new NoSuchMethodException("Handler for this path doesn't exists");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
        LOGGER.info("Connection closed with session " + session);
        synchronized (sessions) {
            sessions.remove(session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        super.handleTransportError(session, exception);
        LOGGER.error("Error with session " + session, exception);
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        controllers.clear();
        handlers.clear();
        for(Object bean: listableBeanFactory.getBeansWithAnnotation(WsRpcController.class).values()) {
            Class cls = bean.getClass();
            String path = cls.getSimpleName();
            controllers.put(bean.getClass(), bean);
            for(Method method: cls.getDeclaredMethods()) {
                if(Modifier.isPublic(method.getModifiers())) {
                    String fullPath = path + "." + method.getName();
                    handlers.put(fullPath, method);
                    LOGGER.info("Mapped remote method [" + fullPath + "] onto handler of type [" + method.toString() + "]");
                }
            }
        }
    }

    public void setSessionIdentifier(WebSocketSession session, WsRpcIdentifier identifier) {
        synchronized (sessions) {
            if (sessions.containsValue(identifier)) {
                WebSocketSession sessionToRemove = null;
                for(Map.Entry<WebSocketSession, WsRpcIdentifier> entry: sessions.entrySet()) {
                    if(entry.getValue().equals(identifier) && !entry.getKey().equals(session)) {
                        try {
                            sessionToRemove = entry.getKey();
                            if(session.isOpen()) {
                                entry.getKey().close();
                            }
                        } catch(IOException ex) {
                            LOGGER.warn("Error at closing session");
                        } finally {
                            break;
                        }
                    }
                }
                if(sessionToRemove != null) {
                    sessions.remove(sessionToRemove);
                }
            }
            sessions.put(session, identifier);
        }
    }

    public HashMap<WebSocketSession, WsRpcIdentifier> getSessions() {
        return sessions;
    }

    public <T> void callAll(String method, T data) {
        WsRpcResponse<T> callRequest = new WsRpcResponse<T>(method, data);
        sendToAll(callRequest);
    }

    public <T> void call(String method, T data, WebSocketSession... sessions) {
        WsRpcResponse<T> callRequest = new WsRpcResponse<T>(method, data);
        sendTo(callRequest, sessions);
    }

    public <T> void call(String method, T data, WsRpcIdentifier... identifiers) {
        WsRpcResponse<T> callRequest = new WsRpcResponse<T>(method, data);
        sendTo(Arrays.asList(identifiers), callRequest);
    }

    private void callMethod(Method method, WebSocketSession session, TextMessage message, WsRpcRequest request) {
        Object[] parameterValues = new Object[method.getParameterCount()];
        for(int i = 0; i < parameterValues.length; i++) {
            Parameter parameter = method.getParameters()[i];
            if (parameter.getType().equals(WebSocketSession.class)) {
                parameterValues[i] = session;
            } else if (parameter.getType().equals(TextMessage.class)) {
                parameterValues[i] = message;
            } else if (parameter.getType().equals(WsRpcIdentifier.class)) {
                parameterValues[i] = sessions.get(session);
            } else if (parameter.getType().equals(WsRpcRequest.class)) {
                parameterValues[i] = request;
            } else {
                parameterValues[i] = parseRequest(message.getPayload(), parameter.getType()).getData();
            }
        }

        try {
            Object response = method.invoke(controllers.get(method.getDeclaringClass()), parameterValues);
            LOGGER.info("Returning response");
            WsRpcResponse wsResponse = createMethodResponse(request.getResponseKey(), response);
            sendTo(wsResponse, session);
        } catch (InvocationTargetException ex) {
            LOGGER.info("Returning error");
            WsRpcResponse<Object> response = new WsRpcResponse<>();
            response.setResponseKey(request.getResponseKey());
            response.setError(ex.getCause());
            sendTo(response, session);
        } catch(Exception ex) {
            LOGGER.error("WebSocket invoke method error", ex);
        }
    }

    private <T> WsRpcRequest<T> parseRequest(String payload, Class<T> dataType) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            JavaType type = mapper.getTypeFactory().constructParametricType(WsRpcRequest.class, dataType);
            WsRpcRequest<T> request = mapper.readValue(payload, type);
            return request;
        } catch(Exception ex) {
            return null;
        }
    }

    private <T> WsRpcResponse<T> createMethodResponse(String responseKey, T responseData) {
        WsRpcResponse<T> response = new WsRpcResponse<T>(responseData);
        response.setResponseKey(responseKey);
        return response;
    }

    private void sendToAll(WsRpcResponse message) {
        TextMessage textMessage = convertMessage(message);
        synchronized (sessions) {
            sessions.forEach((session, player) -> {
                sendTo(session, textMessage);
            });
        }
    }

    private void sendTo(WsRpcResponse message, WebSocketSession... sessions) {
        for(WebSocketSession session: sessions) {
            sendTo(session, convertMessage(message));
        }
    }

    private void sendTo(List<WsRpcIdentifier> identifiers, WsRpcResponse message) {
        synchronized (sessions) {
            sessions.entrySet().stream()
                    .filter(entry -> identifiers.contains(entry.getValue()))
                    .forEach((entry) -> sendTo(entry.getKey(), convertMessage(message)));
        }
    }

    private void sendTo(WebSocketSession session, TextMessage message) {
        try {
            if(session.isOpen()) {
                session.sendMessage(message);
            } else {
                LOGGER.warn("sendTo", session.toString() + " is not opened, not sending message " + message.getPayload());
            }
        } catch(Exception ex) {
            LOGGER.error("sendTo", "Exception " + ex.getMessage() + " thrown when sending message " + message.getPayload() + " to " + session.toString(), ex);
        }
    }

    private TextMessage convertMessage(WsRpcResponse message) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            TextMessage textMessage = new TextMessage(mapper.writeValueAsString(message));
            return textMessage;
        } catch(JsonProcessingException ex) {
            throw new RuntimeException("Error parsing message to send", ex);
        }
    }
}
