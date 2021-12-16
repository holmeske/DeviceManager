package com.yfve.t19c.projection.devicemanager;

import android.car.Car;
import android.car.CarInfoManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

public class CarHelper {
    private static final String TAG = "CarHelper";
    private final Handler mHandler = new Handler();
    private String presentAndroidAuto = "";
    private String presentCarPlay = "";
    private Car mCar;
    private CarInfoManager mCarInfoManager;
    private byte[] property;
    private OnGetBytePropertyListener onGetBytePropertyListener;
    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            if (property != null && mCarInfoManager != null) {
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

    public CarHelper(Context mContext) {
        Log.d(TAG, "CarHelper() called");
        initCar(mContext);
    }

    public static String byteToBit(byte b) {
        return "" + (byte) ((b >> 7) & 0x1) +

                (byte) ((b >> 6) & 0x1) +

                (byte) ((b >> 5) & 0x1) +

                (byte) ((b >> 4) & 0x1) +

                (byte) ((b >> 3) & 0x1) +

                (byte) ((b >> 2) & 0x1) +

                (byte) ((b >> 1) & 0x1) +

                (byte) ((b >> 0) & 0x1);

    }

    public void setOnGetBytePropertyListener(OnGetBytePropertyListener onGetBytePropertyListener) {
        this.onGetBytePropertyListener = onGetBytePropertyListener;
    }

    public boolean isPresentAndroidAuto() {
        if (presentAndroidAuto.equals("1")) {
            return true;
        } else {
            Log.d(TAG, "Android Auto not present");
            return false;
        }
    }

    public boolean isPresentCarPlay() {
        if (presentCarPlay.equals("1")) {
            return true;
        } else {
            Log.d(TAG, "CarPlay not present");
            return false;
        }
    }

    private void initCar(Context context) {
        mCar = Car.createCar(context, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "onServiceConnected() called with: name = [" + name.getPackageName() + "]");
                if (mCar != null) {
                    try {
                        mCarInfoManager = (CarInfoManager) mCar.getCarManager(Car.INFO_SERVICE);
                        if (mCarInfoManager != null) {
                            property = mCarInfoManager.getByteProperty(CarInfoManager.ID_DIAGNOSTIC_CONFIG_701A);

                            //property = new byte[]{0x00};
                            if (property.length != 8) {
                                mHandler.postDelayed(runnable, 1000);
                            } else {
                                processValidValue();
                            }

                        } else {
                            Log.i(TAG, "CarInfoManager is null");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "onServiceConnected: ", e);
                    }
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG, "onServiceDisconnected() called with: name = [" + name.getPackageName() + "]");
            }
        });
        if (mCar != null) {
            mCar.connect();
        }

    }

    public void release() {
        if (mCar != null) {
            mCar.disconnect();
        }
    }

    private void processValidValue() {
        if (onGetBytePropertyListener != null) {
            onGetBytePropertyListener.callback();
        }

        for (int i = 0; i < property.length; i++) {
            Log.d(TAG, "property[" + i + "] = " + byteToBit(property[i]));
        }

        if (property.length > 2) {
            String byte2 = byteToBit(property[2]);
            presentAndroidAuto = byte2.substring(1, 2);//第7个bit
            Log.d(TAG, "presentAndroidAuto: " + presentAndroidAuto);

            presentCarPlay = byte2.substring(4, 5);//第4个bit
            Log.d(TAG, "presentCarPlay: " + presentCarPlay);
        } else {
            Log.d(TAG, "CarService returned value is invalid");
        }
    }

    /**
     * CarService got value from MCU
     */
    public interface OnGetBytePropertyListener {
        void callback();
    }

}
