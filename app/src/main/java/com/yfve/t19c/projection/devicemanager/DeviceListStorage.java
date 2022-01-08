/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yfve.t19c.projection.devicemanager;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides API to pergetProtocol() failed. Retrying...sist USB device settings.
 */
public final class DeviceListStorage {
    private static final String DATABASE_NAME = "deviceManager.db";
    private static final String TAG = DeviceListStorage.class.getSimpleName();
    private static final String TABLE_DEVICE_LIST = "devices_info_list";
    private static final String COLUMN_SERIAL = "serial";
    private static final String COLUMN_BT = "btmac";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_CONTYPE = "type";
    private static final String COLUMN_CP_SUP = "cpSupport";
    private static final String COLUMN_AA_SUP = "aaSupport";
    private static final String COLUMN_CL_SUP = "clSupport";
    private static final String COLUMN_HC_SUP = "hcSupport";
    private final DeviceListDbHelper mDbHelper;

    public DeviceListStorage(Context context) {
        Log.d(TAG, "DeviceListStorage() called");
        mDbHelper = new DeviceListDbHelper(context);
    }

    public void saveDeviceList(List<DeviceInfo> deviceList) {
        SQLiteDatabase sqliteDatabase = mDbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        for (DeviceInfo device : deviceList) {
            values.put(COLUMN_SERIAL, device.SerialNumber);
            values.put(COLUMN_BT, device.BluetoothMac);
            values.put(COLUMN_NAME, device.DeviceName);
            values.put(COLUMN_CONTYPE, device.ConnectionType);
            values.put(COLUMN_CP_SUP, device.AppAvailable[DeviceInfo.Connectivity_AppType_CarPlay]);
            values.put(COLUMN_AA_SUP, device.AppAvailable[DeviceInfo.Connectivity_AppType_AndroidAuto]);
            values.put(COLUMN_CL_SUP, device.AppAvailable[DeviceInfo.Connectivity_AppType_CarLife]);
            values.put(COLUMN_HC_SUP, device.AppAvailable[DeviceInfo.Connectivity_AppType_HiCar]);

            try {
                long rowID = sqliteDatabase.insertOrThrow(TABLE_DEVICE_LIST, null, values);
                Log.i(TAG, "insert data end,rowID:" + rowID);
            } catch (Exception SQLException) {
                Log.i(TAG, "Exception:" + SQLException);
            }
        }
        sqliteDatabase.close();
    }

    public void clearDataBase() {
        Log.d(TAG, "clearDataBase() called");
        SQLiteDatabase sqliteDatabase = mDbHelper.getWritableDatabase();
        int ret = sqliteDatabase.delete(TABLE_DEVICE_LIST, null, null);
        Log.i(TAG, "delete ret:" + ret);
        sqliteDatabase.close();
    }

    public void deleteDevice(DeviceInfo device) {
        SQLiteDatabase sqliteDatabase = mDbHelper.getWritableDatabase();

        int result = sqliteDatabase.delete(TABLE_DEVICE_LIST, COLUMN_SERIAL + " = ? AND " + COLUMN_BT + " = ? AND " + COLUMN_NAME + " = ?",
                new String[]{device.SerialNumber, device.BluetoothMac, device.DeviceName});
        if (result == 0) {
            Log.w(TAG, "No settings with serialNumber: " + device.SerialNumber + " btMac: " + device.BluetoothMac + " deviceName: " + device.DeviceName);
        }
        if (result > 1) {
            Log.e(TAG, "Deleted multiple rows (" + result + ") for serialNumber: " + device.SerialNumber + " btMac: " + device.BluetoothMac + " deviceName: " + device.DeviceName);
        }
    }

    public void saveDevice(DeviceInfo device) {
        Log.d(TAG, "saveDevice() called with: device = [" + device.toString() + "]");
        SQLiteDatabase sqliteDatabase = mDbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_SERIAL, device.SerialNumber);
        values.put(COLUMN_BT, device.BluetoothMac);
        values.put(COLUMN_NAME, device.DeviceName);
        values.put(COLUMN_CONTYPE, device.ConnectionType);
        values.put(COLUMN_CP_SUP, device.AppAvailable[DeviceInfo.Connectivity_AppType_CarPlay]);
        values.put(COLUMN_AA_SUP, device.AppAvailable[DeviceInfo.Connectivity_AppType_AndroidAuto]);
        values.put(COLUMN_CL_SUP, device.AppAvailable[DeviceInfo.Connectivity_AppType_CarLife]);
        values.put(COLUMN_HC_SUP, device.AppAvailable[DeviceInfo.Connectivity_AppType_HiCar]);

