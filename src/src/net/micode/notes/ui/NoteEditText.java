/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.ui;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.widget.EditText;

import net.micode.notes.R;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 清单模式（CheckList）专用的自定义便签文本编辑框控件，继承自 Android EditText。
 * 扩展支持触摸坐标精确定位光标、拦截回车键（ENTER）自动拆分新建下一行清单项、
 * 在行首按退格键（DEL）删除合并当前清单项，以及长按识别电话/网址/邮箱/地图超链接弹出快捷操作菜单。
 */
public class NoteEditText extends EditText {
    private static final String TAG = "NoteEditText";
    // 当前编辑框在清单模式线性容器中的行号索引（从 0 开始）
    private int mIndex;
    // 按下退格删除键前记录的光标起始位置（为 0 说明位于行首）
    private int mSelectionStartBeforeDelete;
    private boolean mMarkdownEnabled = false;

    // Markdown regex patterns
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)");
    private static final Pattern STRIKETHROUGH_PATTERN = Pattern.compile("~~(.+?)~~");
    private static final Pattern HIGHLIGHT_PATTERN = Pattern.compile("==(.+?)==");

    // 支持识别的超链接协议前缀常量（电话 tel:、网页 http:、邮件 mailto:）
    private static final String SCHEME_TEL = "tel:" ;
    private static final String SCHEME_HTTP = "http:" ;
    private static final String SCHEME_EMAIL = "mailto:" ;

    private static final Map<String, Integer> sSchemaActionResMap = new HashMap<>();
    static {
        sSchemaActionResMap.put(SCHEME_TEL, R.string.note_link_tel);
        sSchemaActionResMap.put(SCHEME_HTTP, R.string.note_link_web);
        sSchemaActionResMap.put(SCHEME_EMAIL, R.string.note_link_email);
    }

    /**
     * Call by the {@link NoteEditActivity} to delete or add edit text
     */
    /**
     * 清单行文本编辑事件回调接口，通知 NoteEditActivity 动态增删清单行或显隐复选框。
     */
    public interface OnTextViewChangeListener {
        void onEditTextDelete(int index, String text);
        void onEditTextEnter(int index, String text);
        void onTextChange(int index, boolean hasText);
    }

    // 清单文本变化事件回调监听器
    private OnTextViewChangeListener mOnTextViewChangeListener;

    /**
     * TextWatcher that applies Markdown styles as the user types.
     * Only runs when Markdown mode is enabled.
     */
    private final TextWatcher mMarkdownTextWatcher = new TextWatcher() {
        private boolean mIsUpdating = false;

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (mIsUpdating) return;
            if (!mMarkdownEnabled) return;
            if (TextUtils.isEmpty(s)) return;

            mIsUpdating = true;

            // Use post() to defer span application, avoid text flashing during paste
            post(() -> {
                if (!mMarkdownEnabled) {
                    mIsUpdating = false;
                    return;
                }

                // Remove existing markdown spans
                removeMarkdownSpans(s);

                // Apply new markdown spans
                applySpans(s);

                mIsUpdating = false;
            });
        }
    };

    // 代码动态创建时使用的单参构造方法，默认行索引为 0
    public NoteEditText(Context context) {
        super(context, null);
        mIndex = 0;
    }

    // 更新当前编辑框在清单列表中的行号索引
    public void setIndex(int index) {
        mIndex = index;
    }

    // 注册文本行增删与内容变化监听器
    public void setOnTextViewChangeListener(OnTextViewChangeListener listener) {
        mOnTextViewChangeListener = listener;
        initMarkdown();
    }

    // XML 布局解析时调用的双参构造方法
    public NoteEditText(Context context, AttributeSet attrs) {
        super(context, attrs, android.R.attr.editTextStyle);
        initMarkdown();
    }

    // 带默认样式属性的三参构造方法
    public NoteEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initMarkdown();
    }

    private void initMarkdown() {
        addTextChangedListener(mMarkdownTextWatcher);
    }

    public void setIndex(int index) {
        mIndex = index;
    }

    public void setMarkdownEnabled(boolean enabled) {
        if (mMarkdownEnabled == enabled) return;
        mMarkdownEnabled = enabled;
        if (mMarkdownEnabled) {
            // Apply styles to current text
            applySpans(getText());
        } else {
            // Remove all markdown spans
            removeMarkdownSpans(getText());
        }
    }

    public boolean isMarkdownEnabled() {
        return mMarkdownEnabled;
    }

    /**
     * Remove all markdown-related spans from the Editable text.
     */
    private void removeMarkdownSpans(Editable text) {
        // Remove StyleSpan (bold/italic)
        StyleSpan[] styleSpans = text.getSpans(0, text.length(), StyleSpan.class);
        for (StyleSpan span : styleSpans) {
            if (span.getStyle() == Typeface.BOLD || span.getStyle() == Typeface.ITALIC) {
                text.removeSpan(span);
            }
        }
        // Remove StrikethroughSpan
        StrikethroughSpan[] strikeSpans = text.getSpans(0, text.length(), StrikethroughSpan.class);
        for (StrikethroughSpan span : strikeSpans) {
            text.removeSpan(span);
        }
        // Remove BackgroundColorSpan (highlight)
        BackgroundColorSpan[] bgSpans = text.getSpans(0, text.length(), BackgroundColorSpan.class);
        for (BackgroundColorSpan span : bgSpans) {
            text.removeSpan(span);
        }
        // Remove ForegroundColorSpan (transparent markers)
        ForegroundColorSpan[] fgSpans = text.getSpans(0, text.length(), ForegroundColorSpan.class);
        for (ForegroundColorSpan span : fgSpans) {
            text.removeSpan(span);
        }
    }

    /**
     * Apply Markdown spans to the Editable text.
     * Modifies spans in-place without replacing text content.
     */
    private void applySpans(Editable text) {
        String fullText = text.toString();

        // Bold: **text**
        Matcher m = BOLD_PATTERN.matcher(fullText);
        while (m.find()) {
            int start = m.start();
            int end = m.end();
            int cs = start + 2;
            int ce = end - 2;
            if (cs < ce) {
                text.setSpan(new StyleSpan(Typeface.BOLD), cs, ce, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new ForegroundColorSpan(0x00000000), start, cs, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new ForegroundColorSpan(0x00000000), ce, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        // Italic: *text* (not inside **)
        m = ITALIC_PATTERN.matcher(fullText);
        while (m.find()) {
            int start = m.start();
            int end = m.end();
            int cs = start + 1;
            int ce = end - 1;
            if (cs < ce) {
                text.setSpan(new StyleSpan(Typeface.ITALIC), cs, ce, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new ForegroundColorSpan(0x00000000), start, cs, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new ForegroundColorSpan(0x00000000), ce, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        // Strikethrough: ~~text~~
        m = STRIKETHROUGH_PATTERN.matcher(fullText);
        while (m.find()) {
            int start = m.start();
            int end = m.end();
            int cs = start + 2;
            int ce = end - 2;
            if (cs < ce) {
                text.setSpan(new StrikethroughSpan(), cs, ce, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new ForegroundColorSpan(0x00000000), start, cs, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new ForegroundColorSpan(0x00000000), ce, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        // Highlight: ==text==
        m = HIGHLIGHT_PATTERN.matcher(fullText);
        while (m.find()) {
            int start = m.start();
            int end = m.end();
            int cs = start + 2;
            int ce = end - 2;
            if (cs < ce) {
                text.setSpan(new BackgroundColorSpan(0xFFFFFF00), cs, ce, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new ForegroundColorSpan(0x00000000), start, cs, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new ForegroundColorSpan(0x00000000), ce, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    public void setOnTextViewChangeListener(OnTextViewChangeListener listener) {
        mOnTextViewChangeListener = listener;
    }

    @Override
    /**
     * 重写触摸事件：在手指按下（ACTION_DOWN）时根据触摸坐标换算文本行与字符偏移量，精准定位输入光标。
     */
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                int x = (int) event.getX();
                int y = (int) event.getY();
                x -= getTotalPaddingLeft();
                y -= getTotalPaddingTop();
                x += getScrollX();
                y += getScrollY();
                Layout layout = getLayout();
                int line = layout.getLineForVertical(y);
                int off = layout.getOffsetForHorizontal(line, x);
                Selection.setSelection(getText(), off);
                break;
        }
        return super.onTouchEvent(event);
    }

    @Override
    /**
     * 重写按键按下事件：拦截回车键交由 onKeyUp 拆分清单项，并在按下删除键时记录删除前的光标位置。
     */
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                if (mOnTextViewChangeListener != null) {
                    return false;
                }
                break;
            case KeyEvent.KEYCODE_DEL:
                mSelectionStartBeforeDelete = getSelectionStart();
                break;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    /**
     * 重写按键抬起事件：
     * - 非首行且光标在行首按 DEL 时，回调 onEditTextDelete 删除当前行并将剩余文字合并到上一行；
     * - 按 ENTER 时，截断光标后半段文本并回调 onEditTextEnter 在下方插入新清单行。
     */
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DEL:
                if (mOnTextViewChangeListener != null) {
                    if (0 == mSelectionStartBeforeDelete && mIndex != 0) {
                        mOnTextViewChangeListener.onEditTextDelete(mIndex, getText().toString());
                        return true;
                    }
                }
                break;
            case KeyEvent.KEYCODE_ENTER:
                if (mOnTextViewChangeListener != null) {
                    int selectionStart = getSelectionStart();
                    String text = getText().subSequence(selectionStart, length()).toString();
                    setText(getText().subSequence(0, selectionStart));
                    mOnTextViewChangeListener.onEditTextEnter(mIndex + 1, text);
                }
                break;
            default:
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    /**
     * 焦点变化回调：失去焦点且文本为空时通知隐藏左侧复选框，获得焦点或有内容时显示复选框。
     */
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        if (mOnTextViewChangeListener != null) {
            if (!focused && TextUtils.isEmpty(getText())) {
                mOnTextViewChangeListener.onTextChange(mIndex, false);
            } else {
                mOnTextViewChangeListener.onTextChange(mIndex, true);
            }
        }
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    @Override
    /**
     * 长按创建上下文菜单回调：检测当前光标处是否包含 URLSpan 超链接，
     * 若包含则根据协议类型添加“呼叫电话 / 浏览网页 / 发送邮件 / 打开地图”快捷跳转菜单项。
     */
    protected void onCreateContextMenu(ContextMenu menu) {
        if (getText() instanceof Spanned) {
            int selStart = getSelectionStart();
            int selEnd = getSelectionEnd();
            int min = Math.min(selStart, selEnd);
            int max = Math.max(selStart, selEnd);
            final URLSpan[] urls = ((Spanned) getText()).getSpans(min, max, URLSpan.class);
            if (urls.length == 1) {
                int defaultResId = 0;
                for (String schema : sSchemaActionResMap.keySet()) {
                    if (urls[0].getURL().indexOf(schema) >= 0) {
                        defaultResId = sSchemaActionResMap.get(schema);
                        break;
                    }
                }
                if (defaultResId == 0) {
                    defaultResId = R.string.note_link_other;
                }
                menu.add(0, 0, 0, defaultResId).setOnMenuItemClickListener(
                        new OnMenuItemClickListener() {
                            public boolean onMenuItemClick(MenuItem item) {
                                urls[0].onClick(NoteEditText.this);
                                return true;
                            }
                        });
            }
        }
        super.onCreateContextMenu(menu);
    }
}