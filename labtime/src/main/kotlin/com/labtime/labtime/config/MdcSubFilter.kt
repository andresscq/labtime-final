package com.labtime.labtime.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Se corre DESPUES del filtro que valida el JWT (BearerTokenAuthenticationFilter),
 * asi que para cuando esto se ejecuta, si habia token valido, el SecurityContext ya
 * tiene el JwtAuthenticationToken.
 *
 * Hace dos cosas que exige el estandar de logging (Criterio 2):
 *  1. Mete el "sub" de Cognito en el MDC -> el patron de logback lo estampa en TODAS
 *     las lineas de este request, sin pasarlo a mano por cada capa. Si no hay token,
 *     %X{sub:-anonimo} en el patron ya cubre "sub=anonimo".
 *  2. Deja una linea event=http.request al entrar y event=http.response (con el
 *     codigo HTTP) al salir, para que CUALQUIER peticion deje rastro aunque el
 *     controller/service no logueen nada explicito.
 */
@Component
class MdcSubFilter : OncePerRequestFilter() {

    private val appLogger = LoggerFactory.getLogger(MdcSubFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val auth = SecurityContextHolder.getContext().authentication
        val sub = (auth as? JwtAuthenticationToken)?.token?.subject
        try {
            if (sub != null) MDC.put("sub", sub)

            appLogger.info("event=http.request | msg=${request.method} ${request.requestURI}")
            filterChain.doFilter(request, response)
            appLogger.info("event=http.response | msg=${response.status} ${request.method} ${request.requestURI}")
        } finally {
            MDC.remove("sub")
        }
    }
}
