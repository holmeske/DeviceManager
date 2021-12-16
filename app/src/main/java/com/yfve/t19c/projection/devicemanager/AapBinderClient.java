package com.yfve.t19c.projection.devicemanager;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import com.yfve.t19c.projection.androidauto.IAapReceiverService;
import com.yfve.t19c.projection.androidauto.ISessionStatusListener;
import com.yfve.t19c.projection.devicemanager.callback.OnCallBackListener;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;


public class AapBinderClient implements IBinder.DeathRecipient {
    public static final String TAG = "AapBinderClient";
    public static final String DESCRIPTOR = "vendor.aapreceiver";
    private static final int BIND_FAIL_RETRY_CNT = 10;
    private static final int BIND_FAIL_RETRY_INTERVAL = 1000;
    private final List<AapListener> mCallbackList = new ArrayList<>();
    private final ISessionStatusListener mAapSessionStsListener = new ISessionStatusListener.Stub() {
        @Override
        public void sessionStarted(boolean b, String smallIcon, String mediumIcon, String largeIcon, String label,
                                   String deviceName, String instanceId) {
            Log.i(TAG, "sessionStarted is called()");
            for (AapListener l : mCallbackList) {
                try {
                    l.sessionStarted(b, smallIcon, mediumIcon, largeIcon, label, deviceName, instanceId);
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
            }
        }

        @Override
        public void sessionTerminated(boolean b, int reason) {
            Log.i(TAG, "sessionTerminated is called()");
            for (AapListener l : mCallbackList) {
                try {
                    l.sessionTerminated(b, reason);
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
            }
        }
    };
    private IAapReceiverService mAapClient = null;
    private HandlerThread mHandlerThread = null;
    private OnCallBackListener onCallBackListener;

    public void setOnCallBackListener(OnCallBackListener onCallBackListener) {
        this.onCallBackListener = onCallBackListener;
    }

    public AapBinderClient() {
        if (mHandlerThread == null) mHandlerThread = new HandlerThread(TAG);
        //connectService();
    }

    public IAapReceiverService getClient() {
        return mAapClient;
    }

    public boolean registerListener(AapListener listener) {
        if (listener == null) return false;
        if (!mCallbackList.contains(listener)) mCallbackList.add(listener);
        return connectService();
    }

    public void unregisterListener(AapListener listener) {
        mCallbackList.remove(listener);
        if (mAapClient != null) {
            try {
                mAapClient.unregisterStatusListener(mAapSessionStsListener);
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException:" + e);
            }
        }
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
        }
    }

    private void getBinderClient() {
        IBinder binder = null;
        int cnt = 0;
        while (cnt < BIND_FAIL_RETRY_CNT) {
            Log.i(TAG, "try to get binder");
            binder = ServiceManager.getService(DESCRIPTOR);
            if (binder != null) break;
            try {
                Thread.sleep(BIND_FAIL_RETRY_INTERVAL);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            cnt++;
        }

        if (binder == null) {
            Log.i(TAG, "Binder still null");
            return;
        }

        Log.i(TAG, "IAapReceiverService is bound");
        if (onCallBackListener!=null){
            onCallBackListener.callback();
        }

        try {
            binder.linkToDeath(this, 0);
        } catch (RemoteException e) {
            e.printStackTrace();
            Log.i(TAG, e.toString());
        }

        mAapClient = IAapReceiverService.Stub.asInterface(binder);

        if (null == mAapClient) {
            Log.i(TAG, "Service is null");
            return;
        }

        try {
            mAapClient.registerStatusListener(mAapSessionStsListener);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException:" + e);
        }
    }

    private boolean connectService() {
        Log.d(TAG, "connectService() called");
        if (mAapClient != null) {
            Log.i(TAG, "already binded");
            return true;
        }

        if (!mHandlerThread.isAlive()) {
            mHandlerThread.start();
        }
        //Log.i(TAG, "try to get binder in sub thread");
//        getThreadHandler().post(this::getBinderClient);
        Log.i(TAG, "connectService Thread == " + Thread.currentThread().getId());
        new Handler().post(this::getBinderClient);
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Handler Thread == " + Thread.currentThread().getId());
            }
        });
        return true;
    }

    Handler getThreadHandler() {
        Handler mHandler = null;
        try {
            Class cls = mHandlerThread.getClass();
            Method method = cls.getMethod("getThreadHandler");
            if (!method.isAccessible()) {
                method.setAccessible(true);
            }
            mHandler = (Handler) method.invoke(mHandlerThread);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            Log.e(TAG, "getThreadHandler: ", e);
        }
        return mHandler;
    }

    @Override
    public void binderDied() {
        Log.i(TAG, "binderDied");
        mAapClient = null;
    }

    public int startSession(String deviceName) {
        Log.d(TAG, "android auto startSession() called with: usbDeviceAddress = [" + deviceName + "]");
        int ret = -1;
        if (mAapClient != null) {
            try {
                ret = mAapClient.startSession(true, deviceName);
            } catch (RemoteException e) {
                Log.e(TAG, "startSession: ", e);
            }
        } else {
            Log.e(TAG, "mAapClient == null");
        }
        return ret;
    }

    public void stopSession() {
        Log.d(TAG, "android auto stopSession() called");
        if (mAapClient != null) {
            try {
                mAapClient.stopSession(true);
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException:" + e);
            }
        } else {
            Log.e(TAG, "mAapClient is null");
        }
    }
}
