/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.systemtest.clients.lib;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.fabric8.kubernetes.api.model.LoadBalancerIngress;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
import io.strimzi.api.kafka.model.KafkaResources;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Properties;
import java.util.Random;

import static io.strimzi.api.kafka.model.KafkaResources.externalBootstrapServiceName;
import static io.strimzi.test.BaseITST.kubeClient;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressFBWarnings("REC_CATCH_EXCEPTION")
class KafkaClientProperties {

    private static final Logger LOGGER = LogManager.getLogger(KafkaClientProperties.class);

    /**
     * Create producer properties with PLAINTEXT security
     * @param namespace kafka namespace
     * @param clusterName kafka cluster name
     * @return producer properties
     */
    static Properties createProducerProperties(String namespace, String clusterName) {
        return createProducerProperties(namespace, clusterName, "", CommonClientConfigs.DEFAULT_SECURITY_PROTOCOL);
    }

    /**
     * Create producer properties with SSL security
     * @param namespace kafka namespace
     * @param clusterName kafka cluster name
     * @param userName user name for authorization
     * @param securityProtocol security protocol
     * @return producer configuration
     */
    static Properties createProducerProperties(String namespace, String clusterName, String userName, String securityProtocol) {
        Properties producerProperties = new Properties();
        producerProperties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                getExternalBootstrapConnect(namespace, clusterName));
        producerProperties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProperties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProperties.setProperty(ProducerConfig.MAX_BLOCK_MS_CONFIG, "1000");
        producerProperties.setProperty(CommonClientConfigs.CLIENT_ID_CONFIG, userName + "-producer");
        producerProperties.setProperty(ProducerConfig.ACKS_CONFIG, "all");

        producerProperties.putAll(sharedClientProperties(namespace, clusterName, userName, securityProtocol));

