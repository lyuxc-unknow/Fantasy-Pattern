package cn.lyxc.fantasytechnology.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompactCountTest {

    @Test
    void formatsEnglishMagnitudes() {
        assertEquals("0", CompactCount.format(0));
        assertEquals("999", CompactCount.format(999));
        assertEquals("1K", CompactCount.format(1_000));
        assertEquals("1.5K", CompactCount.format(1_500));
        assertEquals("1.6M", CompactCount.format(1_600_000));
        assertEquals("6.4M", CompactCount.format(6_400_000));
        assertEquals("51.2M", CompactCount.format(51_200_000));
        assertEquals("100.2M", CompactCount.format(100_200_000));
        assertEquals("1B", CompactCount.format(1_000_000_000L));
        assertEquals("1.5B", CompactCount.format(1_500_000_000L));
        assertEquals("1T", CompactCount.format(1_000_000_000_000L));
        assertEquals("1M", CompactCount.format(999_950));
        assertEquals("999.9K", CompactCount.format(999_949));
    }
}
