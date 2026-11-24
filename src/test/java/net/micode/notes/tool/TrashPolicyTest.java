package net.micode.notes.tool;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 本次维护新增回收站保留期策略 {@link TrashPolicy} 的本地单元测试。
 * 覆盖删除时间有效性、到期时间计算、过期判定边界与剩余天数换算等纯逻辑方法。
 */
public class TrashPolicyTest {
    // 测试基准时间：第 40 天，便于构造保留期边界
    private static final long NOW = 40L * TrashPolicy.MILLIS_PER_DAY;

    @Test
    public void hasDeletedDate_onlyPositiveIsValid() {
        assertFalse(TrashPolicy.hasDeletedDate(0));
        assertFalse(TrashPolicy.hasDeletedDate(-1));
        assertTrue(TrashPolicy.hasDeletedDate(1));
    }

    @Test
    public void expireDate_addsRetentionDays() {
        long deletedDate = 10L * TrashPolicy.MILLIS_PER_DAY;
        assertEquals(40L * TrashPolicy.MILLIS_PER_DAY,
                TrashPolicy.expireDate(deletedDate, 30));
        // 删除时间非法时返回 0，避免把未删除记录当成已过期
        assertEquals(0, TrashPolicy.expireDate(0, 30));
    }

    @Test
    public void isExpired_trueOnlyAtOrAfterDeadline() {
        long deletedDate = 10L * TrashPolicy.MILLIS_PER_DAY;
        long deadline = TrashPolicy.expireDate(deletedDate, 30);
        assertFalse(TrashPolicy.isExpired(deletedDate, deadline - 1, 30));
        assertTrue(TrashPolicy.isExpired(deletedDate, deadline, 30));
        assertTrue(TrashPolicy.isExpired(deletedDate, deadline + 1, 30));
        // 没有删除时间的记录永不判定为过期
        assertFalse(TrashPolicy.isExpired(0, NOW, 30));
    }

    @Test
    public void remainingDays_roundsUpPartialDay() {
        long deletedDate = 10L * TrashPolicy.MILLIS_PER_DAY;
        // 刚删除：剩余完整 30 天
        assertEquals(30, TrashPolicy.remainingDays(deletedDate, deletedDate, 30));
        // 已过 29 天零 1 毫秒：不足一天按一天计
        long almostExpired = deletedDate + 29L * TrashPolicy.MILLIS_PER_DAY + 1;
        assertEquals(1, TrashPolicy.remainingDays(deletedDate, almostExpired, 30));
    }

    @Test
    public void remainingDays_expiredReturnsZero() {
        long deletedDate = 10L * TrashPolicy.MILLIS_PER_DAY;
        long afterDeadline = TrashPolicy.expireDate(deletedDate, 30);
        assertEquals(0, TrashPolicy.remainingDays(deletedDate, afterDeadline, 30));
        assertEquals(0, TrashPolicy.remainingDays(deletedDate, afterDeadline + 5, 30));
    }

    @Test
    public void remainingDays_withoutDeletedDate_returnsFullPeriod() {
        // 未标记删除时间的记录按完整保留期处理，避免界面显示 0 天造成误解
        assertEquals(30, TrashPolicy.remainingDays(0, NOW, 30));
    }
}
