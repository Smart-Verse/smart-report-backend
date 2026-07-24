package com.smartverse.smartreportbackend.config.messaging;

import com.smartverse.smartreportbackend_gen.messaging.RabbitConfig;
import com.smartverse.smartreportbackend_gen.messaging.RabbitExchange;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RabbitExchange("smart.payment.events")
public class PaymentRabbitConfig extends RabbitConfig {
    public static final String ROUTING_KEY = "payment.confirmed.REPORT";

    @Bean
    public Queue paymentConfirmedQueue(
            @Value("${PAYMENT_CONFIRMED_QUEUE:smart.payment.confirmed.report}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding paymentConfirmedBinding(Queue paymentConfirmedQueue, TopicExchange appTopicExchange) {
        return BindingBuilder.bind(paymentConfirmedQueue).to(appTopicExchange).with(ROUTING_KEY);
    }
}
