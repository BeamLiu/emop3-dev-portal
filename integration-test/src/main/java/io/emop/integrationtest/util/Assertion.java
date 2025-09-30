package io.emop.integrationtest.util;

public class Assertion {
    public static void assertEquals(Object expect, Object actual, String message) {
        boolean result;
        if (expect == null) {
            result = actual == null;
        } else if (actual == null) {
            result = expect == null;
        } else {
            result = expect.equals(actual);
        }
        if (!result) {
            throw new RuntimeException(message != null ? message : ("except " + expect + " is not euqal to actual " + actual));
        }
    }

    public static void assertEquals(Object expect, Object actual) {
        assertEquals(expect, actual, null);
    }

    public static void assertNotEquals(Object expect, Object actual) {
        assertNotEquals(expect, actual, null);
    }

    public static void assertNotEquals(Object expect, Object actual, String msg) {
        boolean result;
        if (expect == null) {
            result = actual != null;
        } else if (actual == null) {
            result = true;  // expect不为null而actual为null，必然不相等
        } else {
            result = !expect.equals(actual);
        }
        if (!result) {
            throw new RuntimeException("expect " + expect + " should not be equal to actual " + actual + (msg != null ? msg : ""));
        }
    }

    public static void assertNotNull(Object a) {
        if (a == null) {
            throw new RuntimeException("target is not expected to null.");
        }
    }

    public static void assertNotNull(Object a, String msg) {
        if (a == null) {
            throw new RuntimeException(msg);
        }
    }

    public static void assertNull(Object a) {
        if (a != null) {
            throw new RuntimeException("target is expected to null.");
        }
    }

    public static void assertNull(Object a, String msg) {
        if (a != null) {
            throw new RuntimeException(msg);
        }
    }

    public static <T, R> void assertException(Runnable fun) {
        boolean encounterException = false;
        try {
            fun.run();
            encounterException = false;
        } catch (Throwable e) {
            System.out.println("expected exception, please ignire above stacktrace: " + e.getMessage());
            encounterException = true;
        }
        if (!encounterException) {
            throw new RuntimeException("expect exception, but not encountered.");
        }
    }

    public static void assertTrue(boolean result) {
        if (!result) {
            throw new RuntimeException("expected true, but encountered false.");
        }
    }

    public static void assertTrue(boolean result, String message) {
        if (!result) {
            throw new RuntimeException(message);
        }
    }

    public static void assertTrue(String message, boolean result) {
        if (!result) {
            throw new RuntimeException(message);
        }
    }

    public static void assertFalse(boolean result) {
        if (result) {
            throw new RuntimeException("expected false, but encountered " + result);
        }
    }

    public static void assertFalse(boolean result, String message) {
        if (result) {
            throw new RuntimeException(message);
        }
    }

    public static void assertFalse(String message, boolean result) {
        if (result) {
            throw new RuntimeException(message);
        }
    }

    public static void fail(String message) {
        throw new RuntimeException(message);
    }
}
