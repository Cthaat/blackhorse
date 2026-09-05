package com.ruoyi.lab.task;

/** Bounded task policy shared by submission and execution. */
public final class TaskRules
{
    private TaskRules() { }
    public static void validateUpload(long bytes)
    { if (bytes <= 0 || bytes > 5L * 1024 * 1024) throw new IllegalArgumentException("文件大小须在五兆以内且非空"); }
    public static boolean cancellable(String state)
    { return java.util.Set.of("PRECHECKED", "QUEUED", "RUNNING").contains(state); }
    public static String result(int succeeded, int failed)
    { return failed == 0 ? "SUCCEEDED" : succeeded == 0 ? "FAILED" : "PARTIAL"; }
}
