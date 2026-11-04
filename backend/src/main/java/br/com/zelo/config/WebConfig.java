package br.com.zelo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Endereços amigáveis das telas, os mesmos da versão online (vercel.json), para os links funcionarem nos dois ambientes. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/login").setViewName("forward:/index.html");
        for (String path : new String[] {"/dashboard", "/painel", "/tutor"}) {
            registry.addViewController(path).setViewName("forward:/dashboard.html");
        }
        for (String path : new String[] {"/apresentacao", "/pitch", "/slides"}) {
            registry.addViewController(path).setViewName("forward:/apresentacao.html");
        }
        for (String path : new String[] {"/video", "/demonstracao"}) {
            registry.addViewController(path).setViewName("forward:/video.html");
        }
    }
}
