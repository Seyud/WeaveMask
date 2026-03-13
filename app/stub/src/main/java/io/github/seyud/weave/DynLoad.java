package io.github.seyud.weave;

import static io.github.seyud.weave.BuildConfig.APPLICATION_ID;

import android.app.AppComponentFactory;
import android.app.Application;
import android.app.job.JobService;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.util.Log;

import io.github.seyud.weave.utils.APKInstall;
import io.github.seyud.weave.utils.DynamicClassLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class DynLoad {

    static Object componentFactory;
    static ClassLoader activeClassLoader = DynLoad.class.getClassLoader();

    static StubApk.Data createApkData() {
        var data = new StubApk.Data();
        data.setVersion(BuildConfig.STUB_VERSION);
        data.setClassToComponent(new HashMap<>());
        data.setRootService(StubRootService.class);
        return data;
    }

    static void attachContext(Object o, Context context) {
        if (!(o instanceof ContextWrapper))
            return;
        try {
            Method m = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
            m.setAccessible(true);
            m.invoke(o, context);
        } catch (Exception ignored) { /* Impossible */ }
    }

    // Dynamically load APK from internal, external storage, or previous app
    static DynamicClassLoader loadApk(Context context) {
        File apk = StubApk.current(context);
        File update = StubApk.update(context);

        if (update.exists()) {
            // Rename from update
            update.renameTo(apk);
        }

        // Copy from external for easier development
        if (BuildConfig.DEBUG) {
            try {
                File external = new File(context.getExternalFilesDir(null), "magisk.apk");
                if (external.exists()) {
                    apk.delete();
                    try {
                        var in = new FileInputStream(external);
                        var out = new FileOutputStream(apk);
                        apk.setReadOnly();
                        try (in; out) {
                            APKInstall.transfer(in, out);
                        }
                    } catch (IOException e) {
                        Log.e(DynLoad.class.getSimpleName(), "", e);
                        apk.delete();
                    } finally {
                        external.delete();
                    }
                }
            } catch (SecurityException e) {
                // Do not crash in root service
            }
        }

        if (apk.exists()) {
            apk.setReadOnly();
            return new DynamicClassLoader(apk);
        }

        // If no APK is loaded, attempt to copy from previous app
        if (!context.getPackageName().equals(APPLICATION_ID)) {
            try {
                var info = context.getPackageManager().getApplicationInfo(APPLICATION_ID, 0);
                apk.delete();
                var src = new FileInputStream(info.sourceDir);
                var out = new FileOutputStream(apk);
                apk.setReadOnly();
                try (src; out) {
                    APKInstall.transfer(src, out);
                }
                return new DynamicClassLoader(apk);
            } catch (PackageManager.NameNotFoundException ignored) {
            } catch (IOException e) {
                Log.e(DynLoad.class.getSimpleName(), "", e);
                apk.delete();
            }
        }

        return null;
    }

    // Dynamically load APK and initialize the application
    static void loadAndInitializeApp(Application context) {
        // On API >= 29, AppComponentFactory will replace the ClassLoader for us
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            replaceClassLoader(context);

        // noinspection InlinedApi
        int flags = PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES
                | PackageManager.GET_PROVIDERS | PackageManager.GET_RECEIVERS
                | PackageManager.MATCH_DIRECT_BOOT_AWARE | PackageManager.MATCH_DISABLED_COMPONENTS
                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
        var pm = context.getPackageManager();

        final PackageInfo stubInfo;
        try {
            // noinspection WrongConstant
            stubInfo = pm.getPackageInfo(context.getPackageName(), flags);
        } catch (PackageManager.NameNotFoundException e) {
            // Impossible
            throw new RuntimeException(e);
        }

        File apk = StubApk.current(context);

        final var cl = loadApk(context);
        if (cl != null) try {
            // noinspection WrongConstant
            var apkInfo = pm.getPackageArchiveInfo(apk.getPath(), flags);
            var mapping = generateMapping(stubInfo, apkInfo);

            var data = createApkData();
            var map = data.getClassToComponent();
            // Create the inverse mapping (class to component name)
            for (var e : mapping.entrySet()) {
                map.put(e.getValue(), e.getKey());
            }

            var appInfo = apkInfo.applicationInfo;
            // Create the receiver Application with proper constructor
            var app = cl.loadClass(appInfo.className)
                    .getConstructor(Object.class)
                    .newInstance(data.getObject());

            // Create the receiver component factory
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && componentFactory != null) {
                var delegate = (DelegateComponentFactory) componentFactory;
                if (appInfo.appComponentFactory == null) {
                    delegate.receiver = new AppComponentFactory();
                } else {
                    Object factory = cl.loadClass(appInfo.appComponentFactory).newInstance();
                    delegate.receiver = (AppComponentFactory) factory;
                }
            }

            activeClassLoader = new MappingClassLoader(cl, mapping);

            // Call Application.attachBaseContext
            attachContext(app, context);
        } catch (Exception e) {
            Log.e(DynLoad.class.getSimpleName(), "", e);
            apk.delete();
        } else {
            // Dynamic loading failed, use normal stub classloader
            activeClassLoader = new StubClassLoader(stubInfo);
        }
    }

    // Replace LoadedApk mClassLoader
    private static void replaceClassLoader(Context context) {
        // Get ContextImpl
        while (context instanceof ContextWrapper) {
            context = ((ContextWrapper) context).getBaseContext();
        }

        try {
            Field mInfo = context.getClass().getDeclaredField("mPackageInfo");
            mInfo.setAccessible(true);
            Object loadedApk = mInfo.get(context);
            assert loadedApk != null;
            Field mcl = loadedApk.getClass().getDeclaredField("mClassLoader");
            mcl.setAccessible(true);
            mcl.set(loadedApk, new DelegateClassLoader());
        } catch (Exception e) {
            // Actually impossible as this method is only called on API < 29,
            // and API 23 - 28 do not restrict access to these fields.
            Log.e(DynLoad.class.getSimpleName(), "", e);
        }
    }

    private static Map<String, String> generateMapping(PackageInfo stub, PackageInfo app) {
        var mapping = new HashMap<String, String>();
        {
            var stubList = new ArrayList<>(Arrays.asList(stub.activities));
            var appList = new ArrayList<>(Arrays.asList(app.activities));

            var stubGroups = groupActivities(stubList);
            var appGroups = groupActivities(appList);

            for (var entry : appGroups.entrySet()) {
                var key = entry.getKey();
                var appGroup = entry.getValue();
                var stubGroup = stubGroups.get(key);
                if (stubGroup == null || stubGroup.isEmpty())
                    continue;
                appGroup.sort(Comparator.comparing(a -> a.name));
                stubGroup.sort(Comparator.comparing(a -> a.name));
                int n = Math.min(stubGroup.size(), appGroup.size());
                for (int i = 0; i < n; i++) {
                    mapping.put(stubGroup.get(i).name, appGroup.get(i).name);
                }
                if (n > 0) {
                    stubGroup.subList(0, n).clear();
                    appGroup.subList(0, n).clear();
                }
            }

            var stubRemaining = new ArrayList<ActivityInfo>();
            for (var list : stubGroups.values()) {
                stubRemaining.addAll(list);
            }
            var appRemaining = new ArrayList<ActivityInfo>();
            for (var list : appGroups.values()) {
                appRemaining.addAll(list);
            }
            stubRemaining.sort(Comparator.comparing(a -> a.name));
            appRemaining.sort(Comparator.comparing(a -> a.name));
            int n = Math.min(stubRemaining.size(), appRemaining.size());
            for (int i = 0; i < n; i++) {
                mapping.put(stubRemaining.get(i).name, appRemaining.get(i).name);
            }
        }

        {
            var src = stub.services;
            var dest = app.services;

            final ServiceInfo sa;
            final ServiceInfo da;
            final ServiceInfo sb;
            final ServiceInfo db;
            if (JobService.PERMISSION_BIND.equals(src[0].permission)) {
                sa = src[0];
                sb = src[1];
            } else {
                sa = src[1];
                sb = src[0];
            }
            if (JobService.PERMISSION_BIND.equals(dest[0].permission)) {
                da = dest[0];
                db = dest[1];
            } else {
                da = dest[1];
                db = dest[0];
            }
            mapping.put(sa.name, da.name);
            mapping.put(sb.name, db.name);
        }

        {
            var src = stub.receivers;
            var dest = app.receivers;
            mapping.put(src[0].name, dest[0].name);
        }

        {
            var src = stub.providers;
            var dest = app.providers;
            mapping.put(src[0].name, dest[0].name);
        }
        return mapping;
    }

    private static Map<String, List<ActivityInfo>> groupActivities(List<ActivityInfo> activities) {
        var groups = new HashMap<String, List<ActivityInfo>>();
        for (var info : activities) {
            var key = activityKey(info);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(info);
        }
        return groups;
    }

    private static String activityKey(ActivityInfo info) {
        return info.exported + ":" + info.directBootAware;
    }
}
