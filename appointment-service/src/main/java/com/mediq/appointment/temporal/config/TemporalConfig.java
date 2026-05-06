package com.mediq.appointment.temporal.config;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.WorkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TemporalConfig {

    @Value("${temporal.host:localhost}")
    private String temporalHost;

    @Value("${temporal.port:7233}")
    private int temporalPort;

    @Value("${temporal.namespace:mediq-appointments}")
    private String namespace;

    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        return WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder()
                .setTarget(temporalHost + ":" + temporalPort)
                .build());
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs stubs) {
        return WorkflowClient.newInstance(stubs,
            WorkflowClientOptions.newBuilder()
                .setNamespace(namespace)
                .build());
    }

    @Bean
    public WorkerFactory workerFactory(WorkflowClient client) {
        return WorkerFactory.newInstance(client);
    }
}
