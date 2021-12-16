package com.yfve.t19c.projection.devicelist;

import android.os.Parcel;
import android.os.Parcelable;

public class Device implements Parcelable {
    public static final Creator<Device> CREATOR = new Creator<Device>() {
        @Override
        public Device createFromParcel(Parcel in) {
            return new Device(in);
        }

        @Override
        public Device[] newArray(int size) {
            return new Device[size];
        }
    };
    private int type;
    private String name;
    private String serial;
    private String mac;
    private boolean usbAA;
    private boolean wirelessAA;
    private boolean usbCP;
    private boolean wirelessCP;
    private boolean available;

    public Device() {
    }

    public Device(int type, String name, String serial, String mac, boolean usbAA, boolean wirelessAA, boolean usbCP, boolean wirelessCP, boolean available) {
        this.type = type;
        this.name = name;
        this.serial = serial;
        this.mac = mac;
        this.usbAA = usbAA;
        this.wirelessAA = wirelessAA;
        this.usbCP = usbCP;
        this.wirelessCP = wirelessCP;
        this.available = available;
    }

    public Device(Parcel in) {
        type = in.readInt();
        name = in.readString();
        serial = in.readString();
        mac = in.readString();
        usbAA = in.readByte() != 0;
        wirelessAA = in.readByte() != 0;
        usbCP = in.readByte() != 0;
        wirelessCP = in.readByte() != 0;
        available = in.readByte() != 0;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
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

    public boolean isUsbAA() {
        return usbAA;
    }

    public void setUsbAA(boolean usbAA) {
        this.usbAA = usbAA;
    }

    public boolean isWirelessAA() {
        return wirelessAA;
    }

    public void setWirelessAA(boolean wirelessAA) {
        this.wirelessAA = wirelessAA;
    }

    public boolean isUsbCP() {
        return usbCP;
    }

    public void setUsbCP(boolean usbCP) {
        this.usbCP = usbCP;
    }

    public boolean isWirelessCP() {
        return wirelessCP;
    }

    public void setWirelessCP(boolean wirelessCP) {
        this.wirelessCP = wirelessCP;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(type);
        dest.writeString(name);
        dest.writeString(serial);
        dest.writeString(mac);
        dest.writeByte((byte) (usbAA ? 1 : 0));
        dest.writeByte((byte) (wirelessAA ? 1 : 0));
        dest.writeByte((byte) (usbCP ? 1 : 0));
        dest.writeByte((byte) (wirelessCP ? 1 : 0));
        dest.writeByte((byte) (available ? 1 : 0));
    }

    @Override
    public String toString() {
        return "Device{" +
                "type=" + type +
                ", name='" + name + '\'' +
                ", serial='" + serial + '\'' +
                ", mac='" + mac + '\'' +
                ", usbAA=" + usbAA +
                ", wirelessAA=" + wirelessAA +
                ", usbCP=" + usbCP +
                ", wirelessCP=" + wirelessCP +
                ", available=" + available +
                '}';
    }
}
