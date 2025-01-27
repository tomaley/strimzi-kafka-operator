/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.systemtest;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.strimzi.api.kafka.model.EntityOperatorJvmOptions;
import io.strimzi.api.kafka.model.JvmOptions;
import io.strimzi.api.kafka.model.KafkaConnectResources;
import io.strimzi.api.kafka.model.KafkaMirrorMakerResources;
import io.strimzi.api.kafka.model.KafkaResources;
import io.strimzi.systemtest.utils.StUtils;
import io.strimzi.test.timemeasuring.Operation;
import io.strimzi.test.timemeasuring.TimeMeasuringSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.strimzi.systemtest.Constants.REGRESSION;
import static io.strimzi.test.k8s.BaseCmdKubeClient.STATEFUL_SET;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@Tag(REGRESSION)
@TestMethodOrder(OrderAnnotation.class)
class LogSettingST extends AbstractST {
    static final String NAMESPACE = "log-setting-cluster-test";
    private static final Logger LOGGER = LogManager.getLogger(LogSettingST.class);
    private static final String KAFKA_MAP = String.format("%s-%s", CLUSTER_NAME, "kafka-config");
    private static final String ZOOKEEPER_MAP = String.format("%s-%s", CLUSTER_NAME, "zookeeper-config");
    private static final String TO_MAP = String.format("%s-%s", CLUSTER_NAME, "entity-topic-operator-config");
    private static final String UO_MAP = String.format("%s-%s", CLUSTER_NAME, "entity-user-operator-config");
    private static final String CONNECT_MAP = String.format("%s-%s", CLUSTER_NAME, "connect-config");
    private static final String MM_MAP = String.format("%s-%s", CLUSTER_NAME, "mirror-maker-config");

    private static final String INFO = "INFO";
    private static final String ERROR = "ERROR";
    private static final String WARN = "WARN";
    private static final String TRACE = "TRACE";
    private static final String DEBUG = "DEBUG";
    private static final String FATAL = "FATAL";
    private static final String OFF = "OFF";

    private static final String GC_LOGGING_SET_NAME = "gc-set-logging";

    private static final Map<String, String> KAFKA_LOGGERS = new HashMap<String, String>() {
        {
            put("kafka.root.logger.level", INFO);
            put("log4j.logger.org.I0Itec.zkclient.ZkClient", ERROR);
            put("log4j.logger.org.apache.zookeeper", WARN);
            put("log4j.logger.kafka", TRACE);
            put("log4j.logger.org.apache.kafka", DEBUG);
            put("log4j.logger.kafka.request.logger", FATAL);
            put("log4j.logger.kafka.network.Processor", OFF);
            put("log4j.logger.kafka.server.KafkaApis", INFO);
            put("log4j.logger.kafka.network.RequestChannel$", ERROR);
            put("log4j.logger.kafka.controller", WARN);
            put("log4j.logger.kafka.log.LogCleaner", TRACE);
            put("log4j.logger.state.change.logger", DEBUG);
            put("log4j.logger.kafka.authorizer.logger", FATAL);
        }
    };

    private static final Map<String, String> ZOOKEEPER_LOGGERS = new HashMap<String, String>() {
        {
            put("zookeeper.root.logger", OFF);
        }
    };

    private static final Map<String, String> CONNECT_LOGGERS = new HashMap<String, String>() {
        {
            put("connect.root.logger.level", INFO);
            put("log4j.logger.org.I0Itec.zkclient", ERROR);
            put("log4j.logger.org.reflections", WARN);
        }
    };

    private static final Map<String, String> OPERATORS_LOGGERS = new HashMap<String, String>() {
        {
            put("rootLogger.level", DEBUG);
        }
    };

    private static final Map<String, String> MIRROR_MAKER_LOGGERS = new HashMap<String, String>() {
        {
            put("mirrormaker.root.logger", TRACE);
        }
    };

    @Test
    @Order(1)
    void testLoggersKafka() {
        int duration = TimeMeasuringSystem.getCurrentDuration(testClass, testClass, getOperationID());
        assertThat("Kafka's log level is set properly", checkLoggersLevel(KAFKA_LOGGERS, duration, KAFKA_MAP), is(true));
    }

    @Test
    @Order(2)
    void testLoggersZookeeper() {
        int duration = TimeMeasuringSystem.getCurrentDuration(testClass, testClass, getOperationID());
        assertThat("Zookeeper's log level is set properly", checkLoggersLevel(ZOOKEEPER_LOGGERS, duration, ZOOKEEPER_MAP), is(true));
    }

