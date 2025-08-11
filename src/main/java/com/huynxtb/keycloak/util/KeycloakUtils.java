package com.huynxtb.keycloak.util;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KeycloakUtils {

    private static final Pattern USER_ID_PATTERN = Pattern.compile("^users/([^/]+).*$");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Extracts the user ID (UUID) from a Keycloak resource path.
     */
    public static String extractUserId(String resourcePath) {
        if (resourcePath == null) {
            throw new IllegalArgumentException("resourcePath must not be null");
        }
        Matcher m = USER_ID_PATTERN.matcher(resourcePath);
        if (m.matches()) {
            return m.group(1);
        }

        return null;
    }

    /**
     * Build full payload for CREATE/UPDATE
     */
    public static String buildUserJson(String action,
                                       RealmModel realm,
                                       UserModel user,
                                       String ipAddress) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("action", action);
        root.put("realmId", realm.getId());
        root.put("realmName", realm.getName());
        root.put("id", user.getId());
        root.put("username", safe(user.getUsername()));
        root.put("email", safe(user.getEmail()));
        root.put("emailVerified", user.isEmailVerified());
        root.put("enabled", user.isEnabled());
        root.put("ipAddress", ipAddress);
        Long createdTs = user.getCreatedTimestamp();
        if (createdTs != null) {
            root.put("createdTimestamp", createdTs);
        }

        // Roles
        ArrayNode rolesArr = root.putArray("roles");
        user.getRealmRoleMappingsStream().forEach(r -> rolesArr.add(r.getName()));

        // Attributes as key-value list
        ArrayNode attrsArr = root.putArray("attributes");
        user.getAttributes().forEach((k, vals) -> {
            vals.forEach(v -> {
                ObjectNode attr = attrsArr.addObject();
                attr.put("key", k);
                attr.put("value", v);
            });
        });

        // Groups
        ArrayNode groupsArr = root.putArray("groups");
        user.getGroupsStream().forEach(g -> groupsArr.add(g.getName()));

        return root.toString();
    }

    /**
     * Build payload for DELETE
     */
    public static String buildUserJson(String action,
                                       RealmModel realm,
                                       String userId,
                                       String ipAddress) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("action", action);
        root.put("realmId", realm.getId());
        root.put("realmName", realm.getName());
        root.put("id", userId);
        root.put("ipAddress", ipAddress);

        return root.toString();
    }

    /** escape quotes */
    public static String safe(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
