package com.vivire.locapet.app.global.security

import com.vivire.locapet.app.global.auth.JwtTokenProvider
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider
) : OncePerRequestFilter() {

    companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = resolveToken(request)

        if (token != null) {
            try {
                val claims = jwtTokenProvider.validateAndGetClaims(token)
                val tokenType = claims["type"] as? String

                if (tokenType == "ACCESS") {
                    val memberId = claims.subject.toLong()
                    val role = claims["role"] as? String ?: "USER"

                    val authorities = listOf(SimpleGrantedAuthority("ROLE_$role"))
                    val authentication = UsernamePasswordAuthenticationToken(
                        memberId, null, authorities
                    )
                    SecurityContextHolder.getContext().authentication = authentication
                }
            } catch (_: Exception) {
                // 토큰 검증 실패 시 인증 정보를 설정하지 않음
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader(AUTHORIZATION_HEADER) ?: return null
        if (bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length)
        }
        return null
    }
}
