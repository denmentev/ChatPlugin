package org.Denis496.chatPlugin.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.logging.Logger;
import java.lang.reflect.Method;

/**
 * Safe integration wrapper for PlayTimeTracker API
 * Prevents ClassNotFoundException when PlayTimeTracker is not available
 */
public class PlayTimeIntegration {

    private static PlayTimeIntegration instance;
    private static final Logger logger = Bukkit.getLogger();

    private boolean isAvailable = false;
    private Object apiInstance = null;
    private Method getTotalHoursMethod = null;
    private Method getDailyHoursMethod = null;
    private Method getWeeklyHoursMethod = null;
    private Method getMonthlyHoursMethod = null;

    private PlayTimeIntegration() {
        initialize();
    }

    public static PlayTimeIntegration getInstance() {
        if (instance == null) {
            instance = new PlayTimeIntegration();
        }
        return instance;
    }

    private void initialize() {
        try {
            // Check if PlayTimeTracker plugin exists
            if (Bukkit.getPluginManager().getPlugin("PlayTimeTracker") == null) {
                logger.info("[ChatPlugin] PlayTimeTracker not found - playtime features disabled");
                return;
            }

            // Check if enabled
            if (!Bukkit.getPluginManager().isPluginEnabled("PlayTimeTracker")) {
                logger.info("[ChatPlugin] PlayTimeTracker found but not enabled");
                // Schedule retry
                Bukkit.getScheduler().runTaskLater(
                        Bukkit.getPluginManager().getPlugin("ChatPlugin"),
                        this::initialize,
                        20L
                );
                return;
            }

            // Load API class using reflection
            Class<?> apiClass = Class.forName("com.denis496.playtimetracker.api.PlayTimeTrackerAPI");

            // Get instance
            Method getInstanceMethod = apiClass.getMethod("getInstance");
            apiInstance = getInstanceMethod.invoke(null);

            if (apiInstance == null) {
                logger.warning("[ChatPlugin] PlayTimeTracker API not ready - will retry");
                // Retry later
                Bukkit.getScheduler().runTaskLater(
                        Bukkit.getPluginManager().getPlugin("ChatPlugin"),
                        this::initialize,
                        100L // 5 seconds
                );
                return;
            }

            // Get method references for better performance
            getTotalHoursMethod = apiClass.getMethod("getTotalHours", Player.class);
            getDailyHoursMethod = apiClass.getMethod("getDailyHours", Player.class);
            getWeeklyHoursMethod = apiClass.getMethod("getWeeklyHours", Player.class);
            getMonthlyHoursMethod = apiClass.getMethod("getMonthlyHours", Player.class);

            isAvailable = true;
            logger.info("[ChatPlugin] Successfully integrated with PlayTimeTracker API!");

        } catch (ClassNotFoundException e) {
            logger.info("[ChatPlugin] PlayTimeTracker API not found - playtime features disabled");
        } catch (Exception e) {
            logger.warning("[ChatPlugin] Error initializing PlayTimeTracker integration: " + e.getMessage());
        }
    }

    /**
     * Reinitialize the integration (useful after reloads)
     */
    public void reinitialize() {
        isAvailable = false;
        apiInstance = null;
        getTotalHoursMethod = null;
        getDailyHoursMethod = null;
        getWeeklyHoursMethod = null;
        getMonthlyHoursMethod = null;
        initialize();
    }

    /**
     * Check if PlayTimeTracker integration is available
     */
    public boolean isAvailable() {
        return isAvailable && apiInstance != null;
    }

    /**
     * Get player's total hours safely
     * @param player The player
     * @return Total hours or 0.0 if not available
     */
    public double getTotalHours(Player player) {
        if (!isAvailable() || player == null) {
            return 0.0;
        }

        try {
            Object result = getTotalHoursMethod.invoke(apiInstance, player);
            return result != null ? (double) result : 0.0;
        } catch (Exception e) {
            // Silent fail - return 0
            return 0.0;
        }
    }

    /**
     * Get player's daily hours safely
     */
    public double getDailyHours(Player player) {
        if (!isAvailable() || player == null) {
            return 0.0;
        }

        try {
            Object result = getDailyHoursMethod.invoke(apiInstance, player);
            return result != null ? (double) result : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Get player's weekly hours safely
     */
    public double getWeeklyHours(Player player) {
        if (!isAvailable() || player == null) {
            return 0.0;
        }

        try {
            Object result = getWeeklyHoursMethod.invoke(apiInstance, player);
            return result != null ? (double) result : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Get player's monthly hours safely
     */
    public double getMonthlyHours(Player player) {
        if (!isAvailable() || player == null) {
            return 0.0;
        }

        try {
            Object result = getMonthlyHoursMethod.invoke(apiInstance, player);
            return result != null ? (double) result : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Format hours to display string
     */
    public String formatHours(double hours) {
        return String.format("%.1f", hours);
    }
}