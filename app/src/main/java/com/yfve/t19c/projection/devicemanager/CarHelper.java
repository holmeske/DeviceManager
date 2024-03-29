package com.yfve.t19c.projection.devicemanager;

import android.car.Car;
import android.car.CarInfoManager;
import android.car.hardware.power.CarPowerManager;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.yfve.t19c.projection.devicemanager.constant.CarPowerState;

public enum CarHelper {

    INSTANCE;

    private static final String TAG = "CarHelper";
    private static String AndroidAutoSwitch = "";
    private static String CarPlaySwitch = "";
    private static String QDLinkSwitch = "";
    private static boolean StandbyMode;
    private final Handler mHandler = new Handler();
    private Car mCar;
    private CarPowerManager mCarPowerManager;
    private CarInfoManager mCarInfoManager;
    private byte[] property;
    private OnGetValidValueListener onGetValidValueListener;
    private final Runnable notifyPropertiesUpdate = new Runnable() {
        @Override
        public void run() {
            if (onGetValidValueListener != null) {
                onGetValidValueListener.callback();
            }
        }
    };
    private boolean bPropertiesUpdate = false;
    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            if (mCarInfoManager != null) {
                Log.d(TAG, "property.length = " + property.length + " , continue reading data from CarService ...");
                property = mCarInfoManager.getByteProperty(CarInfoManager.ID_DIAGNOSTIC_CONFIG_701A);

                if (property.length != 8) {
                    mHandler.postDelayed(this, 1000);
                } else {
                    processValidValue();
                }
            }
        }
    };
    private OnCarPowerStateListener onCarPowerStateListener;

    public static boolean isStandbyMode() {
        Log.d(TAG, "StandbyMode = " + StandbyMode);
        return StandbyMode;
    }

    public static String byteToBit(byte b) {
        return "" + (byte) ((b >> 7) & 0x1) +

                (byte) ((b >> 6) & 0x1) +

                (byte) ((b >> 5) & 0x1) +

                (byte) ((b >> 4) & 0x1) +

                (byte) ((b >> 3) & 0x1) +

                (byte) ((b >> 2) & 0x1) +

                (byte) ((b >> 1) & 0x1) +

                (byte) ((b) & 0x1);

    }

    public static boolean isOpenAndroidAuto() {
        if ("1".equals(AndroidAutoSwitch)) {
            return true;
        } else {
            Log.e(TAG, "android auto switch is close");
            return false;
        }
    }

    public static boolean isOpenCarPlay() {
        if ("1".equals(CarPlaySwitch)) {
            return true;
        } else {
            Log.e(TAG, "carplay switch is close");
            return false;
        }
    }

    public static boolean isOpenQDLink() {
        if ("1".equals(QDLinkSwitch)) {
            Log.e(TAG, "QDLink Switch is open");
            return true;
        } else {
            return false;
        }
    }

    public void sendPROModeExit() {
        Log.d(TAG, "sendPROModeExit() called");
        if (mCarPowerManager != null) {
            Log.d(TAG, "PROModeExit  true");
            mCarPowerManager.sendPROModeExit(true);
        }
    }

    public void setOnCarPowerStateListener(OnCarPowerStateListener onCarPowerStateListener) {
        this.onCarPowerStateListener = onCarPowerStateListener;
    }

    public void setOnGetValidValueListener(OnGetValidValueListener onGetValidValueListener) {
        this.onGetValidValueListener = onGetValidValueListener;
        if (isUpdateProperties()) {
            mHandler.post(notifyPropertiesUpdate);
        }
    }

    public void initCar(Context context) {
        mCar = Car.createCar(context);
        if (mCar != null) {
            try {
                initCarPowerStateListener();
                mCarInfoManager = (CarInfoManager) mCar.getCarManager(Car.INFO_SERVICE);
                if (mCarInfoManager != null) {
                    property = mCarInfoManager.getByteProperty(CarInfoManager.ID_DIAGNOSTIC_CONFIG_701A);

                    if (property.length != 8) {
                        Log.d(TAG, "onServiceConnected: postDelayed  1000");
                        mHandler.postDelayed(runnable, 1000);
                    } else {
                        processValidValue();
                    }

                } else {
                    Log.e(TAG, "CarInfoManager is null");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            Log.e(TAG, "Car is null");
        }
    }

    private void initCarPowerStateListener() {
        Log.d(TAG, "initCarPowerStateListener() called");
        mCarPowerManager = (CarPowerManager) mCar.getCarManager(Car.POWER_SERVICE);
        if (mCarPowerManager != null) {
            try {
                mCarPowerManager.setListener(state -> {
                    // int PWR_MODE_NONE = 9;
                    // int PWR_MODE_OFF = 10;
                    // int PWR_MODE_STANDBY = 11;
                    // int PWR_MODE_RUN = 12;
                    // int PWR_MODE_SLEEP = 13;
                    // int PWR_MODE_ABNORMAL = 14;
                    // int PWR_MODE_TEMP_ON = 15;
                    // int PWR_MODE_OFF_USER = 16;
                    // int PWR_MODE_PARTIALRUN = 17;
                    // int PWR_MODE_PROTECTION = 18;
                    // int PWR_MODE_TEMPRUN_ENDING = 19;
                    // int PWR_REQ_SYSTEM_OFF = 20;
                    // int PWR_SCREEN_ON = 21;
                    // int PWR_SCREEN_OFF = 22;
                    //sometime will receive twice pwr_mode_standy without pwr_run
                    if (state == CarPowerManager.CarPowerStateListener.PWR_MODE_STANDBY) {
                        Log.d(TAG, "onStateChanged() called with: PWR_MODE_STANDBY");
                        StandbyMode = true;
                        Log.d(TAG, "standby == true");
                        if (onCarPowerStateListener != null) {
                            onCarPowerStateListener.standby();
                        }
                    } else if (state == CarPowerManager.CarPowerStateListener.PWR_MODE_RUN) {
                        Log.d(TAG, "onStateChanged() called with: PWR_MODE_RUN");
                        StandbyMode = false;
                        Log.d(TAG, "standby == false");
                        if (onCarPowerStateListener != null) {
                            onCarPowerStateListener.run();
                        }
                    } else if (state == CarPowerManager.CarPowerStateListener.PWR_MODE_TEMP_ON) {
                        Log.d(TAG, "onStateChanged() called with: PWR_MODE_TEMP_ON");
                        StandbyMode = false;
                        Log.d(TAG, "standby == false");
                        if (onCarPowerStateListener != null) {
                            onCarPowerStateListener.run();
                        }
                    } else if (state == CarPowerManager.CarPowerStateListener.PWR_MODE_PROTECTION) {
                        Log.d(TAG, "onStateChanged() called with: PWR_MODE_PROTECTION");
                        CarPowerState.PWR_MODE_PROTECTION = true;
                    } else if (state == CarPowerManager.CarPowerStateListener.PWR_SCREEN_ON) {
                        Log.d(TAG, "onStateChanged() called with: PWR_SCREEN_ON");
                        CarPowerState.PWR_SCREEN_ON = true;
                    } else if (state == CarPowerManager.CarPowerStateListener.PWR_SCREEN_OFF) {
                        Log.d(TAG, "onStateChanged() called with: PWR_SCREEN_OFF");
                        CarPowerState.PWR_SCREEN_ON = false;
                    }
                });
            } catch (NoSuchMethodError e) {
                Log.e(TAG, "setCarPowerStateListener: ", e);
            }
        } else {
            Log.e(TAG, "CarPowerManager is null");
        }
    }

    public void release() {
        if (mCar != null) {
            mCar.disconnect();
        }
    }
    /*property[0] = 00000000
    property[1] = 00000000
    property[2] = 00101000 cp   qd
    property[3] = 00000000
    property[4] = 00000000
    property[5] = 00000000
    property[6] = 00000000
    property[7] = 00000000*/

    private void processValidValue() {
        Log.d(TAG, "processValidValue() called");
        for (int i = 0; i < property.length; i++) {
            Log.d(TAG, "property[" + i + "] = " + byteToBit(property[i]));
        }

        if (property.length > 2) {
            String byte2 = byteToBit(property[2]);
            AndroidAutoSwitch = byte2.substring(1, 2);//第7个bit
            Log.d(TAG, "AndroidAutoSwitch: " + AndroidAutoSwitch);

            CarPlaySwitch = byte2.substring(4, 5);//第4个bit
            Log.d(TAG, "CarPlaySwitch: " + CarPlaySwitch);

            QDLinkSwitch = byte2.substring(2, 3);//第6个bit
            Log.d(TAG, "QDLinkSwitch: " + QDLinkSwitch);

            if (onGetValidValueListener != null) {
                onGetValidValueListener.callback();
            }
            updateProperties(true);
        } else {
            Log.e(TAG, "CarService returned value is invalid");
        }
    }

    private void updateProperties(boolean value) {
        bPropertiesUpdate = value;
    }

    private boolean isUpdateProperties() {
        return bPropertiesUpdate;
    }

    /**
     * CarService got value from MCU
     */
    public interface OnGetValidValueListener {
        void callback();
    }

    public interface OnCarPowerStateListener {
        void standby();

        void run();
    }
}
