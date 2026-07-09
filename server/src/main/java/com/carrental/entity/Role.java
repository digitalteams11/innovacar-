package com.carrental.entity;

/**
 * Application roles.
 *  ADMIN    – full tenant administration (create/delete users, manage resources)
 *  EMPLOYEE – internal staff; read/write access to business resources, no user management
 *  AGENT    – day-to-day rental operations (legacy alias kept for compatibility)
 *  CLIENT   – read-only / customer-facing
 */
/**
 * Application roles.
 *  SUPER_ADMIN – platform-wide administrator (Innovax Technologies)
 *  ADMIN       – full tenant administration (create/delete users, manage resources)
 *  EMPLOYEE    – internal staff; read/write access to business resources, no user management
 *  AGENT       – day-to-day rental operations (legacy alias kept for compatibility)
 *  CLIENT      – read-only / customer-facing
 */
public enum Role {
    SUPER_ADMIN,
    AGENCY_OWNER,
    ADMIN,
    MANAGER,
    EMPLOYEE,
    ACCOUNTANT,
    FLEET_MANAGER,
    DRIVER,
    CUSTOM,
    RECEPTIONIST,
    VIEWER,
    AGENT,
    CLIENT
}
