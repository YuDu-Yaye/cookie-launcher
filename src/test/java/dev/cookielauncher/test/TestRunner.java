package dev.cookielauncher.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Zero-dependency test runner.
 *
 * <p>Discovers test classes from the {@code tests.list} classpath resource and
 * runs every static {@code test*} method. No JUnit: the launcher ships with zero
 * runtime dependencies, so its tests need zero build dependencies too.</p>
 */
public final class TestRunner {

    private static int passed;
    private static int failed;
    private static final List<String> failures = new ArrayList<>();

    private TestRunner() {
    }

    public static void main(String[] args) {
        List<String> classes = discover(args);
        if (classes.isEmpty()) {
            System.out.println("No tests discovered. Pass class names or add tests.list.");
            System.exit(1);
        }
        long start = System.currentTimeMillis();
        for (String className : classes) {
            runClass(className);
        }
        long elapsed = System.currentTimeMillis() - start;
        System.out.println();
        System.out.println("=".repeat(66));
        System.out.printf("Tests: %d passed, %d failed (%d ms)%n", passed, failed, elapsed);
        if (failed > 0) {
            System.out.println();
            System.out.println("Failures:");
            for (String failure : failures) {
                System.out.println("  - " + failure);
            }
        }
        System.out.println("=".repeat(66));
        System.exit(failed == 0 ? 0 : 1);
    }

    private static List<String> discover(String[] args) {
        List<String> classes = new ArrayList<>();
        for (String arg : args) {
            if (!arg.startsWith("-")) {
                classes.add(arg);
            }
        }
        if (!classes.isEmpty()) {
            return classes;
        }
        try (var stream = TestRunner.class.getClassLoader().getResourceAsStream("tests.list")) {
            if (stream == null) {
                return classes;
            }
            for (String line : new String(stream.readAllBytes()).split("\\R")) {
                String trimmed = line.strip();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    classes.add(trimmed);
                }
            }
        } catch (Exception e) {
            System.out.println("Cannot read tests.list: " + e.getMessage());
        }
        return classes;
    }

    private static void runClass(String className) {
        Class<?> type;
        try {
            type = Class.forName(className);
        } catch (ClassNotFoundException e) {
            failed++;
            failures.add(className + ": class not found");
            return;
        }
        List<Method> tests = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().startsWith("test")
                    && method.getParameterCount() == 0
                    && Modifier.isStatic(method.getModifiers())) {
                tests.add(method);
            }
        }
        tests.sort(Comparator.comparing(Method::getName));
        if (tests.isEmpty()) {
            return;
        }
        System.out.println();
        System.out.println(type.getSimpleName());
        for (Method test : tests) {
            test.setAccessible(true);
            String label = humanize(test.getName());
            try {
                test.invoke(null);
                passed++;
                System.out.printf("  PASS  %s%n", label);
            } catch (InvocationTargetException e) {
                failed++;
                Throwable cause = e.getCause() == null ? e : e.getCause();
                failures.add(type.getSimpleName() + "." + test.getName() + ": " + cause.getMessage());
                System.out.printf("  FAIL  %s%n", label);
                System.out.printf("        %s%n", cause);
                StackTraceElement[] trace = cause.getStackTrace();
                for (int i = 0; i < Math.min(3, trace.length); i++) {
                    System.out.printf("        at %s%n", trace[i]);
                }
            } catch (Exception e) {
                failed++;
                failures.add(type.getSimpleName() + "." + test.getName() + ": " + e);
                System.out.printf("  ERROR %s (%s)%n", label, e);
            }
        }
    }

    private static String humanize(String methodName) {
        String name = methodName.startsWith("test") ? methodName.substring(4) : methodName;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                builder.append(' ');
            }
            builder.append(Character.toLowerCase(c));
        }
        return builder.toString();
    }
}