    @Test
    @Order(3)
    void testLoggersTO() {
        int duration = TimeMeasuringSystem.getCurrentDuration(testClass, testClass, getOperationID());
        assertThat("Topic operator's log level is set properly", checkLoggersLevel(OPERATORS_LOGGERS, duration, TO_MAP), is(true));
    }

    @Test
    @Order(4)
    void testLoggersUO() {
        int duration = TimeMeasuringSystem.getCurrentDuration(testClass, testClass, getOperationID());
        assertThat("User operator's log level is set properly", checkLoggersLevel(OPERATORS_LOGGERS, duration, UO_MAP), is(true));
    }

    @Test
    @Order(5)
    void testLoggersKafkaConnect() {
        int duration = TimeMeasuringSystem.getCurrentDuration(testClass, testClass, getOperationID());
        assertThat("Kafka connect's log level is set properly", checkLoggersLevel(CONNECT_LOGGERS, duration, CONNECT_MAP), is(true));
    }

    @Test
    @Order(6)
    void testLoggersMirrorMaker() {
        int duration = TimeMeasuringSystem.getCurrentDuration(testClass, testClass, getOperationID());
        assertThat("Mirror maker's log level is set properly", checkLoggersLevel(MIRROR_MAKER_LOGGERS, duration, MM_MAP), is(true));
    }

    @Test
    @Order(7)
    void testGcLoggingNonSetEnabled() {
        assertThat("Kafka GC logging is enabled", checkGcLoggingStatefulSets(KafkaResources.kafkaStatefulSetName(GC_LOGGING_SET_NAME)), is(true));
        assertThat("Zookeeper GC logging is enabled", checkGcLoggingStatefulSets(KafkaResources.zookeeperStatefulSetName(GC_LOGGING_SET_NAME)), is(true));

        assertThat("TO GC logging is enabled", checkGcLoggingDeployments(KafkaResources.entityOperatorDeploymentName(GC_LOGGING_SET_NAME), "topic-operator"), is(true));
        assertThat("UO GC logging is enabled", checkGcLoggingDeployments(KafkaResources.entityOperatorDeploymentName(GC_LOGGING_SET_NAME), "user-operator"), is(true));
    }

    @Test
    @Order(8)
    void testGcLoggingSetEnabled() {
        assertThat("Kafka GC logging is enabled", checkGcLoggingStatefulSets(KafkaResources.kafkaStatefulSetName(CLUSTER_NAME)), is(true));
        assertThat("Zookeeper GC logging is enabled", checkGcLoggingStatefulSets(KafkaResources.zookeeperStatefulSetName(CLUSTER_NAME)), is(true));

        assertThat("TO GC logging is enabled", checkGcLoggingDeployments(KafkaResources.entityOperatorDeploymentName(CLUSTER_NAME), "topic-operator"), is(true));
        assertThat("UO GC logging is enabled", checkGcLoggingDeployments(KafkaResources.entityOperatorDeploymentName(CLUSTER_NAME), "user-operator"), is(true));

        assertThat("Connect GC logging is enabled", checkGcLoggingDeployments(KafkaConnectResources.deploymentName(CLUSTER_NAME)), is(true));
        assertThat("Mirror-maker GC logging is enabled", checkGcLoggingDeployments(KafkaMirrorMakerResources.deploymentName(CLUSTER_NAME)), is(true));
    }

