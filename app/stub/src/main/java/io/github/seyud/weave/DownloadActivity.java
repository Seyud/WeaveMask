package io.github.seyud.weave;

import static android.R.string.cancel;
import static android.R.string.ok;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.loader.ResourcesLoader;
import android.content.res.loader.ResourcesProvider;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;
import android.widget.ProgressBar;

import io.github.seyud.weave.net.Networking;
import io.github.seyud.weave.net.Request;
import io.github.seyud.weave.utils.APKInstall;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class DownloadActivity extends Activity {

    private static final String APP_NAME = "WeaveMask";
    private static final String RES_PKG_NAME = "io.github.seyud.weave";
    private static final ExecutorService DOWNLOAD_EXECUTOR = Executors.newCachedThreadPool();

    private boolean dynLoad;
    private int dling;
    private int no_internet_msg;
    private int upgrade_msg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Only download and dynamic load full APK if hidden
        dynLoad = !getPackageName().equals(BuildConfig.APPLICATION_ID);

        // Inject resources
        try {
            loadResources();
        } catch (Exception e) {
            error(e);
        }

        ProviderInstaller.install(this);

        if (Networking.checkNetworkStatus(this)) {
            showDialog();
        } else {
            new AlertDialog.Builder(this)
                    .setCancelable(false)
                    .setTitle(APP_NAME)
                    .setMessage(getString(no_internet_msg))
                    .setNegativeButton(ok, (d, w) -> finish())
                    .show();
        }
    }

    @Override
    public void finish() {
        super.finish();
        Runtime.getRuntime().exit(0);
    }

    private void error(Throwable e) {
        Log.e(getClass().getSimpleName(), Log.getStackTraceString(e));
        finish();
    }

    private Request request(String url) {
        return Networking.get(url).setErrorHandler((conn, e) -> error(e));
    }

    private void showDialog() {
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle(APP_NAME)
                .setMessage(getString(upgrade_msg))
                .setPositiveButton(ok, (d, w) -> dlAPK())
                .setNegativeButton(cancel, (d, w) -> finish())
                .show();
    }

    private void dlAPK() {
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle(getString(dling))
                .setMessage(getString(dling) + " " + APP_NAME)
                .setView(new ProgressBar(this))
                .show();
        // Download and upgrade the app
        var request = request(BuildConfig.APK_URL).setExecutor(DOWNLOAD_EXECUTOR);
        if (dynLoad) {
            request.getAsFile(StubApk.current(this), file -> StubApk.restartProcess(this));
        } else {
            request.getAsInputStream(input -> {
                var session = APKInstall.startSession(this);
                try (input; var out = session.openStream(this)) {
                    if (out != null)
                        APKInstall.transfer(input, out);
                } catch (IOException e) {
                    error(e);
                }
                Intent intent = session.waitIntent();
                if (intent != null)
                    startActivity(intent);
            });
        }
    }

    private void decryptResources(OutputStream out) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKey key = new SecretKeySpec(Bytes.key(), "AES");
        IvParameterSpec iv = new IvParameterSpec(Bytes.iv());
        cipher.init(Cipher.DECRYPT_MODE, key, iv);
        var is = new InflaterInputStream(new CipherInputStream(
                new ByteArrayInputStream(Bytes.res()), cipher));
        try (is; out) {
            APKInstall.transfer(is, out);
        }
    }

    private void loadResources() throws Exception {
        var res = getResources();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            var fd = Os.memfd_create("res", 0);
            try {
                decryptResources(new FileOutputStream(fd));
                Os.lseek(fd, 0, OsConstants.SEEK_SET);
                var loader = new ResourcesLoader();
                try (var pfd = ParcelFileDescriptor.dup(fd)) {
                    loader.addProvider(ResourcesProvider.loadFromTable(pfd, null));
                    res.addLoaders(loader);
                }
            } finally {
                Os.close(fd);
            }
        } else {
            File apk = new File(getCodeCacheDir(), "res.apk");
            try (var out = new ZipOutputStream(new FileOutputStream(apk))) {
                // AndroidManifest.xml is required on Android 6-, and directory support is broken on Android 9-10
                out.putNextEntry(new ZipEntry("AndroidManifest.xml"));
                try (var stubApk = new ZipFile(getPackageCodePath())) {
                    APKInstall.transfer(stubApk.getInputStream(stubApk.getEntry("AndroidManifest.xml")), out);
                }
                out.putNextEntry(new ZipEntry("resources.arsc"));
                decryptResources(out);
            }
            StubApk.addAssetPath(res, apk.getPath());
        }
        dling = res.getIdentifier("dling", "string", RES_PKG_NAME);
        no_internet_msg = res.getIdentifier("no_internet_msg", "string", RES_PKG_NAME);
        upgrade_msg = res.getIdentifier("upgrade_msg", "string", RES_PKG_NAME);
    }
}
