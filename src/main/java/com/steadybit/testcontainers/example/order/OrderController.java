package com.steadybit.testcontainers.example.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private static final Logger log = LoggerFactory.getLogger(OrderController.class);
    private final OrderRepository repository;
    private final OrderMessagingAdapter messageAdapter;

    public OrderController(OrderRepository repository, OrderMessagingAdapter orderMessageAdapter) {
        this.repository = repository;
        this.messageAdapter = orderMessageAdapter;
    }

    @PostMapping
    public Order createOrder(@RequestBody Order body) {
        var order = this.repository.save(body);
        log.info("Created order {}", order.getId());
        this.messageAdapter.publishOrderCreated(order);
        return order;
    }

}
