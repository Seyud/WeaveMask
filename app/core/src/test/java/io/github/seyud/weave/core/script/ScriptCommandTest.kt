package io.github.seyud.weave.core.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptCommandTest {

    private val host = ScriptHostInfo(magiskVersion = "30.7.5:WEAVE", magiskVersionCode = 30705)
    private val dir = "/data/user/0/io.github.seyud.weave/files/oneshot/" + "a".repeat(64)

    private val params = ScriptParamsParser.parse(
        """
        {"schemaVersion":1,"params":[
          {"key":"FLAG","type":"bool","label":"f","default":true},
          {"key":"MODE","type":"enum","label":"m","options":[{"value":"a"},{"value":"b"}]},
          {"key":"NAME","type":"string","label":"n"},
          {"key":"COUNT","type":"int","label":"c","min":0,"max":10}
        ]}
        """.trimIndent()
    )

    @Test
    fun quotesForPosixShell() {
        assertEquals("''", ScriptCommand.quote(""))
        assertEquals("'abc'", ScriptCommand.quote("abc"))
        assertEquals("'it'\\''s'", ScriptCommand.quote("it's"))
        assertEquals("'\$(id) `id` \"x\" \\ ;&|'", ScriptCommand.quote("\$(id) `id` \"x\" \\ ;&|"))
        assertEquals("''\\'''\\'''", ScriptCommand.quote("''"))
        assertThrows(IllegalArgumentException::class.java) { ScriptCommand.quote("a\u0000b") }
    }

    @Test
    fun buildsEnvironmentInContractOrder() {
        val env = ScriptCommand.environment(host, "my_script", dir, params, mapOf("NAME" to "x'y", "COUNT" to "+3"))
        assertEquals(
            listOf(
                "ASH_STANDALONE" to "1",
                "MAGISK_VER" to "30.7.5:WEAVE",
                "MAGISK_VER_CODE" to "30705",
                "MODULE_ID" to "my_script",
                "MODULE_ONESHOT" to "1",
                "MODPATH" to dir,
                "MODULE_PARAM_FLAG" to "true",
                "KSU_PARAM_FLAG" to "true",
                "MODULE_PARAM_MODE" to "a",
                "KSU_PARAM_MODE" to "a",
                "MODULE_PARAM_NAME" to "x'y",
                "KSU_PARAM_NAME" to "x'y",
                "MODULE_PARAM_COUNT" to "3",
                "KSU_PARAM_COUNT" to "3",
            ),
            env,
        )
    }

    @Test
    fun neverExportsTheKsuNamespace() {
        val env = ScriptCommand.environment(host, "my_script", dir, params, emptyMap())
        // Only the parameter namespace is aliased for scripts published for the KernelSU manager;
        // no KernelSU state variable is exported, so `$KSU` branches keep taking the Magisk path.
        assertTrue(env.none { (name, _) -> name.startsWith("KSU_") && !name.startsWith(ScriptCommand.LEGACY_PARAM_PREFIX) })
        assertTrue(env.none { (name, _) -> name in setOf("KSU", "KSU_VER", "KSU_VER_CODE", "KSU_MODULE", "KSU_ONESHOT") })
        // Every parameter is exported under both prefixes, with the same canonical value.
        for (param in params.params) {
            assertEquals(
                env.single { it.first == param.envName }.second,
                env.single { it.first == ScriptCommand.LEGACY_PARAM_PREFIX + param.key }.second,
            )
        }
        assertEquals(host.magiskVersion, env.single { it.first == "MAGISK_VER" }.second)
    }

    @Test
    fun rejectsInvalidValues() {
        assertThrows(IllegalArgumentException::class.java) {
            ScriptCommand.environment(host, "s", dir, params, mapOf("COUNT" to "11"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScriptCommand.environment(host, "s", dir, params, mapOf("MODE" to "c"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScriptCommand.environment(host, "s", dir, params, mapOf("NAME" to "a\nb"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScriptCommand.build(dir, listOf("BAD-NAME" to "x"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScriptCommand.build(dir, listOf("PATH" to "/tmp"))
        }
    }

    @Test
    fun buildsCommandWithEveryValueQuoted() {
        val env = ScriptCommand.environment(
            host, "my_script", dir, params,
            mapOf("NAME" to "'; rm -rf / #", "MODE" to "b", "FLAG" to "false"),
        )
        val nasty = "'; rm -rf / #"
        val cmd = ScriptCommand.build(dir, env)
        assertEquals(
            "cd '$dir' && ASH_STANDALONE='1' MAGISK_VER='30.7.5:WEAVE' MAGISK_VER_CODE='30705' " +
                "MODULE_ID='my_script' MODULE_ONESHOT='1' MODPATH='$dir' " +
                // Every parameter is exported under both prefixes, with the same value.
                "MODULE_PARAM_FLAG='false' KSU_PARAM_FLAG='false' " +
                "MODULE_PARAM_MODE='b' KSU_PARAM_MODE='b' " +
                "MODULE_PARAM_NAME=${ScriptCommand.quote(nasty)} KSU_PARAM_NAME=${ScriptCommand.quote(nasty)} " +
                "MODULE_PARAM_COUNT='0' KSU_PARAM_COUNT='0' " +
                "PATH=\"\$PATH\":${ScriptCommand.BIN_DIR} ${ScriptCommand.BUSYBOX} sh ./action.sh </dev/null",
            cmd,
        )
    }

    @Test
    fun runsTheScriptWithMagiskBusybox() {
        // Magisk's busybox lives under the tmpfs mount, so the command carries an expansion
        // (`$MAGISKTMP`, falling back to `magisk --path`) instead of a fixed path.
        assertEquals("\"\${MAGISKTMP:-\$(magisk --path)}\"/.magisk/busybox/busybox", ScriptCommand.BUSYBOX)
        assertEquals("\"\${MAGISKTMP:-\$(magisk --path)}\"/.magisk/busybox", ScriptCommand.BIN_DIR)
        val cmd = ScriptCommand.build(dir, emptyList())
        assertEquals(
            "cd '$dir' && PATH=\"\$PATH\":${ScriptCommand.BIN_DIR} ${ScriptCommand.BUSYBOX} sh ./action.sh </dev/null",
            cmd,
        )
    }

    @Test
    fun classifiesExitCodes() {
        assertEquals(ScriptOutcome.SUCCESS, ScriptOutcome.of(0))
        assertEquals(ScriptOutcome.NOT_APPLICABLE, ScriptOutcome.of(2))
        assertEquals(ScriptOutcome.FAILURE, ScriptOutcome.of(1))
        assertEquals(ScriptOutcome.FAILURE, ScriptOutcome.of(127))
        assertEquals(ScriptOutcome.FAILURE, ScriptOutcome.of(-1))
    }

    @Test
    fun runRecordsRoundTrip() {
        val storage = object : ScriptRunStorage {
            val map = HashMap<String, String>()
            override fun read(key: String) = map[key]
            override fun write(key: String, value: String) {
                map[key] = value
            }
        }
        val history = ScriptRunHistory(storage)
        assertEquals(null, history.last("a"))
        val record = ScriptRunRecord(id = "a", versionCode = 3, time = 1234L, exitCode = 2)
        history.record(record)
        assertEquals(record, history.last("a"))
        assertEquals(ScriptOutcome.NOT_APPLICABLE, history.last("a")!!.outcome)
        assertEquals(null, history.last("b"))
        assertEquals(null, ScriptRunRecord.decode("garbage", "a"))
        // A record stored under another id is ignored.
        assertEquals(null, ScriptRunRecord.decode(record.encode(), "b"))
    }
}
