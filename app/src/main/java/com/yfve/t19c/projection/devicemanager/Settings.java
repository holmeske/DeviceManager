package com.yfve.t19c.projection.devicemanager;

import android.content.ComponentName;
import android.os.Parcel;
import android.os.Parcelable;

public class Settings implements Parcelable {
    public static final Creator<Settings> CREATOR = new Creator<Settings>() {

        @Override
        public Settings createFromParcel(Parcel source) {
            return new Settings(source);
        }

        @Override
        public Settings[] newArray(int size) {
            return new Settings[size];
        }
    };
    public static int APP_TYPE_NO_CARPLAY = 0;
    public static int APP_TYPE_CARPLAY = 1;
    public String mSerialNumber;
    public int mVid;
    public int mPid;
    public String mDeviceName;
    public ComponentName mHandler;
    public boolean mAoap;
    public boolean mDefaultHandler;
    public int mApptype;

    public Settings() {

    }

    public Settings(String serialNumber, int vid, int pid) {
        mSerialNumber = serialNumber;
        mVid = vid;
        mPid = pid;
        mApptype = APP_TYPE_NO_CARPLAY;
    }

    public Settings(Settings setting) {
        mSerialNumber = setting.mSerialNumber;
        mVid = setting.mVid;
        mPid = setting.mPid;
        mDeviceName = setting.mDeviceName;
        mHandler = setting.mHandler;
        mAoap = setting.mAoap;
        mDefaultHandler = setting.mDefaultHandler;
        mApptype = setting.mApptype;
    }


    protected Settings(Parcel in) {
        this.readFromParcel(in);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        //数组序列化
        dest.writeString(mSerialNumber);
        dest.writeInt(mVid);
        dest.writeInt(mPid);
        dest.writeString(mDeviceName);
        mHandler.writeToParcel(dest, flags);
        dest.writeBoolean(mAoap);
        dest.writeBoolean(mDefaultHandler);
        dest.writeInt(mApptype);
    }

    public void readFromParcel(Parcel dest) {
        // 注意，此处的读值顺序应当是和writeToParcel()方法中一致的
        mSerialNumber = dest.readString();
        mVid = dest.readInt();
        mPid = dest.readInt();
        mDeviceName = dest.readString();
        mHandler = dest.readParcelable(null);
        mAoap = dest.readBoolean();
        mDefaultHandler = dest.readBoolean();
        mApptype = dest.readInt();
    }

    @Override
    public String toString() {
        return "Settings{" +
                "mSerialNumber='" + mSerialNumber + '\'' +
                ", mVid=" + mVid +
                ", mPid=" + mPid +
                ", mDeviceName='" + mDeviceName + '\'' +
                ", mHandler=" + mHandler +
                ", mAoap=" + mAoap +
                ", mDefaultHandler=" + mDefaultHandler +
                ", mApptype=" + mApptype +
                '}';
    }
}

