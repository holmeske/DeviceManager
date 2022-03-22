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

public final class StorageHelper {
    private static final String TAG = "StorageHelper";
    private static final String DATABASE_NAME = "device.db";
    private static final String TABLE_DEVICE_LIST = "device_list";
    private static final String COLUMN_SERIAL = "serial";
    private static final String COLUMN_BT = "btmac";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_CONTYPE = "type";
    private static final String COLUMN_ABILITY = "ability";
    private static final String COLUMN_CP_SUP = "cpSupport";
    private static final String COLUMN_AA_SUP = "aaSupport";
    private static final String COLUMN_CL_SUP = "clSupport";
    private static final String COLUMN_HC_SUP = "hcSupport";

    private static final String SQL_BT = "SELECT * FROM " + TABLE_DEVICE_LIST + " WHERE " + COLUMN_BT + "=?";

    private static final String SQL_SERIAL = "SELECT * FROM " + TABLE_DEVICE_LIST + " WHERE " + COLUMN_SERIAL + "=?";

    private static final String SQL_SERIAL_BT = "SELECT * FROM " + TABLE_DEVICE_LIST + " WHERE " + COLUMN_SERIAL + "=? OR " + COLUMN_BT + "=?";

    private static final String WHERE_CLAUSE_SERIAL = COLUMN_SERIAL + " = ?";
    private static final String WHERE_CLAUSE_BT = COLUMN_BT + " = ?";
    private static final String WHERE_CLAUSE_SERIAL_BT = COLUMN_SERIAL + " = ? AND " + COLUMN_BT + " = ?";
    private static final String WHERE_CLAUSE_SERIAL_BT_NAME = COLUMN_SERIAL + " = ? AND " + COLUMN_BT + " = ? AND " + COLUMN_NAME + " = ?";
    private final DeviceListSQLiteOpenHelper mDbHelper;

    public StorageHelper(Context context) {
        Log.d(TAG, "StorageHelper() called");
        mDbHelper = new DeviceListSQLiteOpenHelper(context);
    }

    public void clear() {
        Log.d(TAG, "clear() called");
        SQLiteDatabase sqliteDatabase = mDbHelper.getWritableDatabase();
        int ret = sqliteDatabase.delete(TABLE_DEVICE_LIST, null, null);
        Log.i(TAG, "delete ret:" + ret);
        sqliteDatabase.close();
    }

    public void deleteByMac(String mac) {
        Log.d(TAG, "deleteByMac() called with: mac = [" + mac + "]");
        SQLiteDatabase sqliteDatabase = mDbHelper.getWritableDatabase();
        int result = sqliteDatabase.delete(TABLE_DEVICE_LIST, WHERE_CLAUSE_BT, new String[]{mac});
        Log.d(TAG, "delete result = " + result);
    }

    public void deleteBySerial(String serial) {
        Log.d(TAG, "deleteBySerial() called with: serial = [" + serial + "]");
        SQLiteDatabase sqliteDatabase = mDbHelper.getWritableDatabase();
        int result = sqliteDatabase.delete(TABLE_DEVICE_LIST, WHERE_CLAUSE_SERIAL, new String[]{serial});
        Log.d(TAG, "delete result = " + result);
    }

    public void deleteBySerialMac(String serial, String mac) {
        Log.d(TAG, "deleteBySerialMac() called with: serial = [" + serial + "], mac = [" + mac + "]");
        SQLiteDatabase sqliteDatabase = mDbHelper.getWritableDatabase();
        int result = sqliteDatabase.delete(TABLE_DEVICE_LIST, WHERE_CLAUSE_SERIAL_BT, new String[]{serial, mac});
        Log.d(TAG, "delete result = " + result);
    }

    public void delete(Device device) {
        Log.d(TAG, "delete() called with: device = [" + device.toString() + "]");
        SQLiteDatabase sqliteDatabase = mDbHelper.getWritableDatabase();

        int result = sqliteDatabase.delete(TABLE_DEVICE_LIST, WHERE_CLAUSE_SERIAL_BT_NAME, new String[]{device.getSerial(), device.getMac(), device.getName()});

        Log.d(TAG, "delete result = " + result);
    }

    public void insert(Device device) {
        Log.d(TAG, "insert() called with: device = [" + device.toString() + "]");
        SQLiteDatabase sqliteDatabase = mDbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_NAME, device.getName());
        values.put(COLUMN_SERIAL, device.getSerial());
        values.put(COLUMN_BT, device.getMac());
        values.put(COLUMN_ABILITY, device.getAbility());

