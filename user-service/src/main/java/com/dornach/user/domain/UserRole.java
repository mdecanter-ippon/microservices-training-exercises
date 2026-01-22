package com.dornach.user.domain;

/**
 * User roles in the Dornach system.
 * Each role has specific permissions for business operations.
 */
public enum UserRole {
    ADMIN(true, true, true),
    MANAGER(false, true, true),
    EMPLOYEE(false, false, true);

    private final boolean canManageUsers;
    private final boolean canViewReports;
    private final boolean canCreateShipments;

    UserRole(boolean canManageUsers, boolean canViewReports, boolean canCreateShipments) {
        this.canManageUsers = canManageUsers;
        this.canViewReports = canViewReports;
        this.canCreateShipments = canCreateShipments;
    }

    public boolean canManageUsers() {
        return canManageUsers;
    }

    public boolean canViewReports() {
        return canViewReports;
    }

    public boolean canCreateShipments() {
        return canCreateShipments;
    }
}
