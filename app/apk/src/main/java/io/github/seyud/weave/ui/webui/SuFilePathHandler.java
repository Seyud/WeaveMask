package io.github.seyud.weave.ui.webui;

import android.content.Context;
import android.webkit.WebResourceResponse;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.webkit.WebViewAssetLoader;

import com.topjohnwu.superuser.nio.ExtendedFile;
import com.topjohnwu.superuser.nio.FileSystemManager;

import io.github.seyud.weave.core.Config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import timber.log.Timber;

public final class SuFilePathHandler implements WebViewAssetLoader.PathHandler {

    public static final String DEFAULT_MIME_TYPE = "text/plain";

    @NonNull
    private final File mDirectory;
    private final FileSystemManager mFs;
    private final InsetsSupplier mInsetsSupplier;
    private final OnInsetsRequestedListener mOnInsetsRequestedListener;

    public SuFilePathHandler(
            @NonNull Context context,
            @NonNull File directory,
            @NonNull FileSystemManager fs,
            @NonNull InsetsSupplier insetsSupplier,
            OnInsetsRequestedListener onInsetsRequestedListener
    ) {
        try {
            mInsetsSupplier = insetsSupplier;
            mOnInsetsRequestedListener = onInsetsRequestedListener;
            mDirectory = new File(getCanonicalDirPath(directory));
            mFs = fs;
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Failed to resolve the canonical path for the given directory: "
                            + directory.getPath(), e);
        }
    }

    public static String getCanonicalDirPath(@NonNull File file) throws IOException {
        String canonicalPath = file.getCanonicalPath();
        if (!canonicalPath.endsWith("/")) canonicalPath += "/";
        return canonicalPath;
    }

    public static File getCanonicalFileIfChild(@NonNull File parent, @NonNull String child)
            throws IOException {
        String parentCanonicalPath = getCanonicalDirPath(parent);
        String childCanonicalPath = new File(parent, child).getCanonicalPath();
        if (childCanonicalPath.startsWith(parentCanonicalPath)) {
            return new File(childCanonicalPath);
        }
        return null;
    }

    @NonNull
    private static InputStream handleSvgzStream(@NonNull String path,
                                                @NonNull InputStream stream) throws IOException {
        return path.endsWith(".svgz") ? new GZIPInputStream(stream) : stream;
    }

    public static InputStream openFile(@NonNull File file, @NonNull FileSystemManager fs) throws IOException {
        ExtendedFile extFile = fs.getFile(file.getAbsolutePath());
        InputStream fis = extFile.newInputStream();
        return handleSvgzStream(file.getPath(), fis);
    }

    @NonNull
    public static String guessMimeType(@NonNull String filePath) {
        String mimeType = MimeUtil.getMimeFromFileName(filePath);
        return mimeType == null ? DEFAULT_MIME_TYPE : mimeType;
    }

    @Override
    @WorkerThread
    @NonNull
    public WebResourceResponse handle(@NonNull String path) {
        if ("internal/insets.css".equals(path)) {
            if (mOnInsetsRequestedListener != null) {
                mOnInsetsRequestedListener.onInsetsRequested(true);
            }
            String css = mInsetsSupplier.get().getCss();
            return new WebResourceResponse(
                    "text/css",
                    "utf-8",
                    new ByteArrayInputStream(css.getBytes(StandardCharsets.UTF_8))
            );
        }
        if ("internal/colors.css".equals(path)) {
            int colorMode = Config.INSTANCE.getColorMode();
            String css = "";
            if (colorMode >= 3 && colorMode <= 5) {
                css = MonetColorsProvider.INSTANCE.getColorsCss();
            }
            return new WebResourceResponse(
                    "text/css",
                    "utf-8",
                    new ByteArrayInputStream(css.getBytes(StandardCharsets.UTF_8))
            );
        }
        try {
            File file = getCanonicalFileIfChild(mDirectory, path);
            if (file != null) {
                InputStream is_ = openFile(file, mFs);
                String mimeType = guessMimeType(path);
                return new WebResourceResponse(mimeType, null, is_);
            } else {
                Timber.e("The requested file: %s is outside the mounted directory: %s", path,
                        mDirectory);
            }
        } catch (IOException e) {
            Timber.e(e, "Error opening the requested path: %s", path);
        }
        return new WebResourceResponse(null, null, null);
    }

    public interface InsetsSupplier {
        @NonNull
        Insets get();
    }

    public interface OnInsetsRequestedListener {
        void onInsetsRequested(boolean enable);
    }
}
