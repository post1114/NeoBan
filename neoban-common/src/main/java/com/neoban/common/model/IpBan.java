package com.neoban.common.model;

public class IpBan {

    private int id;
    private String ip;
    private String issuer;
    private String reason;
    private long created;
    private long expires;
    private boolean active;
    private boolean auto;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
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

    public boolean isAuto() {
        return auto;
    }

    public void setAuto(boolean auto) {
        this.auto = auto;
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
