package com.ramonnnarokomo.hucha.shared;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP y navegador de quien hace la petición, para auditoría y la lista de sesiones.
 *
 * <p>No se lee X-Forwarded-For a mano: cualquiera puede inventárselo. Con
 * {@code server.forward-headers-strategy=native} Tomcat solo lo acepta si viene de un proxy de la
 * red interna (el nginx de docker-compose) y entonces {@code getRemoteAddr()} ya da la IP real.
 */
public record RequestInfo(String ipAddress, String userAgent) {

    public static RequestInfo from(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && userAgent.length() > 255) {
            userAgent = userAgent.substring(0, 255);
        }
        return new RequestInfo(ip, userAgent);
    }

    public static RequestInfo unknown() {
        return new RequestInfo(null, null);
    }
}