        try {
            long rowID = sqliteDatabase.insertOrThrow(TABLE_DEVICE_LIST, null, values);
            Log.i(TAG, "insert data end, rowID : " + rowID);
        } catch (Exception e) {
            Log.e(TAG, "" + e);
        }
        sqliteDatabase.close();
    }

    public void update(Device device) {
        Log.d(TAG, "update() called with: device = [" + device.toString() + "]");

        SQLiteDatabase sqliteDatabase = mDbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME, device.getName());
        values.put(COLUMN_SERIAL, device.getSerial());
        values.put(COLUMN_BT, device.getMac());
        values.put(COLUMN_ABILITY, device.getAbility());

        try {
            long rowID = sqliteDatabase.update(TABLE_DEVICE_LIST, values, WHERE_CLAUSE_SERIAL_BT_NAME, new String[]{device.getSerial(), device.getMac()});
            Log.i(TAG, "update data end,rowID:" + rowID);
        } catch (Exception e) {
            Log.e(TAG, "" + e);
        }

        sqliteDatabase.close();
    }

    public Device queryBySerial(String serial) {
        Log.d(TAG, "queryBySerial() called with: serial = [" + serial + "]");
        Device device = null;
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        try {
            Cursor cursor = db.rawQuery(SQL_SERIAL, new String[]{serial});
            if (cursor.moveToFirst()) {
                String s1 = cursor.getString(cursor.getColumnIndex(COLUMN_NAME));
                String s2 = cursor.getString(cursor.getColumnIndex(COLUMN_SERIAL));
                String s3 = cursor.getString(cursor.getColumnIndex(COLUMN_BT));
                int ability = cursor.getInt(cursor.getColumnIndex(COLUMN_ABILITY));
                device = new Device(s1, s2, s3, ability);
            }
            cursor.close();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        db.close();
        return device;
    }

    public Device queryByMac(String mac) {
        Log.d(TAG, "queryByMac() called with: mac = [" + mac + "]");
        Device device = null;
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        try {
            Cursor cursor = db.rawQuery(SQL_BT, new String[]{mac});
            if (cursor.moveToFirst()) {
                String s1 = cursor.getString(cursor.getColumnIndex(COLUMN_NAME));
                String s2 = cursor.getString(cursor.getColumnIndex(COLUMN_SERIAL));
                String s3 = cursor.getString(cursor.getColumnIndex(COLUMN_BT));
                int ability = cursor.getInt(cursor.getColumnIndex(COLUMN_ABILITY));
                device = new Device(s1, s2, s3, ability);
            }
            cursor.close();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        db.close();
        return device;
    }

    public Device query(String serial, String mac) {
        Log.d(TAG, "query() called with: serial = [" + serial + "], mac = [" + mac + "]");
        Device device = null;
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        try {
            Cursor cursor = db.rawQuery(SQL_SERIAL_BT, new String[]{serial, mac});
            if (cursor.moveToFirst()) {
                String s1 = cursor.getString(cursor.getColumnIndex(COLUMN_NAME));
                String s2 = cursor.getString(cursor.getColumnIndex(COLUMN_SERIAL));
                String s3 = cursor.getString(cursor.getColumnIndex(COLUMN_BT));
                int ability = cursor.getInt(cursor.getColumnIndex(COLUMN_ABILITY));
                device = new Device(s1, s2, s3, ability);
            }
            cursor.close();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        db.close();
        return device;
    }

    public List<Device> queryAll() {
        List<Device> deviceList = new ArrayList<>();
        SQLiteDatabase sqliteDatabase = mDbHelper.getReadableDatabase();
        Cursor cursor = sqliteDatabase.query(TABLE_DEVICE_LIST, null, null, null, null, null, null);
        while (cursor.moveToNext()) {
            String name = cursor.getString(cursor.getColumnIndex(COLUMN_NAME));
            String serial = cursor.getString(cursor.getColumnIndex(COLUMN_SERIAL));
            String mac = cursor.getString(cursor.getColumnIndex(COLUMN_BT));
            int ability = cursor.getInt(cursor.getColumnIndex(COLUMN_ABILITY));
            deviceList.add(new Device(name, serial, mac, ability));
        }
        cursor.close();
        sqliteDatabase.close();
        return deviceList;
    }

    private static class DeviceListSQLiteOpenHelper extends SQLiteOpenHelper {
        private static final int DATABASE_VERSION = 1;

        DeviceListSQLiteOpenHelper(Context context) {
            super(context.createDeviceProtectedStorageContext(), DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            createTable(db);
        }

        private void createTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE_DEVICE_LIST + " ("
                    + COLUMN_NAME + " TEXT,"
                    + COLUMN_SERIAL + " TEXT,"
                    + COLUMN_BT + " TEXT,"
                    + COLUMN_ABILITY + " INTEGER,"
                    + COLUMN_CONTYPE + " INTEGER,"
                    + COLUMN_CP_SUP + " INTEGER,"
                    + COLUMN_AA_SUP + " INTEGER,"
                    + COLUMN_CL_SUP + " INTEGER,"
                    + COLUMN_HC_SUP + " INTEGER)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        }
    }


}
