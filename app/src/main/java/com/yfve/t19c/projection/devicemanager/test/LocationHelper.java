package com.yfve.t19c.projection.devicemanager.test;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.location.OnNmeaMessageListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;


import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import java.util.Timer;
import java.util.TimerTask;

public class LocationHelper {
    public final static String TAG = "LocationHelper";
    private static String GPGGA = "";//GlobalPositioningSystemFixData TODO: GPS定位信息不一定会包含此字段
    private static String GPRMC = "";//RecommendedMinimumSpecificGPSTransitData TODO: GPS定位信息不一定会包含此字段
    private static String GPGSV = "";//GPSSatellitesInView TODO: GPS定位信息不一定会包含此字段
    private static final String PASCD = "";//VehicleSpeedData
    private static final String PAGCD = "";//VehicleGyroData
    private static final String PAACD = "";//VehicleAccelerometerData
    private static final String GPHDT = "";//VehicleHeadingData
    private final Context mContext;
    private final byte flag;
    /**
     * Android 10及以上申请权限
     */
    private final String[] permissionsQ = new String[]{
            // 定位权限
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            // 编译版本小于29，不能使用Manifest.permission.ACCESS_BACKGROUND_LOCATION
            // 2020-08-28补充：Android10上若要后台使用定位，需要配置权限为【始终允许】
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
    };
    private Timer mTimer;
    private final TimerTask mTimerTask = new TimerTask() {
        @Override
        public void run() {
            //IAP2CommClient.getInstance().updateLocationInfo(getLocationData(flag));
        }
    };
    private final LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            Log.e(TAG, "onLocationChanged:" + location);
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
            Log.e(TAG, "onProviderDisabled:" + provider);
            Toast.makeText(mContext, "GPS关闭了", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {
            Log.e(TAG, "onProviderEnabled:" + provider);
            Toast.makeText(mContext, "GPS开启了", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Log.e(TAG, "onStatusChanged:" + provider);
            switch (status) {

                case LocationProvider.AVAILABLE:

                    Toast.makeText(mContext, "当前GPS为可用状态!", Toast.LENGTH_SHORT).show();

                    break;

                case LocationProvider.OUT_OF_SERVICE:

                    Toast.makeText(mContext, "当前GPS不在服务内", Toast.LENGTH_SHORT).show();

                    break;

                case LocationProvider.TEMPORARILY_UNAVAILABLE:

                    Toast.makeText(mContext, "当前GPS为暂停服务状态", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };
    private final OnNmeaMessageListener mOnNmeaMessageListener = new OnNmeaMessageListener() {
        @Override
        public void onNmeaMessage(String message, long timestamp) {
            Log.v(TAG, "message: " + message + "timestamp: " + timestamp);
            if (message.contains("GPGSV")) {
                GPGSV = message;
            } else if (message.contains("GPGGA")) {
                GPGGA = message;
            } else if (message.contains("GPRMC")) {
                GPRMC = message;
            }
        }
    };
    private LocationManager locationManager;

    public LocationHelper(Context mContext, byte flag) {
        this.mContext = mContext;
        this.flag = flag;
    }

    private boolean checkSelfPermission(@NonNull String permission) {
        return ContextCompat.checkSelfPermission(mContext, permission) ==
                PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    }

    String getLocationData(byte flag) {
        Log.v(TAG, "flag :" + flag);
        StringBuilder sb = new StringBuilder();
        if ((flag & 0x01) == 0x01) {
            sb.append(GPGGA);
        }
        if ((flag & 0x01) == 0x02) {
            sb.append(GPRMC);
        }
        if ((flag & 0x01) == 0x03) {
            sb.append(GPGSV);
        }
        if ((flag & 0x01) == 0x04) {
            sb.append(PASCD);
        }
        if ((flag & 0x01) == 0x05) {
            sb.append(PAGCD);
        }
        if ((flag & 0x01) == 0x06) {
            sb.append(PAACD);
        }
        if ((flag & 0x01) == 0x07) {
            sb.append(GPHDT);
        }
        Log.v(TAG, "send data :" + sb.toString());
        return sb.toString();
    }

    private void startTimer() {
        if (mTimer == null) {
            mTimer = new Timer();
        }
        mTimer.schedule(mTimerTask, 1000, 1000);
    }

    private void stopTimer() {
        mTimer.cancel();
    }

    public void start() {
        startLocation();
        startTimer();
    }

    public void stop() {
        stopLocation();
        stopTimer();
    }

    private void startLocation() {
        if (hasLocationPermission()) {
            if (locationManager == null) {
                locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
            }
            /*if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }*/
            Looper.prepare();
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
            locationManager.addNmeaListener(mOnNmeaMessageListener);
            Looper.loop();
        } else {
            Log.v(TAG, "访问位置信息权限异常");
            Toast.makeText(mContext, "访问位置信息权限异常", Toast.LENGTH_SHORT).show();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void stopLocation() {
        locationManager.removeNmeaListener(mOnNmeaMessageListener);
        locationManager.removeUpdates(mLocationListener);
    }

}
