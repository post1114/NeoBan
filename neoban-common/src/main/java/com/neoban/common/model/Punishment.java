package com.neoban.common.model;

import java.util.UUID;

public class Punishment {

    private final PunishmentType type;
    private int id;
    private UUID uuid;
    private String name;
    private String issuer;
    private String reason;
    private long created;
    private long expires;
    private boolean active;

    public Punishment(PunishmentType type) {
        this.type = type;
    }

    public PunishmentType getType() {
        return type;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public long getCreated() {
        return created;
    }

    public void setCreated(long created) {
        this.created = created;
    }

    public long getExpires() {
        return expires;
    }

    public void setExpires(long expires) {
        this.expires = expires;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isPermanent() {
        return expires <= 0;
    }

    public boolean isExpired(long now) {
        return !isPermanent() && now >= expires;
    }

    public long remaining(long now) {
        return isPermanent() ? -1 : Math.max(0L, expires - now);
    }
}
