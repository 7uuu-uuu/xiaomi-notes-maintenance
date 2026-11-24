package net.micode.notes;

import net.micode.notes.tool.DataUtils;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 本次维护修复方法 {@link DataUtils#getFormattedSnippet(String)} 空值安全行为的本地单元测试。
 */
public class DataUtilsSnippetTest {

    @Test
    public void snippet_null_returnsEmptyString() {
        // 修复前 null 会原样返回，下游调用易触发空指针；修复后应返回空串
        assertEquals("", DataUtils.getFormattedSnippet(null));
    }

    @Test
    public void snippet_multiline_returnsFirstLineTrimmed() {
        assertEquals("第一行", DataUtils.getFormattedSnippet(" 第一行\n第二行 "));
    }

    @Test
    public void snippet_singleLine_trimmed() {
        assertEquals("单行", DataUtils.getFormattedSnippet("  单行  "));
        assertEquals("", DataUtils.getFormattedSnippet("   "));
    }
}
