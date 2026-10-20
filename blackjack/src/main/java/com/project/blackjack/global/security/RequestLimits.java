package com.project.blackjack.global.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.util.concurrent.ConcurrentHashMap;
public class RequestLimits extends OncePerRequestFilter {
    private record Window(long minute,int count) {}
    private final ConcurrentHashMap<String,Window> counts=new ConcurrentHashMap<>();
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws IOException,ServletException {
        String path=req.getRequestURI();
        if(!path.startsWith("/api/") && !path.startsWith("/ws-blackjack")) { chain.doFilter(req,res);return; }
        long minute=System.currentTimeMillis()/60000;
        boolean auth=path.matches("/api/members/(login|join|reissue)");
        String key=req.getRemoteAddr()+":"+(auth?"auth":"api");
        if(counts.size()>10000) { counts.entrySet().removeIf(e -> e.getValue().minute()<minute);if(counts.size()>10000) { res.setStatus(429);return; } }
        Window w=counts.compute(key,(k,old) -> new Window(minute,old==null || old.minute()!=minute?1:old.count()+1));
        if(w.count()>(auth?30:300)) { res.setHeader("Retry-After","60");res.setStatus(429);return; }
        if("POST".equals(req.getMethod()) && path.startsWith("/api/")) {
            byte[] body=req.getInputStream().readNBytes(16385);
            if(body.length>16384) { res.setStatus(413);return; }
            HttpServletRequest wrapped=new HttpServletRequestWrapper(req) {
                @Override public ServletInputStream getInputStream() {
                    ByteArrayInputStream input=new ByteArrayInputStream(body);
                    return new ServletInputStream() {
                        public int read(){return input.read();}
                        public boolean isFinished(){return input.available()==0;}
                        public boolean isReady(){return true;}
                        public void setReadListener(ReadListener listener){throw new UnsupportedOperationException();}
                    };
                }
                @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8));}
            };
            chain.doFilter(wrapped,res);
        } else chain.doFilter(req,res);
    }
}
