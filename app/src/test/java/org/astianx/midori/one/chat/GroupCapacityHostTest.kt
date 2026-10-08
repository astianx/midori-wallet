package org.astianx.midori.one.chat

import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.jivesoftware.smack.java7.Java7SmackInitializer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Opt-in real-server test; the runner alone provides temporary credentials and phone serials. */
class GroupCapacityHostTest {
    @Test fun fullCapacityWithAndroidSenders() {
        val fixture = File(requireNotNull(System.getProperty("midori.group.fixture")))
        val config = JSONObject(fixture.readText())
        Java7SmackInitializer().initialize()
        GroupCapacityScenario(config, fixture.parentFile, "127.0.0.1", ::println,
            { (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 }, "jvm_heap_used_kib",
            { room, owner, members, approve, received, reply ->
                val reports = JSONArray()
                val serials = config.getJSONArray("serials")
                for (i in 0 until serials.length()) {
                    val serial = serials.getString(i)
                    reports.put(phone(serial, room, owner, members, config.getString("pin"), approve, received, reply))
                }
                reports
            }).run()
    }

    private fun phone(serial: String, room: String, owner: ChatAccount, members: Set<String>, pin: String,
        approve: () -> Unit, received: (String, String) -> Boolean, reply: (String) -> Unit): JSONObject {
        val prefix = listOf("adb", "-s", serial, "shell", "run-as", "org.astianx.midori.one")
        fun run(command: List<String>, input: String? = null): String {
            val process = ProcessBuilder(command).redirectError(File("/dev/null")).start()
            process.outputStream.bufferedWriter().use { if (input != null) it.write(input) }
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) { "Phone laboratory command failed" }
            return output
        }
        fun read(suffix: String) = runCatching { run(prefix + listOf("cat", "files/group-phone-$suffix")) }.getOrNull()
        fun write(suffix: String, value: String) {
            run(prefix + listOf("sh", "-c", "'umask 077; cat > files/group-phone-$suffix'"), value)
        }
        val artifacts = listOf("fixture.json", "ready", "go", "outgoing.json", "ack", "report.json")
        fun clean() { run(prefix + listOf("rm", "-f") + artifacts.map { "files/group-phone-$it" }) }
        clean()
        val replyId = UUID.randomUUID().toString()
        val data = JSONObject().put("account", owner.address).put("password", owner.password).put("pin", pin)
            .put("room", room).put("members", JSONArray(members.toList())).put("reply_id", replyId)
        write("fixture.json", data.toString())
        val log = File(System.getProperty("midori.group.fixture")).parentFile.resolve("phone-$serial.log")
        val process = ProcessBuilder("adb", "-s", serial, "shell", "am", "instrument", "-w", "-r", "-e", "class",
            "org.astianx.midori.one.chat.GroupPhoneProbeTest",
            "org.astianx.midori.one.test/androidx.test.runner.AndroidJUnitRunner").redirectErrorStream(true).redirectOutput(log).start()
        try {
            val deadline = System.nanoTime() + 600_000_000_000L
            var approved = false
            var replied = false
            val acknowledged = mutableSetOf<String>()
            while (process.isAlive && System.nanoTime() < deadline) {
                if (!approved && read("ready") == "ready") { approve(); write("go", "go"); approved = true }
                val outgoing = read("outgoing.json")?.let { runCatching { JSONObject(it) }.getOrNull() }
                if (outgoing != null) {
                    val id = outgoing.getString("id")
                    if (id !in acknowledged && received(id, outgoing.getString("body"))) {
                        write("ack", id); acknowledged.add(id)
                    }
                }
                if (acknowledged.size == 10 && !replied) { reply(replyId); replied = true }
                Thread.sleep(30)
            }
            assertFalse("Android probe timeout", process.isAlive)
            assertEquals(10, acknowledged.size)
            assertTrue("Android instrumentation failed", log.readText().contains("OK (1 test)"))
            val result = JSONObject(requireNotNull(read("report.json")))
            assertEquals("passed", result.getString("status"))
            assertEquals(GroupPolicy.MAX_ENCRYPTED_MEMBERS, result.getInt("members"))
            result.put("serial", serial)
            return result
        } finally {
            if (process.isAlive) {
                run(listOf("adb", "-s", serial, "shell", "am", "force-stop", "org.astianx.midori.one"))
                process.destroyForcibly()
            }
            clean()
        }
    }
}