        try {
            long rowID = sqliteDatabase.insertOrThrow(TABLE_DEVICE_LIST, null, values);
            Log.i(TAG, "insert data end,rowID:" + rowID);
        } catch (Exception SQLException) {
            Log.i(TAG, "Exception:" + SQLException);
        }
        sqliteDatabase.close();
    }

    public List<DeviceInfo> readDeviceList() {
        List<DeviceInfo> deviceList = new ArrayList<>();
        SQLiteDatabase sqliteDatabase = mDbHelper.getReadableDatabase();
        // Cursor cursor = sqliteDatabase.query(TABLE_DEVICE_LIST, new String[] {COLUMN_SERIAL,COLUMN_BT,COLUMN_NAME,COLUMN_CONTYPE}
        //     , null, null, null, null, null);
        Cursor cursor = sqliteDatabase.query(TABLE_DEVICE_LIST, null, null, null, null, null, null);
        // 第一个参数:The table name to compile the query against.
        // 第二个参数:A list of which columns to return. Passing null will return all columns, which is discouraged to prevent reading data from storage that isn't going to be used
        // 第三个参数:A filter declaring which rows to return, formatted as an SQL WHERE clause (excluding the WHERE itself). Passing null will return all rows for the given table.
        // 第四个参数:You may include ?s in selection, which will be replaced by the values from selectionArgs, in order that they appear in the selection. The values will be bound as Strings.
        // 第五个参数:A filter declaring how to group rows, formatted as an SQL GROUP BY clause (excluding the GROUP BY itself). Passing null will cause the rows to not be grouped.
        // 第六个参数:A filter declare which row groups to include in the cursor, if row grouping is being used, formatted as an SQL HAVING clause (excluding the HAVING itself). Passing null will cause all row groups to be included, and is required when row grouping is not being used.
        // 第七个参数:How to order the rows, formatted as an SQL ORDER BY clause (excluding the ORDER BY itself). Passing null will use the default sort order, which may be unordered
        while (cursor.moveToNext()) {
            String serial = cursor.getString(cursor.getColumnIndex(COLUMN_SERIAL));
            String btMac = cursor.getString(cursor.getColumnIndex(COLUMN_BT));
            String deviceName = cursor.getString(cursor.getColumnIndex(COLUMN_NAME));
            int conType = cursor.getInt(cursor.getColumnIndex(COLUMN_CONTYPE));

            boolean isSupportCP = cursor.getInt(cursor.getColumnIndex(COLUMN_CP_SUP)) > 0;
            boolean isSupportAA = cursor.getInt(cursor.getColumnIndex(COLUMN_AA_SUP)) > 0;
            boolean isSupportCL = cursor.getInt(cursor.getColumnIndex(COLUMN_CL_SUP)) > 0;
            boolean isSupportHC = cursor.getInt(cursor.getColumnIndex(COLUMN_HC_SUP)) > 0;

            DeviceInfo info = new DeviceInfo(serial, btMac, deviceName, conType, isSupportCP, isSupportAA, isSupportCL, isSupportHC);
            deviceList.add(info);
        }
        cursor.close();
        sqliteDatabase.close();
        return deviceList;
    }

    private static class DeviceListDbHelper extends SQLiteOpenHelper {
        private static final int DATABASE_VERSION = 2;

        // we are using device protected storage because we may need to access the db before the
        // user has authenticated
        DeviceListDbHelper(Context context) {
            super(context.createDeviceProtectedStorageContext(), DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            createTable(db);
            // createSerialIndex(db);
        }

        private void createTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + DeviceListStorage.TABLE_DEVICE_LIST + " ("
                    + COLUMN_SERIAL + " TEXT,"
                    + COLUMN_BT + " TEXT,"
                    + COLUMN_NAME + " TEXT,"
                    + COLUMN_CONTYPE + " INTEGER,"
                    + COLUMN_CP_SUP + " INTEGER,"
                    + COLUMN_AA_SUP + " INTEGER,"
                    + COLUMN_CL_SUP + " INTEGER,"
                    + COLUMN_HC_SUP + " INTEGER)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.i(TAG, "onUpgrade");
            // for (; oldVersion != newVersion; oldVersion++) {
            //     switch (oldVersion) {
            //         case 1:
            //             String tempTableName = "temp_" + TABLE_DEVICE_LIST;
            //             createTable(db, tempTableName);
            //             db.execSQL("INSERT INTO " + tempTableName
            //                     + " SELECT * FROM " + TABLE_DEVICE_LIST);
            //             db.execSQL("DROP TABLE " + TABLE_DEVICE_LIST);
            //             db.execSQL("ALTER TABLE " + tempTableName + " RENAME TO "
            //                     + TABLE_DEVICE_LIST);
            //             break;
            //         default:
            //             throw new IllegalArgumentException(
            //                     "Unknown database version " + oldVersion);
            //     }
            // }
        }
    }
}
