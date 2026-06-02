package com.fenixcore.optisaludplus.core.web;

import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Drop-in replacement for Spring Data's default {@link PageableHandlerMethodArgumentResolver}
 * that adds two equivalent ways to bypass pagination and request the full dataset:
 *
 * <ul>
 *   <li>{@code ?size=-1} — sentinel value passed through the same {@code size} param.</li>
 *   <li>{@code ?unpaged=true} — explicit flag, more self-documenting for callers.</li>
 * </ul>
 *
 * <p>Either one resolves the {@link Pageable} method argument to {@link Pageable#unpaged()}.
 * Any other input falls through to the parent implementation (standard {@code page} /
 * {@code size} / {@code sort} handling plus {@code @PageableDefault} resolution).</p>
 *
 * <p>Registered as a {@link org.springframework.context.annotation.Bean} in
 * {@code core.config.PaginationConfig} — Spring Boot's autoconfigured resolver
 * is conditional on missing bean, so this overrides it project-wide.</p>
 */
public class UnpagedAwarePageableArgumentResolver extends PageableHandlerMethodArgumentResolver {

    @Override
    public Pageable resolveArgument(
            MethodParameter methodParameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {

        String unpagedParam = webRequest.getParameter("unpaged");
        String sizeParam = webRequest.getParameter("size");

        boolean wantsUnpaged = "true".equalsIgnoreCase(unpagedParam) || "-1".equals(sizeParam);
        if (wantsUnpaged) {
            return Pageable.unpaged();
        }
        return super.resolveArgument(methodParameter, mavContainer, webRequest, binderFactory);
    }
}
