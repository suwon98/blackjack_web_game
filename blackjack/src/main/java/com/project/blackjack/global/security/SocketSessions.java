package com.project.blackjack.global.security;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
@Component public class SocketSessions {
 public record Session(String username,String token) {}
 private final ConcurrentHashMap<String,Session> values=new ConcurrentHashMap<>();
 public void put(String id,String user,String token){values.put(id,new Session(user,token));}
 public Session get(String id){return id==null?null:values.get(id);}
 public void remove(String id){if(id!=null)values.remove(id);}
 @EventListener public void disconnected(SessionDisconnectEvent e){remove(e.getSessionId());}
}
