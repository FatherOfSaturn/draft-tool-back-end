package org.magic.common.admin;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Service for checking admin privileges against a configured email whitelist.
 * Admin emails are loaded from the {@code admin.emails} configuration property
 * (comma-separated) at startup.
 */
@ApplicationScoped
public class AdminService {
    private static final Logger LOGGER = LogManager.getLogger(AdminService.class);

    private final Set<String> adminEmails;

    @Inject
    public AdminService(@ConfigProperty(name = "admin.emails", defaultValue = "") final String adminEmailsConfig) {
        this.adminEmails = Arrays.stream(adminEmailsConfig.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());

        LOGGER.info("Admin whitelist loaded with {} email(s): {}", adminEmails.size(), adminEmails);
    }

    /**
     * Checks whether the given email address is in the admin whitelist.
     * Comparison is case-insensitive.
     *
     * @param email the email to check
     * @return {@code true} if the email is in the admin whitelist
     */
    public boolean isAdmin(final String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return adminEmails.contains(email.toLowerCase().trim());
    }
}
