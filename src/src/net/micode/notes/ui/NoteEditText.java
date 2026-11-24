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

public class NoteEditText extends EditText {
    private static final String TAG = "NoteEditText";
    private int mIndex;
    private int mSelectionStartBeforeDelete;
    private boolean mMarkdownEnabled = false;

    // Markdown regex patterns
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)");
    private static final Pattern STRIKETHROUGH_PATTERN = Pattern.compile("~~(.+?)~~");
    private static final Pattern HIGHLIGHT_PATTERN = Pattern.compile("==(.+?)==");

    private static final String SCHEME_TEL = "tel:";
    private static final String SCHEME_HTTP = "http:";
    private static final String SCHEME_EMAIL = "mailto:";

    private static final Map<String, Integer> sSchemaActionResMap = new HashMap<>();
    static {
        sSchemaActionResMap.put(SCHEME_TEL, R.string.note_link_tel);
        sSchemaActionResMap.put(SCHEME_HTTP, R.string.note_link_web);
        sSchemaActionResMap.put(SCHEME_EMAIL, R.string.note_link_email);
    }

    public interface OnTextViewChangeListener {
        void onEditTextDelete(int index, String text);
        void onEditTextEnter(int index, String text);
        void onTextChange(int index, boolean hasText);
    }

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

    public NoteEditText(Context context) {
        super(context, null);
        mIndex = 0;
        initMarkdown();
    }

    public NoteEditText(Context context, AttributeSet attrs) {
        super(context, attrs, android.R.attr.editTextStyle);
        initMarkdown();
    }

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