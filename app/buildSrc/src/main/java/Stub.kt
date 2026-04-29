import com.android.build.api.artifact.SingleArtifact
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.io.StringReader
import java.security.SecureRandom
import java.util.Random
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.random.asKotlinRandom
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource

private val kRANDOM get() = RANDOM.asKotlinRandom()
private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

private val c1 = mutableListOf<String>()
private val c2 = mutableListOf<String>()
private val c3 = mutableListOf<String>()

fun initRandom(dict: File) {
    RANDOM = if (RAND_SEED != 0) Random(RAND_SEED.toLong()) else SecureRandom()
    c1.clear()
    c2.clear()
    c3.clear()
    for (a in chain('a'..'z', 'A'..'Z')) {
        if (a != 'a' && a != 'A') {
            c1.add("$a")
        }
        for (b in chain('a'..'z', 'A'..'Z', '0'..'9')) {
            c2.add("$a$b")
            for (c in chain('a'..'z', 'A'..'Z', '0'..'9')) {
                c3.add("$a$b$c")
            }
        }
    }
    c1.shuffle(RANDOM)
    c2.shuffle(RANDOM)
    c3.shuffle(RANDOM)
    PrintStream(dict).use {
        for (c in chain(c1, c2, c3)) {
            it.println(c)
        }
    }
}

private fun <T> chain(vararg iters: Iterable<T>) = sequence {
    iters.forEach { it.forEach { v -> yield(v) } }
}

private fun PrintStream.byteField(name: String, bytes: ByteArray) {
    println("public static byte[] $name() {")
    print("byte[] buf = {")
    print(bytes.joinToString(",") { it.toString() })
    println("};")
    println("return buf;")
    println("}")
}

private fun PrintStream.stringField(name: String, value: String) {
    println("""public static final String $name = "$value";""")
}

@CacheableTask
private abstract class ManifestUpdater: DefaultTask() {
    @get:Input
    abstract val applicationId: Property<String>

    @get:Input
    abstract val factoryClass: Property<String>

    @get:Input
    abstract val appClass: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedManifest: RegularFileProperty

    @get:OutputFile
    abstract val outputManifest: RegularFileProperty

    @TaskAction
    fun taskAction() {
        fun String.ind(level: Int) = replaceIndentByMargin("    ".repeat(level))

        val cmpList = mutableListOf<String>()

        cmpList.add("""
            |<provider
            |    android:name="x.COMPONENT_PLACEHOLDER_0"
            |    android:authorities="${'$'}{applicationId}.provider"
            |    android:directBootAware="true"
            |    android:exported="false"
            |    android:grantUriPermissions="true" />""".ind(2)
        )

        cmpList.add("""
            |<receiver
            |    android:name="x.COMPONENT_PLACEHOLDER_1"
            |    android:exported="false">
            |    <intent-filter>
            |        <action android:name="android.intent.action.LOCALE_CHANGED" />
            |        <action android:name="android.intent.action.UID_REMOVED" />
            |        <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            |    </intent-filter>
            |    <intent-filter>
            |        <action android:name="android.intent.action.PACKAGE_REPLACED" />
            |        <action android:name="android.intent.action.PACKAGE_FULLY_REMOVED" />
            |
            |        <data android:scheme="package" />
            |    </intent-filter>
            |</receiver>""".ind(2)
        )

        cmpList.add("""
            |<activity
            |    android:name="x.COMPONENT_PLACEHOLDER_2"
            |    android:exported="true">
            |    <intent-filter>
            |        <action android:name="android.intent.action.MAIN" />
            |        <category android:name="android.intent.category.LAUNCHER" />
            |    </intent-filter>
            |</activity>""".ind(2)
        )

        cmpList.add("""
            |<activity
            |    android:name="x.COMPONENT_PLACEHOLDER_3"
            |    android:directBootAware="true"
            |    android:exported="false"
            |    android:taskAffinity="">
            |    <intent-filter>
            |        <action android:name="android.intent.action.VIEW"/>
            |        <category android:name="android.intent.category.DEFAULT"/>
            |    </intent-filter>
            |</activity>""".ind(2)
        )

        cmpList.add("""
            |<activity
            |    android:name="x.COMPONENT_PLACEHOLDER_6"
            |    android:exported="false"
            |    android:theme="@android:style/Theme.DeviceDefault.NoActionBar"
            |    android:configChanges="density|orientation|screenSize|screenLayout|smallestScreenSize|uiMode|locale|layoutDirection|fontScale|keyboard|keyboardHidden" />""".ind(2)
        )

        cmpList.add("""
            |<service
            |    android:name="x.COMPONENT_PLACEHOLDER_4"
            |    android:exported="false"
            |    android:foregroundServiceType="dataSync" />""".ind(2)
        )

        cmpList.add("""
            |<service
            |    android:name="x.COMPONENT_PLACEHOLDER_5"
            |    android:exported="false"
            |    android:permission="android.permission.BIND_JOB_SERVICE" />""".ind(2)
        )

        // Shuffle the order of the components
        cmpList.shuffle(RANDOM)
        val document = parseManifest(mergedManifest.asFile.get())
        val application = document.getElementsByTagName("application")
            .item(0) as? Element ?: error("No <application> node in merged manifest")

        application.setAttributeNS(ANDROID_NS, "android:appComponentFactory", factoryClass.get())
        application.setAttributeNS(ANDROID_NS, "android:name", appClass.get())

        removePlaceholderComponents(application)
        cmpList.forEach { componentXml ->
            val resolvedXml = componentXml.replace("\${applicationId}", applicationId.get())
            appendComponent(document, application, resolvedXml)
        }

        writeManifest(document, outputManifest.asFile.get())
    }

