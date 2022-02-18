package com.steadybit.testcontainers.example.order;

import com.steadybit.testcontainers.example.dto.OrderCreatedEvent;
import org.apache.activemq.command.ActiveMQQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.transaction.Transactional;
import java.time.Instant;

@Component
public class OrderMessagingAdapter {
    private static final Logger log = LoggerFactory.getLogger(OrderMessagingAdapter.class);
    private final ActiveMQQueue destination = new ActiveMQQueue("order_created");
    private final JmsTemplate jmsTemplate;
    private final OrderRepository repository;

    public OrderMessagingAdapter(JmsTemplate jmsTemplate, OrderRepository repository) {
        this.jmsTemplate = jmsTemplate;
        this.repository = repository;
    }

    @Async
    @Transactional
    public void publishOrderCreated(Order order) {
        this.repository.markAsPublished(order.getId(), Instant.now());
        this.jmsTemplate.convertAndSend(destination, toEvent(order));
        log.info("Published order {}", order.getId());
    }

    @Scheduled(fixedDelay = 5_000L)
    @Transactional
    public void publishPendingOrders() {
        for (Order order : this.repository.findPublishPending()) {
            this.publishOrderCreated(order);
        }
    }

    private OrderCreatedEvent toEvent(Order order) {
        var event = new OrderCreatedEvent();
        event.setId(order.getId());
        event.setName(order.getName());
        event.setAddress(order.getAddress());
        event.setItems(order.getItems().stream().map(orderItem -> {
            var item = new OrderCreatedEvent.Item();
            item.setName(orderItem.getName());
            item.setQuantity(orderItem.getQuantity());
            return item;
        }).toList());
        return event;
    }
}
