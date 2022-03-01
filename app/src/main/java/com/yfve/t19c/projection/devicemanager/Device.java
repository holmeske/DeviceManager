package com.yfve.t19c.projection.devicemanager;

public class Device {
    private String name;
    private String serial;
    private String mac;
    private int ability;

    public Device(String name, String serial, String mac, int ability) {
        this.name = name;
        this.serial = serial;
        this.mac = mac;
        this.ability = ability;
    }

    public int getAbility() {
        return ability;
    }

    public void setAbility(int ability) {
        this.ability = ability;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSerial() {
        return serial;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    @Override
    public String toString() {
        return "Device{" +
                "name='" + name + '\'' +
                ", serial='" + serial + '\'' +
                ", mac='" + mac + '\'' +
                ", ability=" + ability +
                '}';
    }
}
