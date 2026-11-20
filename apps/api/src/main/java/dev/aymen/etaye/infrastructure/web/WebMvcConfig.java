package dev.aymen.etaye.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.FlashMapManager;

@Configuration
public class WebMvcConfig {

    @Bean
    public FlashMapManager flashMapManager() {
        return new FlashMapManager() {
            @Override
            public FlashMap retrieveAndUpdate(HttpServletRequest request, HttpServletResponse response) {
                return null;
            }

            @Override
            public void saveOutputFlashMap(FlashMap flashMap, HttpServletRequest request, HttpServletResponse response) {
            }
        };
    }
}
