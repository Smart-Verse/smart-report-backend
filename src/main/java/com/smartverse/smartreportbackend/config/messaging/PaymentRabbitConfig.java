package com.smartverse.smartreportbackend.config.messaging;

import com.smartverse.smartreportbackend_gen.messaging.RabbitConfig;
import com.smartverse.smartreportbackend_gen.messaging.RabbitExchange;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

@Configuration
@RabbitExchange("smart.payment.events")
public class PaymentRabbitConfig extends RabbitConfig {
    public static final String ROUTING_KEY = "payment.confirmed.REPORT";

    @Bean
    @Override
    public MessageConverter jsonMessageConverter() {
        var delegate = new Jackson2JsonMessageConverter();
        return new MessageConverter() {
            @Override
            public Message toMessage(Object object, MessageProperties properties) {
                return delegate.toMessage(object, properties);
            }

            @Override
            public Object fromMessage(Message message) {
                return new String(message.getBody(), StandardCharsets.UTF_8);
            }
        };
    }

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