    @Test
    @Order(9)
    void testGcLoggingSetDisabled() {
        String connectName = KafkaConnectResources.deploymentName(CLUSTER_NAME);
        String mmName = KafkaMirrorMakerResources.deploymentName(CLUSTER_NAME);
        String eoName = KafkaResources.entityOperatorDeploymentName(CLUSTER_NAME);
        String kafkaName = KafkaResources.kafkaStatefulSetName(CLUSTER_NAME);
        String zkName = KafkaResources.zookeeperStatefulSetName(CLUSTER_NAME);
        Map<String, String> connectPods = StUtils.depSnapshot(connectName);
        Map<String, String> mmPods = StUtils.depSnapshot(mmName);
        Map<String, String> eoPods = StUtils.depSnapshot(eoName);
        Map<String, String> kafkaPods = StUtils.ssSnapshot(kafkaName);
        Map<String, String> zkPods = StUtils.ssSnapshot(zkName);

        JvmOptions jvmOptions = new JvmOptions();
        jvmOptions.setGcLoggingEnabled(false);

        EntityOperatorJvmOptions entityOperatorJvmOptions = new EntityOperatorJvmOptions();
        entityOperatorJvmOptions.setGcLoggingEnabled(false);

        replaceKafkaResource(CLUSTER_NAME, k -> {
            k.getSpec().getKafka().setJvmOptions(jvmOptions);
            k.getSpec().getZookeeper().setJvmOptions(jvmOptions);
            k.getSpec().getEntityOperator().getTopicOperator().setJvmOptions(entityOperatorJvmOptions);
            k.getSpec().getEntityOperator().getUserOperator().setJvmOptions(entityOperatorJvmOptions);
        });

        replaceKafkaConnectResource(CLUSTER_NAME, k -> k.getSpec().setJvmOptions(jvmOptions));
        replaceMirrorMakerResource(CLUSTER_NAME, k -> k.getSpec().setJvmOptions(jvmOptions));

        StUtils.waitTillSsHasRolled(kafkaName, 3, kafkaPods);
        StUtils.waitTillSsHasRolled(zkName, 1, zkPods);
        StUtils.waitTillDepHasRolled(eoName, 1, eoPods);
        StUtils.waitTillDepHasRolled(connectName, 1, connectPods);
        StUtils.waitTillDepHasRolled(mmName, 1, mmPods);

        assertThat("Kafka GC logging is disabled", checkGcLoggingStatefulSets(KafkaResources.kafkaStatefulSetName(CLUSTER_NAME)), is(false));
        assertThat("Zookeeper GC logging is disabled", checkGcLoggingStatefulSets(KafkaResources.zookeeperStatefulSetName(CLUSTER_NAME)), is(false));

        assertThat("TO GC logging is disabled", checkGcLoggingDeployments(KafkaResources.entityOperatorDeploymentName(CLUSTER_NAME), "topic-operator"), is(false));
        assertThat("UO GC logging is disabled", checkGcLoggingDeployments(KafkaResources.entityOperatorDeploymentName(CLUSTER_NAME), "user-operator"), is(false));

        assertThat("Connect GC logging is disabled", checkGcLoggingDeployments(KafkaConnectResources.deploymentName(CLUSTER_NAME)), is(false));
        assertThat("Mirror-maker GC logging is disabled", checkGcLoggingDeployments(KafkaMirrorMakerResources.deploymentName(CLUSTER_NAME)), is(false));
    }

    private boolean checkLoggersLevel(Map<String, String> loggers, int since, String configMapName) {
        boolean result = false;
        for (Map.Entry<String, String> entry : loggers.entrySet()) {
            LOGGER.info("Check log level setting since {} seconds. Logger: {} Expected: {}", since, entry.getKey(), entry.getValue());
            String configMap = cmdKubeClient().get("configMap", configMapName);
            String loggerConfig = String.format("%s=%s", entry.getKey(), entry.getValue());
            result = configMap.contains(loggerConfig);

            if (result) {
                String log = cmdKubeClient().searchInLog(STATEFUL_SET, KafkaResources.kafkaStatefulSetName(CLUSTER_NAME), since, ERROR);
                result = log.isEmpty();
            }
        }

        return result;
    }

    private Boolean checkGcLoggingDeployments(String deploymentName, String containerName) {
        LOGGER.info("Checking deployment: {}", deploymentName);
        List<Container> containers = kubeClient().getDeployment(deploymentName).getSpec().getTemplate().getSpec().getContainers();
        Container container = getContainerByName(containerName, containers);
        LOGGER.info("Checking container with name: {}", container.getName());
        return checkEnvVarValue(container);
    }

    private Boolean checkGcLoggingDeployments(String deploymentName) {
        LOGGER.info("Checking deployment: {}", deploymentName);
        Container container = kubeClient().getDeployment(deploymentName).getSpec().getTemplate().getSpec().getContainers().get(0);
        LOGGER.info("Checking container with name: {}", container.getName());
        return checkEnvVarValue(container);
    }

    private Boolean checkGcLoggingStatefulSets(String statefulSetName) {
        LOGGER.info("Checking stateful set: {}", statefulSetName);
        Container container = kubeClient().getStatefulSet(statefulSetName).getSpec().getTemplate().getSpec().getContainers().get(0);
        LOGGER.info("Checking container with name: {}", container.getName());
        return checkEnvVarValue(container);
    }