        return producerProperties;
    }

    /**
     * Create consumer properties with SSL security
     * @param namespace kafka namespace
     * @param clusterName kafka cluster name
     * @return consumer configuration
     */
    static Properties createConsumerProperties(String namespace, String clusterName) {
        return createConsumerProperties(namespace, clusterName, "", CommonClientConfigs.DEFAULT_SECURITY_PROTOCOL);
    }

    /**
     * Create consumer properties with SSL security
     * @param namespace kafka namespace
     * @param clusterName kafka cluster name
     * @param userName user name for authorization
     * @param securityProtocol security protocol
     * @return consumer configuration
     */
    static Properties createConsumerProperties(String namespace, String clusterName, String userName, String securityProtocol) {
        Properties consumerProperties = new Properties();
        consumerProperties.setProperty(ConsumerConfig.GROUP_ID_CONFIG,
                "my-group-" + new Random().nextInt(Integer.MAX_VALUE));
        consumerProperties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                getExternalBootstrapConnect(namespace, clusterName));
        consumerProperties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProperties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProperties.setProperty(CommonClientConfigs.CLIENT_ID_CONFIG, userName + "-consumer");
        consumerProperties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        consumerProperties.putAll(sharedClientProperties(namespace, clusterName, userName, securityProtocol));

        return consumerProperties;
    }

    /**
     * Create properties which are same pro producer and consumer
     * @param namespace kafka namespace
     * @param clusterName kafka cluster name
     * @param userName user name for authorization
     * @param securityProtocol security protocol
     * @return shared client properties
     */
    private static Properties sharedClientProperties(String namespace, String clusterName, String userName, String securityProtocol) {
        Properties properties = new Properties();
        // For turn off hostname verification
        properties.setProperty(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "");
        properties.setProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);

        try {
            Secret clusterCaCertSecret = kubeClient(namespace).getSecret(KafkaResources.clusterCaCertificateSecretName(clusterName));

            String tsPassword = new String(Base64.getDecoder().decode(clusterCaCertSecret.getData().get("ca.password")), StandardCharsets.US_ASCII);
            File tsFile = File.createTempFile(KafkaClientProperties.class.getName(), ".truststore");
            String truststore = clusterCaCertSecret.getData().get("ca.p12");
            Files.write(tsFile.toPath(), Base64.getDecoder().decode(truststore));
            tsFile.deleteOnExit();
            properties.setProperty(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "PKCS12");
            properties.setProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, tsPassword);
            properties.setProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, tsFile.getAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (!userName.isEmpty() && securityProtocol.equals(SecurityProtocol.SASL_SSL.name)) {
            properties.setProperty(SaslConfigs.SASL_MECHANISM, "SCRAM-SHA-512");
            Secret userSecret = kubeClient(namespace).getSecret(userName);
            String password = new String(Base64.getDecoder().decode(userSecret.getData().get("password")), Charset.forName("UTF-8"));

            String jaasTemplate = "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"%s\" password=\"%s\";";
            String jaasCfg = String.format(jaasTemplate, userName, password);

            properties.setProperty(SaslConfigs.SASL_JAAS_CONFIG, jaasCfg);
        } else if (!userName.isEmpty()) {
            try {

                Secret userSecret = kubeClient(namespace).getSecret(userName);

                String clientsCaCert = userSecret.getData().get("ca.crt");
                LOGGER.debug("Clients CA cert: {}", clientsCaCert);

                String userCaCert = userSecret.getData().get("user.crt");
                String userCaKey = userSecret.getData().get("user.key");
                String ksPassword = "foo";
                properties.setProperty(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, ksPassword);
                LOGGER.debug("User CA cert: {}", userCaCert);
                LOGGER.debug("User CA key: {}", userCaKey);
                File ksFile = createKeystore(Base64.getDecoder().decode(clientsCaCert),
                        Base64.getDecoder().decode(userCaCert),
                        Base64.getDecoder().decode(userCaKey),
                        ksPassword);
                properties.setProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, ksFile.getAbsolutePath());

                properties.setProperty(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return properties;

    }

    /**
     * Get external bootstrap connection
     * @param namespace kafka namespace
     * @param clusterName kafka cluster name
     * @return bootstrap url as string
     */
    private static String getExternalBootstrapConnect(String namespace, String clusterName) {
        if (kubeClient(namespace).getClient().isAdaptable(OpenShiftClient.class)) {
            Route route = kubeClient(namespace).getClient().adapt(OpenShiftClient.class).routes().inNamespace(namespace).withName(clusterName + "-kafka-bootstrap").get();
            if (route != null && !route.getStatus().getIngress().isEmpty()) {
                return route.getStatus().getIngress().get(0).getHost() + ":443";
            }
        }

        Service extBootstrapService = kubeClient(namespace).getClient().services()
                .inNamespace(namespace)
                .withName(externalBootstrapServiceName(clusterName))
                .get();

        if (extBootstrapService == null) {
            throw new RuntimeException("Kafka cluster " + clusterName + " doesn't have an external bootstrap service");
        }

        String extBootstrapServiceType = extBootstrapService.getSpec().getType();

        if (extBootstrapServiceType.equals("NodePort")) {
            int port = extBootstrapService.getSpec().getPorts().get(0).getNodePort();
            String externalAddress = kubeClient(namespace).listNodes().get(0).getStatus().getAddresses().get(0).getAddress();
            return externalAddress + ":" + port;
        } else if (extBootstrapServiceType.equals("LoadBalancer")) {
            LoadBalancerIngress loadBalancerIngress = extBootstrapService.getStatus().getLoadBalancer().getIngress().get(0);
            String result = loadBalancerIngress.getHostname();

            if (result == null) {
                result = loadBalancerIngress.getIp();
            }
            return result + ":9094";
        } else {
            throw new RuntimeException("Unexpected external bootstrap service for Kafka cluster " + clusterName);
        }
    }

    /**
     * Create keystore
     * @param ca certificate authority
     * @param cert certificate
     * @param key key
     * @param password password
     * @return keystore location as File
     * @throws IOException
     * @throws InterruptedException
     */
    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    private static File createKeystore(byte[] ca, byte[] cert, byte[] key, String password) throws IOException, InterruptedException {
        File caFile = File.createTempFile(KafkaClientProperties.class.getName(), ".crt");
        caFile.deleteOnExit();
        Files.write(caFile.toPath(), ca);
        File certFile = File.createTempFile(KafkaClientProperties.class.getName(), ".crt");
        certFile.deleteOnExit();
        Files.write(certFile.toPath(), cert);
        File keyFile = File.createTempFile(KafkaClientProperties.class.getName(), ".key");
        keyFile.deleteOnExit();
        Files.write(keyFile.toPath(), key);
        File keystore = File.createTempFile(KafkaClientProperties.class.getName(), ".keystore");
        keystore.delete(); // Note horrible race condition, but this is only for testing
        // RANDFILE=/tmp/.rnd openssl pkcs12 -export -in $3 -inkey $4 -name $HOSTNAME -password pass:$2 -out $1
        if (new ProcessBuilder("openssl",
                "pkcs12",
                "-export",
                "-in", certFile.getAbsolutePath(),
                "-inkey", keyFile.getAbsolutePath(),
                "-chain",
                "-CAfile", caFile.getAbsolutePath(),
                "-name", "dfbdbd",
                "-password", "pass:" + password,
                "-out", keystore.getAbsolutePath()).inheritIO().start().waitFor() != 0) {
            fail();
        }
        keystore.deleteOnExit();
        return keystore;
    }
}
