package com.labtime.users.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

// Identico en proposito al de labtime: mete el sub de Cognito en el
// MDC (para el patron de logback) y deja las lineas event=http.request /
// event=http.response de cada peticion.
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
