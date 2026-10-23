package com.yowpainter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        // Client HTTP du JDK, pas HttpURLConnection (SimpleClientHttpRequestFactory) : sur un 401 en
        // reponse a un POST, HttpURLConnection rend le corps illisible. Chaque refus du noyau (mot de
        // passe faux, e-mail non verifie...) arrivait alors vide et passait pour une erreur de
        // client-id / api-key.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().requestFactory(requestFactory);
    }
}
