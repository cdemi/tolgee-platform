package io.tolgee.configuration

import io.tolgee.configuration.tolgee.TolgeeProperties
import io.tolgee.security.InternalDenyFilter
import io.tolgee.security.JwtTokenFilter
import io.tolgee.security.api_key_auth.ApiKeyAuthFilter
import io.tolgee.security.repository_auth.RepositoryPermissionFilter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.BeanIds
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
open class WebSecurityConfig @Autowired constructor(private val jwtTokenFilter: JwtTokenFilter,
                                                    private val configuration: TolgeeProperties,
                                                    private val apiKeyAuthFilter: ApiKeyAuthFilter,
                                                    private val internalDenyFilter: InternalDenyFilter,
                                                    private val repositoryPermissionFilter: RepositoryPermissionFilter
) : WebSecurityConfigurerAdapter() {
    override fun configure(http: HttpSecurity) {
        if (configuration.authentication.enabled) {
            http
                    .csrf().disable().cors().and()
                    .addFilterBefore(internalDenyFilter, UsernamePasswordAuthenticationFilter::class.java)
                    //if jwt token is provided in header, this filter will authorize user, so the request is not gonna reach the ldap auth
                    .addFilterBefore(jwtTokenFilter, UsernamePasswordAuthenticationFilter::class.java)
                    //this is used to authorize user's app calls with generated api key
                    .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
                    .addFilterAfter(repositoryPermissionFilter, JwtTokenFilter::class.java)
                    .authorizeRequests()
                    .antMatchers("/api/public/**", "/webjars/**", "/swagger-ui.html", "/swagger-resources/**", "/v2/api-docs").permitAll()
                    .antMatchers("/api/**", "/uaa", "/uaa/**").authenticated()
                    .and().sessionManagement()
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
            return
        }
        http
                .csrf().disable()
                .cors().and()
                .authorizeRequests().anyRequest().permitAll()
    }

    override fun configure(auth: AuthenticationManagerBuilder) {
        val ldapConfiguration = configuration.authentication.ldap
        if (ldapConfiguration.enabled) {
            auth
                    .ldapAuthentication()
                    .contextSource()
                    .url(ldapConfiguration.urls + ldapConfiguration.baseDn)
                    .managerDn(ldapConfiguration.securityPrincipal)
                    .managerPassword(ldapConfiguration.principalPassword)
                    .and()
                    .userDnPatterns(ldapConfiguration.userDnPattern)
            return
        }
    }

    @Bean(BeanIds.AUTHENTICATION_MANAGER)
    override fun authenticationManagerBean(): AuthenticationManager {
        return super.authenticationManagerBean()
    }
}
