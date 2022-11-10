package com.yfve.t19c.projection.devicemanager.test;

import android.util.Log;

import java.util.List;

public class Test {

    private static final String TAG = "Test";

    public static void main(String[] args) {
        /*for (int i = 0; i < 3; i++) {
            a(i);
        }*/
        System.out.println("xhci-hcd.1.auto".contains("."));
        try {
            String mac = "160:59:227:164:74:196";
            String[] strings = mac.split(":");

            StringBuilder sb = new StringBuilder();

            for (String s : strings) {
                String sixteen = Integer.toHexString(Integer.parseInt(s));
                System.out.println(sixteen.toUpperCase());
                sb.append(sixteen.toUpperCase()).append(":");
            }

            System.out.println(sb);

            System.out.println(sb.substring(0, sb.length() - 1));
        } catch (Exception e) {
            Log.e(TAG, "" + e);
        }
    }

    private static void a(int a) {
        System.out.print("0");
        if (a == 1) {
            return;
        }
        System.out.println("1");
    }


    private static boolean contains(List<String> list, String s) {
        for (String string : list) {
            System.out.print(string + " ");
            if (s.equals(string)) {
                return true;
            }
        }
        return false;
    }

    public static void t() {
    }
}
