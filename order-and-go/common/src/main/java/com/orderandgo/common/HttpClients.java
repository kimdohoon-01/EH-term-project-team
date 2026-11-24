package com.orderandgo.common;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public final class HttpClients {
    private HttpClients() {}
    public static RestClient create(String url, String key) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        var builder=RestClient.builder().baseUrl(url).requestFactory(factory);
        if(key!=null) builder.defaultHeader("X-Internal-Key",key);
        return builder.build();
    }
}
