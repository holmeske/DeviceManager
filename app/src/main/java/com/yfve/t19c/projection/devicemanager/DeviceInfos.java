package com.yfve.t19c.projection.devicemanager;


import android.os.Parcel;
import android.os.Parcelable;

public class DeviceInfos implements Parcelable {
    public static final int Connectivity_AppType_CarPlay = 0;
    public static final int Connectivity_AppType_AndroidAuto = 1;
    public static final int Connectivity_AppType_CarLife = 2;
    public static final int Connectivity_AppType_HiCar = 3;
    public static final int Connectivity_AppType_MAX = 4;
    public static final Creator<DeviceInfos> CREATOR = new Creator<DeviceInfos>() {

        @Override
        public DeviceInfos createFromParcel(Parcel source) {
            return new DeviceInfos(source);
        }

        @Override
        public DeviceInfos[] newArray(int size) {
            return new DeviceInfos[size];
        }
    };
    public String mSerialNumber = new String();
    public String mBluetoothMac = new String();
    public String mDeviceName = new String();
    public int mConnectionType = 0;
    public boolean mAppAvailable[] = new boolean[Connectivity_AppType_MAX];

    public DeviceInfos() {
    }

    public DeviceInfos(String serial, String btMac, String name, int conType) {
        mSerialNumber = serial;
        mBluetoothMac = btMac;
        mDeviceName = name;
        mConnectionType = conType;
    }


    protected DeviceInfos(Parcel in) {
        this.readFromParcel(in);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mSerialNumber);
        dest.writeString(mBluetoothMac);
        dest.writeString(mDeviceName);
        dest.writeInt(mConnectionType);
        dest.writeBooleanArray(mAppAvailable);
    }

    public void readFromParcel(Parcel dest) {
        // 注意，此处的读值顺序应当是和writeToParcel()方法中一致的
        mSerialNumber = dest.readString();
        mBluetoothMac = dest.readString();
        mDeviceName = dest.readString();
        mConnectionType = dest.readInt();
        dest.readBooleanArray(mAppAvailable);
    }

}

