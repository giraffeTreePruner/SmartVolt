package com.codedgiraffe.smartvolt.mqtt;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
//Spring Imports
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.integration.endpoint.MessageProducerSupport;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
// Java msc
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

@Configuration
@EnableIntegration
public class MqttConfig {
    private static final Logger log = LoggerFactory.getLogger(MqttConfig.class);
    
    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    @Value("${mqtt.client.id}")
    private String clientId;

    @Value("${mqtt.username}")
    private String username;

    @Value("${mqtt.password}")
    private String password;

    @Value("${mqtt.topic.telemetry}")
    private String telemetryTopic;

    @Value ("${mqtt.topic.lwt}")
    private String lwtTopic;

    @Value("${mqtt.tls.ca-cert-path:}")
    private String caCertPath;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() throws IllegalStateException {
        
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{brokerUrl});
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        options.setCleanSession(false);
        options.setAutomaticReconnect(true);

        if (caCertPath != null && !caCertPath.isBlank()) {
            options.setSocketFactory(buildSslSocketFactory(caCertPath));
        }

        factory.setConnectionOptions(options);
        return factory;
    }

    // Builds SSL Socket Factory only for our smartvolt domain
    private javax.net.ssl.SSLSocketFactory buildSslSocketFactory(String caCertPath) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate caCert;
            try (FileInputStream fis = new FileInputStream(caCertPath)) {
                caCert = (X509Certificate) cf.generateCertificate(fis);
            }
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            ks.setCertificateEntry("letsencrypt-ca", caCert);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to establish TLS encryption. Aborting...", e);
        }
    }

    // Inbound: broker -> Spring
    @Bean
    public MessageProducerSupport mqttInbound() {
        MqttPahoMessageDrivenChannelAdapter adapter =
            new MqttPahoMessageDrivenChannelAdapter(
                clientId + "-inbound", mqttClientFactory(), telemetryTopic, lwtTopic);
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(new int[] {0, 1});
        adapter.setOutputChannel(mqttInputChannel());
        return adapter;
    }

    //Outbound: Spring -> broker
    @Bean
    @ServiceActivator(inputChannel = "mqttOutputChannel")
    public MessageHandler mqttOutbound() {
        MqttPahoMessageHandler handler =
            new MqttPahoMessageHandler(clientId + "-outbound", mqttClientFactory());
        handler.setAsync(true);
        handler.setDefaultQos(1);
        handler.setDefaultRetained(true);
        return handler;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel mqttOutputChannel() {
        return new DirectChannel();
    }
}
