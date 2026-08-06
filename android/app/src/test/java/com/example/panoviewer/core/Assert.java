package com.example.panoviewer.core;

/**
 * 极简断言工具：避免引入 JUnit（遵守「零新增 Gradle 依赖」约束）。
 * 测试以 {@code public static void main} 形式运行，失败即抛 {@link AssertionError}。
 */
public final class Assert {

    private Assert() {
    }

    public static void assertTrue(boolean cond) {
        if (!cond) {
            throw new AssertionError("assertTrue failed");
        }
    }

    public static void assertFalse(boolean cond) {
        if (cond) {
            throw new AssertionError("assertFalse failed");
        }
    }

    public static void assertEquals(Object expected, Object actual) {
        boolean equal = (expected == null) ? (actual == null) : expected.equals(actual);
        if (!equal) {
            throw new AssertionError("assertEquals failed: expected=[" + expected
                    + "] actual=[" + actual + "]");
        }
    }

    public static void assertNotNull(Object o) {
        if (o == null) {
            throw new AssertionError("assertNotNull failed");
        }
    }

    public static void fail(String msg) {
        throw new AssertionError("fail: " + msg);
    }
}