    private Container getContainerByName(String containerName, List<Container> containers) {
        return containers.stream().filter(c -> c.getName().equals(containerName)).findFirst().orElse(null);
    }

    private Boolean checkEnvVarValue(Container container) {
        assertThat("Container is null!", container, is(notNullValue()));

        List<EnvVar> loggingEnvVar = container.getEnv().stream().filter(envVar -> envVar.getName().contains("GC_LOG_ENABLED")).collect(Collectors.toList());
        LOGGER.info("{}={}", loggingEnvVar.get(0).getName(), loggingEnvVar.get(0).getValue());
        return loggingEnvVar.get(0).getValue().contains("true");
    }

    @BeforeAll
    void createClassResources() {
        LOGGER.info("Create resources for the tests");
        prepareEnvForOperator(NAMESPACE);

        createTestClassResources();
        applyRoleBindings(NAMESPACE);
        // 050-Deployment
        testClassResources().clusterOperator(NAMESPACE).done();

        setOperationID(startDeploymentMeasuring());

        testClassResources().kafkaEphemeral(CLUSTER_NAME, 3, 1)
            .editSpec()
                .editKafka()
                    .withNewInlineLogging()
                        .withLoggers(KAFKA_LOGGERS)
                    .endInlineLogging()
                    .withNewJvmOptions()
                        .withGcLoggingEnabled(true)
                    .endJvmOptions()
                .endKafka()
                .editZookeeper()
                    .withNewInlineLogging()
                        .withLoggers(ZOOKEEPER_LOGGERS)
                    .endInlineLogging()
                    .withNewJvmOptions()
                        .withGcLoggingEnabled(true)
                    .endJvmOptions()
                .endZookeeper()
                .editEntityOperator()
                    .editOrNewUserOperator()
                        .withNewInlineLogging()
                            .withLoggers(OPERATORS_LOGGERS)
                        .endInlineLogging()
                        .withNewJvmOptions()
                            .withGcLoggingEnabled(true)
                        .endJvmOptions()
                    .endUserOperator()
                    .editOrNewTopicOperator()
                        .withNewInlineLogging()
                            .withLoggers(OPERATORS_LOGGERS)
                        .endInlineLogging()
                        .withNewJvmOptions()
                            .withGcLoggingEnabled(true)
                        .endJvmOptions()
                    .endTopicOperator()
                .endEntityOperator()
            .endSpec()
            .done();

        testClassResources().kafkaEphemeral(GC_LOGGING_SET_NAME, 3, 1)
                .editSpec()
                    .editKafka()
                        .withNewJvmOptions()
                        .endJvmOptions()
                    .endKafka()
                    .editZookeeper()
                        .withNewJvmOptions()
                        .endJvmOptions()
                    .endZookeeper()
                    .editEntityOperator()
                        .editTopicOperator()
                            .withNewJvmOptions()
                            .endJvmOptions()
                        .endTopicOperator()
                        .editUserOperator()
                            .withNewJvmOptions()
                            .endJvmOptions()
                        .endUserOperator()
                    .endEntityOperator()
                .endSpec()
                .done();

        testClassResources().kafkaConnect(CLUSTER_NAME, 1)
            .editSpec()
                .withNewInlineLogging()
                    .withLoggers(CONNECT_LOGGERS)
                .endInlineLogging()
                .withNewJvmOptions()
                    .withGcLoggingEnabled(true)
                .endJvmOptions()
            .endSpec().done();

        testClassResources().kafkaMirrorMaker(CLUSTER_NAME, CLUSTER_NAME, GC_LOGGING_SET_NAME, "my-group", 1, false)
            .editSpec()
                .withNewInlineLogging()
                  .withLoggers(MIRROR_MAKER_LOGGERS)
                .endInlineLogging()
                .withNewJvmOptions()
                    .withGcLoggingEnabled(true)
                .endJvmOptions()
            .endSpec()
            .done();
    }

    private String startDeploymentMeasuring() {
        TimeMeasuringSystem.setTestName(testClass, testClass);
        return TimeMeasuringSystem.startOperation(Operation.CLASS_EXECUTION);
    }

    @Override
    protected void recreateTestEnv(String coNamespace, List<String> bindingsNamespaces) {
        LOGGER.info("Skip env recreation after failed tests!");
    }

    @Override
    protected void tearDownEnvironmentAfterAll() {
        teardownEnvForOperator();
    }
}
