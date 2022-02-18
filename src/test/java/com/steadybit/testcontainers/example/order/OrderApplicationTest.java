package com.steadybit.testcontainers.example.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.steadybit.testcontainers.Steadybit;
import com.steadybit.testcontainers.example.dto.OrderCreatedEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jms.UncategorizedJmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderApplicationTest {
    @Container
    public static final ActiveMqContainer broker = new ActiveMqContainer("rmohr/activemq:5.15.9");
    @Container
    public static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:13");
    private static final HttpEntity<String> CREATE_ORDER_BODY = asJsonBody("""
            {
                "name" : "Johannes",
                "address" : "Germany",
                "items" : [
                    { "name" : "The Wrong Trousers", "quantity": 1, "price": 9.99 },
                    { "name" : "A Grant Day Out", "quantity": 1, "price": 9.99 }
                ]
            }
            """);
    @Autowired
    TestRestTemplate http;
    @Autowired
    JmsTemplate jms;
    @Autowired
    RecordingAsyncUncaughtExceptionHandler asyncUncaughtExceptionHandler;

    @BeforeEach
    void setUp() {
        this.drainJmsQueue();
    }

    @Test
    void should_create_order() {
        var orderId = http.postForObject("/orders", CREATE_ORDER_BODY, JsonNode.class).get("id").asLong();
        var event = (OrderCreatedEvent) jms.receiveAndConvert("order_created");
        assertThat(event.getId()).isEqualTo(orderId);
    }

    @Test
    void should_create_order_within_2s() {
        var orderId = Steadybit.networkDelayPackages(Duration.ofSeconds(2))
                .forContainers(broker) //the container to attack
                .exec(() -> {
                    //This code will be executed while running the delay attack
                    return http.postForObject("/orders", CREATE_ORDER_BODY, JsonNode.class).get("id").asLong();
                });

        var event = (OrderCreatedEvent) jms.receiveAndConvert("order_created");
        assertThat(event.getId()).isEqualTo(orderId);
    }

    @Test
    void should_create_order_when_broker_is_offline() {
        var orderId = Steadybit.networkBlackhole()
                .forContainers(broker)
                .exec(() -> {
                    var id = http.postForObject("/orders", CREATE_ORDER_BODY, JsonNode.class).get("id").asLong();
                    //we need to wait for the exception while executing the attack
                    asyncUncaughtExceptionHandler.waitFor(UncategorizedJmsException.class);
                    return id;
                });

        await().untilAsserted(() -> {
            var event = (OrderCreatedEvent) jms.receiveAndConvert("order_created");
            assertThat(event).isNotNull();
            assertThat(event.getId()).isEqualTo(orderId);
        });
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
        registry.add("spring.activemq.broker-url", broker::getBrokerUrl);
    }

    @TestConfiguration
    static class TestConfig implements AsyncConfigurer {
        private final ApplicationContext context;

        TestConfig(ApplicationContext context) {
            this.context = context;
        }

        @Bean
        public RestTemplateBuilder restTemplateBuilder() {
            return new RestTemplateBuilder().setReadTimeout(Duration.ofSeconds(2));
        }

        @Bean
        public RecordingAsyncUncaughtExceptionHandler asyncUncaughtExceptionHandler() {
            return new RecordingAsyncUncaughtExceptionHandler();
        }

        @Override
        public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
            return context.getBean(RecordingAsyncUncaughtExceptionHandler.class);
        }
    }

    private static HttpEntity<String> asJsonBody(String s) {
        return new HttpEntity<>(s, new LinkedMultiValueMap<>(Map.of(HttpHeaders.CONTENT_TYPE, List.of(MediaType.APPLICATION_JSON_VALUE))));
    }

    private void drainJmsQueue() {
        long oldTimeout = jms.getReceiveTimeout();
        jms.setReceiveTimeout(10);
        while (jms.receiveAndConvert("order_created") != null) {
        }
        jms.setReceiveTimeout(oldTimeout);
    }
}

