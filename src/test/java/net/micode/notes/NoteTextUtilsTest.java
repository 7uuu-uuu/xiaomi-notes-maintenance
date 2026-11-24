package net.micode.notes.tool;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 本次维护新增工具类 {@link NoteTextUtils} 的本地单元测试。
 * 覆盖空值判断、字符数/词数/行数统计、清单符号剥离、超长截断与清单勾选进度统计等纯逻辑方法。
 */
public class NoteTextUtilsTest {

    @Test
    public void isEmpty_nullAndBlank_returnsTrue() {
        assertTrue(NoteTextUtils.isEmpty(null));
        assertTrue(NoteTextUtils.isEmpty(""));
        assertTrue(NoteTextUtils.isEmpty("   \n\t "));
    }

    @Test
    public void isEmpty_hasContent_returnsFalse() {
        assertFalse(NoteTextUtils.isEmpty("hello"));
        assertFalse(NoteTextUtils.isEmpty("  便签  "));
    }

    @Test
    public void countChars_ignoresWhitespace() {
        assertEquals(0, NoteTextUtils.countChars(null));
        assertEquals(0, NoteTextUtils.countChars("   "));
        assertEquals(2, NoteTextUtils.countChars(" a b "));
        assertEquals(4, NoteTextUtils.countChars("你好世界"));
    }

    @Test
    public void countWords_asciiAndCjkMixed() {
        assertEquals(0, NoteTextUtils.countWords(null));
        assertEquals(2, NoteTextUtils.countWords("hello world"));
        // 中文逐字计词，连续英文合并为一个词
        assertEquals(5, NoteTextUtils.countWords("你好abc世界"));
    }

    @Test
    public void countLines_nonEmptyOnly() {
        assertEquals(0, NoteTextUtils.countLines(null));
        assertEquals(1, NoteTextUtils.countLines("单行文本"));
        assertEquals(3, NoteTextUtils.countLines("第一行\n第二行\n第三行"));
        assertEquals(2, NoteTextUtils.countLines("第一行\n\n第三行\n"));
    }

    @Test
    public void stripCheckTag_removesPrefixSymbol() {
        assertEquals("买菜", NoteTextUtils.stripCheckTag("√ 买菜"));
        assertEquals("开会", NoteTextUtils.stripCheckTag("□开会"));
        assertEquals("普通文本", NoteTextUtils.stripCheckTag("普通文本"));
        assertEquals("", NoteTextUtils.stripCheckTag(null));
    }

    @Test
    public void truncate_withinLimit_keptWhole() {
        assertEquals("abc", NoteTextUtils.truncate("abc", 10));
        assertEquals("", NoteTextUtils.truncate(null, 5));
        assertEquals("", NoteTextUtils.truncate("abc", 0));
    }

    @Test
    public void truncate_overLimit_appendsEllipsis() {
        String result = NoteTextUtils.truncate("abcdefgh", 3);
        assertEquals("abc…", result);
    }

    @Test
    public void checkListProgress_countsCheckedAndTotal() {
        String text = "√ 已完成事项\n□ 待办事项\n√另一项已完成";
        int[] progress = NoteTextUtils.checkListProgress(text);
        assertEquals(2, progress[0]);
        assertEquals(3, progress[1]);

        int[] empty = NoteTextUtils.checkListProgress(null);
        assertEquals(0, empty[0]);
        assertEquals(0, empty[1]);
    }
}
