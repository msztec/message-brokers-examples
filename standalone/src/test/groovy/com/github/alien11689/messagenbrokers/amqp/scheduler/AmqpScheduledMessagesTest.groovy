package com.github.alien11689.messagenbrokers.amqp.scheduler

import com.github.alien11689.messagenbrokers.helper.Docker
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import spock.lang.AutoCleanup
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import static com.github.alien11689.messagenbrokers.amqp.RmqConnectionFactory.RMQ_CONNECTION_FACTORY

@Requires({ Docker.isRunning('rmqwithscheduler') })
class AmqpScheduledMessagesTest extends Specification {
    String exchange = 'my-exchange'
    String consumerText

    @Unroll
    def 'should send message with 8 second delay to queue #currentUuid'() {
        given:
            String queue = "scheduledMessageQueue-$currentUuid"
            createQueue(queue)
            createConsumer(queue)
            println("Send time: ${new Date()}")
        when:
            channel.basicPublish(exchange,
                queue,
                new AMQP.BasicProperties.Builder().headers('x-delay': 8000).build(),
                "Message: $currentUuid".bytes)
        then:
            Thread.sleep(5000)
        and:
            consumerText == null
        and:
            new PollingConditions().within(5) {
                consumerText != null
            }
        where:
            currentUuid << [UUID.randomUUID()]
    }

    private void createQueue(String queue) {
        channel.exchangeDeclare(exchange, 'x-delayed-message', true, false, ['x-delayed-type': 'direct'])
        channel.queueDeclare(queue, true, false, false, null)
        channel.queueBind(queue, exchange, queue)
    }

    private void createConsumer(String queue) {
        consumerChannel.queueDeclare(queue, true, false, false, null)
        Consumer consumer = new DefaultConsumer(consumerChannel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
                println("Message received at ${new Date()}")
                consumerText = new String(body, 'UTF-8')
            }
        }
        consumerChannel.basicConsume(queue, true, consumer)
    }

    @AutoCleanup(quiet = true)
    Connection connection = RMQ_CONNECTION_FACTORY.newConnection()

    @AutoCleanup(quiet = true)
    Channel channel = connection.createChannel()

    @AutoCleanup(quiet = true)
    Channel consumerChannel = connection.createChannel()

}
