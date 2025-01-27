/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.api.kafka.model;

import io.strimzi.test.TestUtils;
import io.strimzi.test.k8s.KubeClusterException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * The purpose of this test is to confirm that we can create a
 * resource from the POJOs, serialize it and create the resource in K8S.
 * I.e. that such instance resources obtained from POJOs are valid according to the schema
 * validation done by K8S.
 */
public class KafkaBridgeCrdIT extends AbstractCrdIT {

    public static final String NAMESPACE = "kafkabridge-crd-it";

    @Test
    void testKafkaMirrorMakerV1alpha1() {
        assumeKube1_11Plus();
        createDelete(KafkaBridge.class, "KafkaBridgeV1alpha1.yaml");
    }

    @Test
    void testKafkaBridgeMinimal() {
        createDelete(KafkaBridge.class, "KafkaBridge-minimal.yaml");
    }

    @Test
    void testKafkaBridgeWithExtraProperty() {
        createDelete(KafkaMirrorMaker.class, "KafkaBridge-with-extra-property.yaml");
    }

    @Test
    void testKafkaBridgeWithMissingRequired() {
        try {
            createDelete(KafkaBridge.class, "KafkaBridge-with-missing-required-property.yaml");
        } catch (KubeClusterException.InvalidResource e) {
            assertThat(e.getMessage().contains("spec.bootstrapServers in body is required"), is(true));
        }
    }

    @Test
    void testKafkaBridgeWithTls() {
        createDelete(KafkaBridge.class, "KafkaBridge-with-tls.yaml");
    }

    @Test
    void testKafkaBridgeWithTlsAuth() {
        createDelete(KafkaBridge.class, "KafkaBridge-with-tls-auth.yaml");
    }

    @Test
    void testKafkaBridgeWithTlsAuthWithMissingRequired() {
        try {
            createDelete(KafkaBridge.class, "KafkaBridge-with-tls-auth-with-missing-required.yaml");
        } catch (KubeClusterException.InvalidResource e) {
            assertThat(e.getMessage().contains("spec.authentication.certificateAndKey.certificate in body is required"), is(true));
            assertThat(e.getMessage().contains("spec.authentication.certificateAndKey.key in body is required"), is(true));
        }
    }

    @Test
    void testKafkaBridgeWithScramSha512Auth() {
        createDelete(KafkaBridge.class, "KafkaBridge-with-scram-sha-512-auth.yaml");
    }

    @Test
    void testKafkaBridgeWithTemplate() {
        createDelete(KafkaBridge.class, "KafkaBridge-with-template.yaml");
    }

    @Test
    void testKafkaBridgeWithJaegerTracing() {
        createDelete(KafkaBridge.class, "KafkaBridge-with-jaeger-tracing.yaml");
    }

    @Test
    void testKafkaBridgeWithWrongTracingType() {
        try {
            createDelete(KafkaBridge.class, "KafkaBridge-with-wrong-tracing-type.yaml");
        } catch (KubeClusterException.InvalidResource e) {
            assertThat(e.getMessage().contains("spec.tracing.type in body should be one of [jaeger]"), is(true));
        }
    }

    @Test
    void testKafkaBridgeWithMissingTracingType() {
        try {
            createDelete(KafkaBridge.class, "KafkaBridge-with-missing-tracing-type.yaml");
        } catch (KubeClusterException.InvalidResource e) {
            assertThat(e.getMessage().contains("spec.tracing.type in body is required"), is(true));
        }
    }

    @BeforeAll
    void setupEnvironment() {
        createNamespace(NAMESPACE);
        createCustomResources(TestUtils.CRD_KAFKA_BRIDGE);
    }

    @AfterAll
    void teardownEnvironment() {
        deleteCustomResources();
        deleteNamespaces();
    }
}