    private fun parseManifest(file: File): Document {
        return newDocumentBuilder().parse(file)
    }

    private fun appendComponent(document: Document, application: Element, componentXml: String) {
        val fragmentDocument = newDocumentBuilder().parse(
            InputSource(
                StringReader(
                    """
                    <root xmlns:android="$ANDROID_NS">
                    $componentXml
                    </root>
                    """.trimIndent()
                )
            )
        )
        val root = fragmentDocument.documentElement
        val nodes = (0 until root.childNodes.length)
            .map { root.childNodes.item(it) }
            .filterNot { it.nodeType == org.w3c.dom.Node.TEXT_NODE && it.textContent.isBlank() }

        nodes.forEach { node ->
            application.appendChild(document.importNode(node, true))
        }
    }

    private fun removePlaceholderComponents(application: Element) {
        val toRemove = (0 until application.childNodes.length)
            .map { application.childNodes.item(it) }
            .filterIsInstance<Element>()
            .filter { child ->
                child.getAttributeNS(ANDROID_NS, "name").startsWith("x.COMPONENT_PLACEHOLDER_")
            }

        toRemove.forEach(application::removeChild)
    }

    private fun writeManifest(document: Document, outputFile: File) {
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
        }
        outputFile.outputStream().use { stream ->
            transformer.transform(DOMSource(document), StreamResult(stream))
        }
    }

    private fun newDocumentBuilder() =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder()
}

private fun genStubClasses(outDir: File): Pair<String, String> {
    val classNameGenerator = sequence {
        fun notJavaKeyword(name: String) = when (name) {
            "do", "if", "for", "int", "new", "try" -> false
            else -> true
        }

        fun List<String>.process() = asSequence()
            .filter(::notJavaKeyword)
            // Distinct by lower case to support case insensitive file systems
            .distinctBy { it.lowercase() }

        val names = mutableListOf<String>()
        names.addAll(c1)
        names.addAll(c2.process().take(30))
        names.addAll(c3.process().take(30))
        names.shuffle(RANDOM)

        while (true) {
            val cls = StringBuilder()
            cls.append(names.random(kRANDOM))
            cls.append('.')
            cls.append(names.random(kRANDOM))
            // Old Android does not support capitalized package names
            // Check Android 7.0.0 PackageParser#buildClassName
            yield(cls.toString().replaceFirstChar { it.lowercase() })
        }
    }
        // Distinct by lower case to support case insensitive file systems.
        .distinctBy { it.lowercase() }
        .iterator()

    fun genClass(type: String, outDir: File): String {
        val clzName = classNameGenerator.next()
        val (pkg, name) = clzName.split('.')
        val pkgDir = File(outDir, pkg)
        pkgDir.mkdirs()
        PrintStream(File(pkgDir, "$name.java")).use {
            it.println("package $pkg;")
            it.println("import io.github.seyud.weave.$type;")
            it.println("public class $name extends $type {}")
        }
        return clzName
    }

    val factory = genClass("DelegateComponentFactory", outDir)
    val app = genClass("StubApplication", outDir)
    return Pair(factory, app)
}

private fun genEncryptedResources(res: ByteArray, javaOutDir: File, assetsOutDir: File) {
    val mainPkgDir = File(javaOutDir, "io/github/seyud/weave")
    mainPkgDir.mkdirs()
    assetsOutDir.mkdirs()

    // Generate iv and key
    val iv = ByteArray(16)
    val key = ByteArray(32)
    RANDOM.nextBytes(iv)
    RANDOM.nextBytes(key)

    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    val bos = ByteArrayOutputStream()

    ByteArrayInputStream(res).use {
        CipherOutputStream(bos, cipher).use { os ->
            it.transferTo(os)
        }
    }

    val assetName = "res.enc"
    File(assetsOutDir, assetName).writeBytes(bos.toByteArray())

    PrintStream(File(mainPkgDir, "Bytes.java")).use {
        it.println("package io.github.seyud.weave;")
        it.println("public final class Bytes {")

        it.stringField("RES_ASSET", assetName)
        it.byteField("key", key)
        it.byteField("iv", iv)

        it.println("}")
    }
}

