package com.huynxtb.keycloak.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huynxtb.keycloak.constant.EventTypes;
import com.huynxtb.keycloak.service.UserEventService;
import com.huynxtb.keycloak.util.KeycloakUtils;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.UserModel;
import java.util.Optional;

public class CustomEventListenerProvider implements EventListenerProvider {

    private static final Logger log = Logger.getLogger(CustomEventListenerProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KeycloakSession session;
    private final RealmProvider realms;
    private final UserEventService userEventService;

    public CustomEventListenerProvider(KeycloakSession session) {
        this.session = session;
        this.realms = session.realms();
        this.userEventService = new UserEventService();
    }

    @Override
    public void onEvent(Event event) {

        log.infof("=========================");
        log.infof("Event type: %s", event.getType());

        switch (event.getType()) {
            case REGISTER -> handleUserEvent(EventTypes.CREATED, event.getRealmId(), event.getUserId(), event.getIpAddress());
            case UPDATE_PROFILE -> handleUserEvent(EventTypes.UPDATED, event.getRealmId(), event.getUserId(), event.getIpAddress());
            case LOGIN -> handleUserEvent(EventTypes.LOGIN, event.getRealmId(), event.getUserId(), event.getIpAddress());
            case LOGOUT -> handleUserEvent(EventTypes.LOGOUT, event.getRealmId(), event.getUserId(), event.getIpAddress());
            case VERIFY_EMAIL -> handleUserEvent(EventTypes.VERIFY_EMAIL, event.getRealmId(), event.getUserId(), event.getIpAddress());
            default -> { /* ignore everything else */ }
        }
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
        ResourceType resType = adminEvent.getResourceType();
        String realmId = adminEvent.getRealmId();
        String path = adminEvent.getResourcePath();
        String userId = KeycloakUtils.extractUserId(path);

        if (userId == null) {
            return;
        }

        log.infof("=========================");
        log.infof("Resource type: %s", resType.toString());
        log.infof("Operation type: %s", adminEvent.getOperationType());

        if (resType == ResourceType.USER
                || resType == ResourceType.GROUP_MEMBERSHIP
                || resType == ResourceType.REALM_ROLE_MAPPING) {

            String type = switch (adminEvent.getOperationType()) {
                case CREATE -> EventTypes.CREATED;
                case UPDATE -> EventTypes.UPDATED;
                case DELETE -> EventTypes.DELETED;
                default -> null;
            };

            handleUserEvent(type, realmId, userId, adminEvent.getAuthDetails().getIpAddress());
        }
    }

    @Override
    public void close() { }

    /**
     * Centralized dispatch for ALL user events.
     */
    private void handleUserEvent(String type,
                                 String realmId,
                                 String userId,
                                 String ipAddress) {

        RealmModel realm = realms.getRealm(realmId);

        if (realm == null) {
            log.warnf("Realm %s not found", realmId);
            return;
        }

        if (type.equals(EventTypes.DELETED)) {
            String json = KeycloakUtils.buildUserJson(type, realm, userId, ipAddress);
            userEventService.postEvent(json);
            log.infof("Sent %s for user %s", type, userId);
            return;
        }

        fetchUser(realm, userId).ifPresent(user -> {
            String json = KeycloakUtils.buildUserJson(type, realm, user, ipAddress);
            userEventService.postEvent(json);
            log.infof("Sent %s for user %s", type, user.getId());
        });
    }

    /**
     * Get user by id.
     */
    private Optional<UserModel> fetchUser(RealmModel realm, String userId) {
        UserModel user = session.users().getUserById(realm, userId);
        if (user == null) {
            log.warnf("User %s not found in realm %s", userId, realm.getId());
            return Optional.empty();
        }
        return Optional.of(user);
    }
}
