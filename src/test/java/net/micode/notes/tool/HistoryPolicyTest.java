package net.micode.notes.tool;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * 本次维护新增历史版本保留策略 {@link HistoryPolicy} 的本地单元测试。
 * 覆盖归档判定、摘要生成、超限淘汰条数、保留期边界、过期判定与版本列表筛选等纯逻辑方法。
 */
public class HistoryPolicyTest {

    @Test
    public void shouldRecord_emptyPreviousContent_returnsFalse() {
        assertFalse(HistoryPolicy.shouldRecord(null, null));
        assertFalse(HistoryPolicy.shouldRecord("", "old"));
    }

    @Test
    public void shouldRecord_sameContent_returnsFalse() {
        assertFalse(HistoryPolicy.shouldRecord("内容 A", "内容 A"));
    }

    @Test
    public void shouldRecord_differentContent_returnsTrue() {
        assertTrue(HistoryPolicy.shouldRecord("内容 A", null));
        assertTrue(HistoryPolicy.shouldRecord("内容 A", "内容 B"));
    }

    @Test
    public void makeSnippet_collapsesWhitespaceAndNewlines() {
        assertEquals("第一行 第二行", HistoryPolicy.makeSnippet("第一行\n第二行", 40));
        assertEquals("a b", HistoryPolicy.makeSnippet("  a   b  ", 40));
        assertEquals("", HistoryPolicy.makeSnippet(null, 40));
    }

    @Test
    public void makeSnippet_truncatesWhenTooLong() {
        String longText = "0123456789012345678901234567890123456789";
        String snippet = HistoryPolicy.makeSnippet(longText, 10);
        assertEquals("0123456789...", snippet);
        assertEquals(longText.length(), 40);
    }

    @Test
    public void excessCount_onlyCountsOverflow() {
        assertEquals(0, HistoryPolicy.excessCount(0, 20));
        assertEquals(0, HistoryPolicy.excessCount(19, 20));
        assertEquals(1, HistoryPolicy.excessCount(20, 20));
        assertEquals(3, HistoryPolicy.excessCount(22, 20));
    }

    @Test
    public void retentionBoundary_andIsExpired_workOnDayBoundary() {
        long now = 1000L * HistoryPolicy.MILLIS_PER_DAY * 40;
        long boundary = HistoryPolicy.retentionBoundary(now, 30);
        assertEquals(now - 30L * HistoryPolicy.MILLIS_PER_DAY, boundary);
        // 早于边界的版本过期，恰好落在边界上的版本仍在保留期内
        assertFalse(HistoryPolicy.isExpired(boundary, now, 30));
        assertTrue(HistoryPolicy.isExpired(boundary - 1, now, 30));
    }

    @Test
    public void isExpired_invalidDate_returnsFalse() {
        assertFalse(HistoryPolicy.isExpired(0, System.currentTimeMillis(), 30));
    }

    @Test
    public void selectRetained_keepsNewestUniqueVersions() {
        List<String> versions = new ArrayList<String>();
        versions.add("最新");
        versions.add("最新");
        versions.add("次新");
        versions.add("");
        versions.add("较旧");
        List<String> retained = HistoryPolicy.selectRetained(versions, 2);
        assertEquals(2, retained.size());
        assertEquals("最新", retained.get(0));
        assertEquals("次新", retained.get(1));
    }

    @Test
    public void selectRetained_invalidLimit_returnsEmpty() {
        List<String> versions = new ArrayList<String>();
        versions.add("版本");
        assertTrue(HistoryPolicy.selectRetained(versions, 0).isEmpty());
        assertTrue(HistoryPolicy.selectRetained(null, 20).isEmpty());
    }
}
