package com.mediq.keycloak;

import com.mediq.event.UserEvent;
import com.mediq.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class KeycloakSyncConsumer {

    private static final Logger log = LoggerFactory.getLogger(KeycloakSyncConsumer.class);

    private final KeycloakAdminClient keycloakAdminClient;
    private final UserRepository userRepository;

    public KeycloakSyncConsumer(KeycloakAdminClient keycloakAdminClient,
                                UserRepository userRepository) {
        this.keycloakAdminClient = keycloakAdminClient;
        this.userRepository = userRepository;
    }

    @KafkaListener(
        topics = "${mediq.kafka.topic.user-events}",
        groupId = "mediq-keycloak-sync-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onUserEvent(UserEvent event, Acknowledgment ack) {
        log.info("Keycloak sync received eventType={} userId={}",
            event.eventType(), event.userId());

        try {
            switch (event.eventType()) {
                case "USER_REGISTERED" -> handleUserRegistered(event);
                case "USER_DEACTIVATED" -> handleUserDeactivated(event);
                default -> log.debug("Ignoring event type: {}", event.eventType());
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Keycloak sync failed for userId={}: {}",
                event.userId(), e.getMessage());
            // Do NOT acknowledge — Kafka retries
        }
    }

    private void handleUserRegistered(UserEvent event) {
        // Idempotency check — skip if keycloakId already set
        userRepository.findById(UUID.fromString(event.userId()))
            .filter(u -> u.getKeycloakId() != null)
            .ifPresent(u -> {
                throw new IllegalStateException(
                    "Keycloak ID already set for userId=" + event.userId() + " — skipping");
            });

        String keycloakId = keycloakAdminClient.createUser(
            event.primaryEmail(),
            event.firstName() + " " + event.lastName(),
            event.userType()
        );

        userRepository.findById(UUID.fromString(event.userId()))
            .ifPresent(user -> {
                user.setKeycloakId(keycloakId);
                userRepository.save(user);
                log.info("Keycloak ID set for userId={}: keycloakId={}",
                    event.userId(), keycloakId);
            });
    }

    private void handleUserDeactivated(UserEvent event) {
        if (event.keycloakId() != null) {
            keycloakAdminClient.disableUser(event.keycloakId());
            log.info("Keycloak user disabled: keycloakId={}", event.keycloakId());
        }
    }
}
