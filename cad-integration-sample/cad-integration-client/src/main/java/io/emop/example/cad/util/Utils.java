package io.emop.example.cad.util;

public class Utils {
    public static String previewString(String str) {
        return str.substring(0, str.length() > 1000 ? 1000 : str.length()) + "...";
    }
}
