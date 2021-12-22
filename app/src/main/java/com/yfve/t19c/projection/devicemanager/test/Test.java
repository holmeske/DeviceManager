package com.yfve.t19c.projection.devicemanager.test;

import java.util.ArrayList;
import java.util.List;

public class Test {

    public static void main(String[] args) {
        List<String> list = new ArrayList<String>() {
        };
        for (int i = 0; i < 5; i++) {
            list.add("" + (i + 1));
        }
        if (true){
            if (false) return;
            System.out.print( "1");
        }else {

        }

        contains(list, "3");

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
}
