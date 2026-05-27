package com.lms.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private final AvatarProperties avatarProperties;

    public StaticResourceConfig(AvatarProperties avatarProperties) {
        this.avatarProperties = avatarProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String storagePath = avatarProperties.getStoragePath();
        // Ensure the location ends with a slash for Spring's resource handler
        String location = "file:" + (storagePath.endsWith("/") ? storagePath : storagePath + "/");
        registry.addResourceHandler("/static/avatars/**")
                .addResourceLocations(location);
    }
}
