import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.MessageDigest

abstract class GenerateChecksum : DefaultTask() {
    @get:InputFile
    abstract val archive: RegularFileProperty

    @get:OutputFile
    abstract val checksum: RegularFileProperty

    @TaskAction
    fun generate(): Unit = archive.get().asFile.let {
        checksum.get().asFile.writeText("${it.sha256()}  ${it.name}\n")
    }
}

private fun File.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(readBytes())
    .joinToString("") { "%02x".format(it) }
