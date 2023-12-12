package org.folio.am.integration.messaging.config;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.am.integration.messaging.channel.handler.TrxOutboxHandler;
import org.folio.am.integration.messaging.channel.interceptor.DestinationSetterInterceptor;
import org.folio.am.integration.messaging.outbox.store.TrxOutboxStore;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.core.GenericMessagingTemplate;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;

@RequiredArgsConstructor
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingConfiguration {

  private final MessagingProperties messagingProperties;

  @Bean
  public List<ChannelInterceptor> interceptors() {
    return List.of(new DestinationSetterInterceptor());
  }

  @Bean
  public MessageHandler discoveryMessageHandler(TrxOutboxStore outboxStore) {
    return new TrxOutboxHandler(outboxStore);
  }

  @Bean("discovery")
  public MessageChannel discoveryChannel(@Qualifier("discoveryMessageHandler") MessageHandler handler,
    List<ChannelInterceptor> interceptors) {
    var channel = new ExecutorSubscribableChannel();
    channel.subscribe(handler);
    channel.setInterceptors(interceptors);

    return channel;
  }

  @Bean
  public GenericMessagingTemplate messagingTemplate(BeanFactory beanFactory) {
    var template = new GenericMessagingTemplate();

    template.setBeanFactory(beanFactory);
    template.setSendTimeout(messagingProperties.getSendTimeout());

    return template;
  }
}
