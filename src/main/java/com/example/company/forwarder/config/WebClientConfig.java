package com.example.company.forwarder.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Clock;

@Configuration
public class WebClientConfig {

    /**
     * WebClient gọi API Gateway, có timeout (forwarder.http). Không có timeout thì gateway treo là request của đối tác
     * treo theo, chiếm kết nối mãi.
     */
    @Bean
    public WebClient webClient(ForwarderProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) properties.http().connectTimeout().toMillis())
                .responseTimeout(properties.http().responseTimeout());
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * Giờ của máy (Việt Nam), để created_at/updated_at trong forwarder_log cùng múi giờ với CURRENT_TIMESTAMP của MySQL
     * và log của eBank. X-Timestamp là epoch giây nên không phụ thuộc múi giờ.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
