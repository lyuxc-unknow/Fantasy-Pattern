package cn.lyxc.fantasytechnology.util;

/// Compact English magnitude labels for non-negative counts: {@code 1600000} becomes {@code 1.6M}.
public final class CompactCount {

    private static final String[] SUFFIXES = {"K", "M", "B", "T", "Qa", "Qi"};

    private CompactCount() {
    }

    public static String format(long value) {
        if (value < 1_000) {
            return Long.toString(Math.max(0, value));
        }

        int tier = 0;
        long scale = 1_000L;
        while (tier < SUFFIXES.length - 1 && value / 1_000L >= scale) {
            scale = Math.multiplyExact(scale, 1_000L);
            tier++;
        }

        long whole = value / scale;
        int tenth = tenthDigit(value % scale, scale);
        if (tenth == 10) {
            whole++;
            tenth = 0;
        }
        if (whole >= 1_000 && tier < SUFFIXES.length - 1) {
            whole /= 1_000;
            tier++;
            tenth = 0;
        }
        if (tenth == 0) {
            return whole + SUFFIXES[tier];
        }
        return whole + "." + tenth + SUFFIXES[tier];
    }

    /// Nearest tenth of {@code scale}, as a digit 0-10. 10 means the tenth rounded up into the next whole unit.
    private static int tenthDigit(long remainder, long scale) {
        long tenthScale = scale / 10L;
        return (int) ((remainder + tenthScale / 2L) / tenthScale);
    }
}
