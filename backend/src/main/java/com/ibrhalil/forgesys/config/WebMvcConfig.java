package com.ibrhalil.forgesys.config;

import com.ibrhalil.forgesys.web.filter.SearchQueryArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** Registers the K-55 {@code ?sq=} argument resolver for GET list endpoints. */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final SearchQueryArgumentResolver searchQueryArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(searchQueryArgumentResolver);
    }
}