private abstract class TaskWithDir : DefaultTask() {
    @get:OutputDirectory
    abstract val outputFolder: DirectoryProperty
}

private abstract class GeneratedStubResourcesTask : DefaultTask() {
    @get:OutputDirectory
    abstract val javaOutputFolder: DirectoryProperty

    @get:OutputDirectory
    abstract val assetsOutputFolder: DirectoryProperty
}

fun Project.setupStubApk() {
    setupAppCommon()

    androidAppComponents {
        onVariants { variant ->
            val variantName = variant.name
            val variantCapped = variantName.replaceFirstChar { it.uppercase() }
            val variantLowered = variantName.lowercase()

            val componentJavaOutDir = layout.buildDirectory
                .dir("generated/${variantLowered}/components").get().asFile
            // Remove stale generated sources before creating randomized class names.
            // This prevents case-only name changes (e.g. h -> H) from colliding on
            // case-insensitive file systems such as Windows.
            componentJavaOutDir.deleteRecursively()
            componentJavaOutDir.mkdirs()

            val (factory, app) = genStubClasses(componentJavaOutDir)

            val manifestUpdater =
                project.tasks.register("${variantName}ManifestProducer", ManifestUpdater::class.java) {
                    applicationId = variant.applicationId
                    factoryClass.set(factory)
                    appClass.set(app)
                }
            variant.artifacts.use(manifestUpdater)
                .wiredWithFiles(
                    ManifestUpdater::mergedManifest,
                    ManifestUpdater::outputManifest)
                .toTransform(SingleArtifact.MERGED_MANIFEST)

            val aapt = sdkComponents.aapt2.get().executable.get().asFile
            val apk = layout.buildDirectory.file("intermediates/linked_resources_binary_format/" +
                    "${variantLowered}/process${variantCapped}Resources/" +
                    "linked-resources-binary-format-${variantLowered}.ap_").get().asFile

            val genResourcesTask = tasks.register("generate${variantCapped}BundledResources", GeneratedStubResourcesTask::class) {
                dependsOn("process${variantCapped}Resources")
                javaOutputFolder.set(layout.buildDirectory.dir("generated/${variantLowered}/resources/java"))
                assetsOutputFolder.set(layout.buildDirectory.dir("generated/${variantLowered}/resources/assets"))

                doLast {
                    val apkTmp = File("${apk}.tmp")
                    providers.exec {
                        commandLine(aapt, "optimize", "-o", apkTmp, "--collapse-resource-names", apk)
                    }.result.get()

                    val bos = ByteArrayOutputStream()
                    ZipFile(apkTmp).use { src ->
                        ZipOutputStream(apk.outputStream()).use {
                            it.setLevel(Deflater.BEST_COMPRESSION)
                            it.putNextEntry(ZipEntry("AndroidManifest.xml"))
                            src.getInputStream(src.getEntry("AndroidManifest.xml")).transferTo(it)
                            it.closeEntry()
                        }
                        DeflaterOutputStream(bos, Deflater(Deflater.BEST_COMPRESSION)).use {
                            src.getInputStream(src.getEntry("resources.arsc")).transferTo(it)
                        }
                    }
                    apkTmp.delete()
                    genEncryptedResources(
                        res = bos.toByteArray(),
                        javaOutDir = javaOutputFolder.get().asFile,
                        assetsOutDir = assetsOutputFolder.get().asFile,
                    )
                }
            }

            variant.sources.java?.let {
                it.addStaticSourceDirectory(componentJavaOutDir.path)
                it.addGeneratedSourceDirectory(genResourcesTask, GeneratedStubResourcesTask::javaOutputFolder)
            }
            variant.sources.assets?.let {
                it.addGeneratedSourceDirectory(genResourcesTask, GeneratedStubResourcesTask::assetsOutputFolder)
            }
        }
    }

    // Override optimizeReleaseResources task
    val apk = layout.buildDirectory.file("intermediates/linked_resources_binary_format/" +
            "release/processReleaseResources/linked-resources-binary-format-release.ap_").get().asFile
    val optRes = layout.buildDirectory.file("intermediates/optimized_processed_res/" +
            "release/optimizeReleaseResources/resources-release-optimize.ap_").get().asFile
    afterEvaluate {
        tasks.named("optimizeReleaseResources") {
            doLast { apk.copyTo(optRes, true) }
        }
    }
    tasks.named<Delete>("clean") {
        delete.addAll(listOf("src/debug/AndroidManifest.xml", "src/release/AndroidManifest.xml"))
    }
}
