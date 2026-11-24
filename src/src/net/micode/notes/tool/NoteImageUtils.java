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

package net.micode.notes.tool;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 本次维护新增：便签正文图片附件的存储与解析工具。
 *
 * 设计要点：图片文件统一保存在应用私有目录 {@code files/note_images/}，正文里只保留
 * {@code [img:文件名]} 这样的纯文本标记（例如 {@code [img:img_8f12ab.jpg]}）。
 * 好处是数据库仍然只存一列纯文本，同步、导出、字数统计、搜索等既有逻辑全部不受影响；
 * 编辑页渲染时再把标记替换成真实图片（ImageSpan），列表摘要处则替换成“[图片]”占位。
 */
public final class NoteImageUtils {

    private static final String TAG = "NoteImageUtils";

    /** 图片存放目录（相对 files/） */
    public static final String DIR_NAME = "note_images";

    /** 相机原始输出的临时目录（相对 cache/），拍照完成后压缩并复制到 DIR_NAME */
    public static final String CAMERA_TEMP_DIR = "camera";

    /** 入库图片长边上限，避免原图过大占用空间 */
    private static final int MAX_SAVE_EDGE = 1600;

    /** JPEG 压缩质量 */
    private static final int JPEG_QUALITY = 85;

    /** 正文中的图片标记，形如 [img:img_8f12ab.jpg] */
    private static final Pattern MARKER_PATTERN =
            Pattern.compile("\\[img:([A-Za-z0-9_\\-]+(\\.jpg|\\.png))\\]");

    /** 列表摘要中图片的占位文案 */
    public static final String PLACEHOLDER = "[图片]";

    private NoteImageUtils() {
    }

    /**
     * 获取图片存储目录，目录不存在时自动创建。
     */
    public static File getImageDir(Context context) {
        File dir = new File(context.getFilesDir(), DIR_NAME);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * 按文件名取得图片文件对象（不保证文件存在）。
     */
    public static File getImageFile(Context context, String fileName) {
        return new File(getImageDir(context), fileName);
    }

    /**
     * 获取拍照临时目录，目录不存在时自动创建。
     */
    public static File getCameraTempDir(Context context) {
        File dir = new File(context.getCacheDir(), CAMERA_TEMP_DIR);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * 生成正文中使用的图片标记。
     */
    public static String buildMarker(String fileName) {
        return "[img:" + fileName + "]";
    }

    /**
     * 去掉正文中的图片标记，供列表摘要、字数统计等纯文本场景使用。
     */
    public static String stripMarkers(String text) {
        if (TextUtils.isEmpty(text)) {
            return text == null ? "" : text;
        }
        return MARKER_PATTERN.matcher(text).replaceAll(PLACEHOLDER);
    }

    /**
     * 把外部图片来源（相册、拍照输出等）压缩后保存到私有目录。
     *
     * @param context 上下文
     * @param uri     图片来源 Uri
     * @return 保存成功返回文件名；失败返回 null
     */
    public static String importFromUri(Context context, Uri uri) {
        if (uri == null) {
            return null;
        }
        File temp = new File(getCameraTempDir(context), "src_" + UUID.randomUUID() + ".tmp");
        try {
            copyToFile(context, uri, temp);
            return importFromFile(context, temp);
        } catch (IOException e) {
            Log.e(TAG, "Import image from uri failed", e);
            return null;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    /**
     * 把本地图片文件压缩后保存到私有目录，并按 EXIF 信息纠正相机照片的方向。
     *
     * @param context 上下文
     * @param source  源图片文件
     * @return 保存成功返回文件名；失败返回 null
     */
    public static String importFromFile(Context context, File source) {
        if (source == null || !source.exists()) {
            return null;
        }
        Bitmap bitmap = decodeSized(source, MAX_SAVE_EDGE, MAX_SAVE_EDGE);
        if (bitmap == null) {
            Log.e(TAG, "Decode image failed: " + source.getAbsolutePath());
            return null;
        }
        Bitmap rotated = applyExifRotation(source, bitmap);
        String fileName = "img_" + UUID.randomUUID().toString().replace("-", "") + ".jpg";
        File target = getImageFile(context, fileName);
        OutputStream out = null;
        try {
            out = new FileOutputStream(target);
            rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
            out.flush();
            return fileName;
        } catch (IOException e) {
            Log.e(TAG, "Save image failed", e);
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            return null;
        } finally {
            closeQuietly(out);
            if (rotated != bitmap) {
                rotated.recycle();
            }
            bitmap.recycle();
        }
    }

    /**
     * 按目标宽高等比解码图片，优先用 inSampleSize 降采样再精确缩放，避免大图撑爆内存。
     *
     * @return 解码成功返回位图；文件损坏或格式不支持返回 null
     */
    public static Bitmap decodeSized(File file, int maxWidth, int maxHeight) {
        if (file == null || !file.exists() || maxWidth <= 0 || maxHeight <= 0) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null;
        }
        int sample = 1;
        while (options.outWidth / (sample * 2) >= maxWidth
                || options.outHeight / (sample * 2) >= maxHeight) {
            sample *= 2;
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = sample;
        Bitmap decoded;
        try {
            decoded = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "Decode image out of memory", e);
            return null;
        }
        if (decoded == null) {
            return null;
        }
        int width = decoded.getWidth();
        int height = decoded.getHeight();
        float scale = Math.min(1f, Math.min((float) maxWidth / width, (float) maxHeight / height));
        if (scale >= 1f) {
            return decoded;
        }
        int targetWidth = Math.max(1, Math.round(width * scale));
        int targetHeight = Math.max(1, Math.round(height * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true);
        if (scaled != decoded) {
            decoded.recycle();
        }
        return scaled;
    }

    /**
     * 读取 EXIF 方向并按需旋转位图，保证相机竖拍照片在正文里方向正确。
     */
    private static Bitmap applyExifRotation(File file, Bitmap bitmap) {
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException e) {
            Log.w(TAG, "Read exif failed, use original orientation", e);
            return bitmap;
        }
        int degrees;
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                degrees = 90;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                degrees = 180;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                degrees = 270;
                break;
            default:
                return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                bitmap.getHeight(), matrix, true);
        return rotated == null ? bitmap : rotated;
    }

    /**
     * 把 Uri 内容复制到指定文件。
     */
    private static void copyToFile(Context context, Uri uri, File target) throws IOException {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = context.getContentResolver().openInputStream(uri);
            if (in == null) {
                throw new IOException("Open input stream failed: " + uri);
            }
            out = new FileOutputStream(target);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) != -1) {
                out.write(buffer, 0, length);
            }
            out.flush();
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
                // 忽略关闭异常
            }
        }
    }
}
