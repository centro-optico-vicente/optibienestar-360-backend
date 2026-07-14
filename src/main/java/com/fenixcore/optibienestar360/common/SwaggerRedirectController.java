package com.fenixcore.optibienestar360.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

/**
 * 301 redirects to land users on the canonical Swagger UI URL whenever they
 * hit a near-miss variant that would otherwise return 404 (no static resource
 * mapped) or render a blank page (relative asset paths broken).
 *
 * <p>Handled variants → all redirect to {@code /swagger-ui/index.html}:
 * <ul>
 *   <li>{@code /swagger-ui}             — bare path, no trailing slash, no file</li>
 *   <li>{@code /swagger-ui/}            — directory-style with trailing slash</li>
 *   <li>{@code /swagger-ui/index.html/} — file with extra trailing slash that
 *       breaks browser asset resolution (loads as blank page)</li>
 * </ul>
 *
 * <p>Public — no auth (covered by {@code /swagger-ui/**} and the explicit
 * {@code /swagger-ui} entry in {@link com.fenixcore.optibienestar360.security.SecurityConfig}).
 */
@RestController
public class SwaggerRedirectController {

    @GetMapping({
        "/swagger-ui",
        "/swagger-ui/",
        "/swagger-ui/index.html/"
    })
    public RedirectView redirectToSwaggerIndex() {
        RedirectView view = new RedirectView("/swagger-ui/index.html");
        view.setStatusCode(HttpStatus.MOVED_PERMANENTLY);
        return view;
    }
}
