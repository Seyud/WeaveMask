package io.github.seyud.weave.ui.webui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Window
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import com.topjohnwu.superuser.internal.UiThreadHandler
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CompletableFuture

class WebViewInterface(private val state: WebUIState) {
    private val webView get() = state.webView
    private val modDir get() = state.modDir

    private fun newRootShell(): Shell {
        return Shell.Builder.create()
            .setFlags(Shell.FLAG_MOUNT_MASTER)
            .build()
    }

    private fun <T> withNewRootShell(block: Shell.() -> T): T {
        val shell = newRootShell()
        try {
            return block(shell)
        } finally {
            shell.close()
        }
    }

    @JavascriptInterface
    fun exec(cmd: String): String {
        return withNewRootShell { ShellUtils.fastCmd(this, cmd) }
    }

    @JavascriptInterface
    fun exec(cmd: String, callbackFunc: String) {
        exec(cmd, null, callbackFunc)
    }

    private fun processOptions(sb: StringBuilder, options: String?) {
        val opts = if (options == null) JSONObject() else JSONObject(options)

        val cwd = opts.optString("cwd")
        if (!TextUtils.isEmpty(cwd)) {
            sb.append("cd ${cwd};")
        }

        opts.optJSONObject("env")?.let { env ->
            env.keys().forEach { key ->
                sb.append("export ${key}=${env.getString(key)};")
            }
        }
    }

    private fun postJavascript(jsCode: String) {
        webView?.post {
            webView?.evaluateJavascript(jsCode, null)
        }
    }

    @JavascriptInterface
    fun exec(cmd: String, options: String?, callbackFunc: String) {
        val finalCommand = StringBuilder()
        processOptions(finalCommand, options)
        finalCommand.append(cmd)

        val result = withNewRootShell {
            newJob().add(finalCommand.toString()).to(ArrayList(), ArrayList()).exec()
        }
        val stdout = result.out.joinToString(separator = "\n")
        val stderr = result.err.joinToString(separator = "\n")

        val jsCode =
            "(function() { try { ${callbackFunc}(${result.code}, ${
                JSONObject.quote(stdout)
            }, ${JSONObject.quote(stderr)}); } catch(e) { console.error(e); } })();"
        postJavascript(jsCode)
    }

    @JavascriptInterface
    fun spawn(command: String, args: String, options: String?, callbackFunc: String) {
        val finalCommand = StringBuilder()
        processOptions(finalCommand, options)

        if (!TextUtils.isEmpty(args)) {
            finalCommand.append(command).append(" ")
            JSONArray(args).let { argsArray ->
                for (i in 0 until argsArray.length()) {
                    finalCommand.append(argsArray.getString(i))
                    finalCommand.append(" ")
                }
            }
        } else {
            finalCommand.append(command)
        }

        val shell = newRootShell()

        val emitData = fun(name: String, data: String) {
            val jsCode =
                "(function() { try { ${callbackFunc}.${name}.emit('data', ${
                    JSONObject.quote(data)
                }); } catch(e) { console.error('emitData', e); } })();"
            postJavascript(jsCode)
        }

        val stdout = object : CallbackList<String>(UiThreadHandler::runAndWait) {
            override fun onAddElement(s: String) {
                emitData("stdout", s)
            }
        }

        val stderr = object : CallbackList<String>(UiThreadHandler::runAndWait) {
            override fun onAddElement(s: String) {
                emitData("stderr", s)
            }
        }

        val future = shell.newJob().add(finalCommand.toString()).to(stdout, stderr).enqueue()
        val completableFuture = CompletableFuture.supplyAsync { future.get() }

        completableFuture.thenAccept { result ->
            val emitExitCode =
                "(function() { try { ${callbackFunc}.emit('exit', ${result.code}); } catch(e) { console.error(`emitExit error: \${e}`); } })();"
            postJavascript(emitExitCode)

            if (result.code != 0) {
                val emitErrCode =
                    "(function() { try { var err = new Error(); err.exitCode = ${result.code}; err.message = ${
                        JSONObject.quote(result.err.joinToString("\n"))
                    };${callbackFunc}.emit('error', err); } catch(e) { console.error('emitErr', e); } })();"
                postJavascript(emitErrCode)
            }
        }.whenComplete { _, _ ->
            runCatching { shell.close() }
        }
    }

    @JavascriptInterface
    fun toast(msg: String) {
        webView?.post {
            webView?.let { Toast.makeText(it.context, msg, Toast.LENGTH_SHORT).show() }
        }
    }

    @JavascriptInterface
    fun fullScreen(enable: Boolean) {
        val context = webView?.context
        if (context is Activity) {
            Handler(Looper.getMainLooper()).post {
                if (enable) hideSystemUI(context.window) else showSystemUI(context.window)
            }
        }
        enableEdgeToEdge(enable)
    }

    @JavascriptInterface
    fun enableEdgeToEdge(enable: Boolean = true) {
        val context = webView?.context
        if (context is WebUIActivity) {
            context.enableEdgeToEdge(enable)
        }
        state.isInsetsEnabled = enable
    }

    @JavascriptInterface
    fun enableInsets(enable: Boolean = true) = enableEdgeToEdge(enable)

    @JavascriptInterface
    fun moduleInfo(): String {
        val currentModuleInfo = JSONObject()
        currentModuleInfo.put("moduleDir", modDir)
        currentModuleInfo.put("id", File(modDir).name)
        return currentModuleInfo.toString()
    }

    @JavascriptInterface
    fun listPackages(type: String): String {
        val context = webView?.context ?: return JSONArray().toString()
        return WebUiPackageRegistry.listPackages(context, type)
    }

    @JavascriptInterface
    fun getPackagesInfo(packageNamesJson: String): String {
        val context = webView?.context ?: return JSONArray().toString()
        return WebUiPackageRegistry.getPackagesInfo(context, packageNamesJson)
    }

    @JavascriptInterface
    fun exit() {
        state.requestExit()
    }
}

fun hideSystemUI(window: Window) =
    WindowInsetsControllerCompat(window, window.decorView).let { controller ->
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

fun showSystemUI(window: Window) =
    WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
