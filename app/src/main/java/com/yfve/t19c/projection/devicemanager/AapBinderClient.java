package com.yfve.t19c.projection.devicemanager;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import com.yfve.t19c.projection.androidauto.IAapReceiverService;
import com.yfve.t19c.projection.androidauto.ISessionStatusListener;

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
        public void sessionStarted(boolean b, String smallIcon, String mediumIcon, String largeIcon, String label, String deviceName, String instanceId) {
            for (AapListener l : mCallbackList) {
                if (l != null) {
                    l.sessionStarted(b, smallIcon, mediumIcon, largeIcon, label, deviceName, instanceId);
                } else {
                    Log.e(TAG, "sessionStarted() AapListener is null");
                }
            }
        }

        @Override
        public void sessionTerminated(boolean b, int reason) {
            for (AapListener l : mCallbackList) {
                if (l != null) {
                    l.sessionTerminated(b, reason);
                } else {
                    Log.e(TAG, "sessionTerminated() AapListener is null");
                }
            }
        }

        @Override
        public void resultOfProbe(String s, int i) {
            for (AapListener l : mCallbackList) {
                if (l != null) {
                    l.resultOfProbe(s, i);
                } else {
                    Log.e(TAG, "resultOfProbe() AapListener is null");
                }
            }
        }
    };
    private final AppController mAppController;
    private IAapReceiverService mIAapReceiverService = null;
    private HandlerThread mHandlerThread = null;
    private OnBindIAapReceiverServiceListener listener;

    public AapBinderClient(AppController appController) {
        this.mAppController = appController;
        if (mHandlerThread == null) mHandlerThread = new HandlerThread(TAG);
    }

    public void registerListener(AapListener listener) {
        if (listener == null) return;
        if (!mCallbackList.contains(listener)) mCallbackList.add(listener);
        connectService();
    }

    public void startProbe(String serialNumber, String deviceName) {
        Log.d(TAG, "startProbe() called with: serialNumber = [" + serialNumber + "], deviceName = [" + deviceName + "]");
        try {
            if (mIAapReceiverService == null) {
                Log.d(TAG, "mIAapReceiverService == null");
            } else {
                mIAapReceiverService.startProbe(serialNumber, deviceName);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void unregisterListener(AapListener listener) {
        mCallbackList.remove(listener);

        if (mIAapReceiverService != null) {
            try {
                mIAapReceiverService.unregisterStatusListener(mAapSessionStsListener);
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
            Log.d(TAG, "try to get binder");
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
            Log.e(TAG, "Binder still null");
            return;
        }

        try {
            binder.linkToDeath(this, 0);
        } catch (RemoteException e) {
            Log.e(TAG, e.toString());
        }

        mIAapReceiverService = IAapReceiverService.Stub.asInterface(binder);
        if (listener != null) listener.success();

        if (null == mIAapReceiverService) {
            Log.e(TAG, "Service is null");
            return;
        }

        try {
            mIAapReceiverService.registerStatusListener(mAapSessionStsListener);
        } catch (RemoteException e) {
            Log.e(TAG, e.toString());
        }
    }

    private void connectService() {
        Log.d(TAG, "connectService() called");
        if (mIAapReceiverService != null) {
            Log.i(TAG, "already bind");
            return;
        }
        if (!mHandlerThread.isAlive()) {
            mHandlerThread.start();
        }
        new Handler().post(this::getBinderClient);
    }

    @Override
    public void binderDied() {
        Log.i(TAG, "binderDied");
        mIAapReceiverService = null;
        if (mAppController != null && mAppController.currentSessionIsAndroidAuto()) {
            mAppController.updateIdleState();
        } else {
            Log.d(TAG, "mAppController == null");
        }
        getBinderClient();
    }

    public void startAndroidAuto(String deviceName) {
        Log.d(TAG, "startAndroidAuto() called with: deviceName = [" + deviceName + "]");
        if (mIAapReceiverService != null) {
            try {
                mIAapReceiverService.startSession(true, deviceName);
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
        } else {
            Log.e(TAG, "IAapReceiverService is null");
        }
    }

    public void stopAndroidAuto() {
        Log.d(TAG, "stopAndroidAuto() called");
        if (mIAapReceiverService != null) {
            try {
                mIAapReceiverService.stopSession(true);
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
        } else {
            Log.e(TAG, "IAapReceiverService is null");
        }
    }

    public void setOnBindIAapReceiverServiceListener(OnBindIAapReceiverServiceListener listener) {
        this.listener = listener;
    }

    public interface OnBindIAapReceiverServiceListener {
        void success();
    }
}
