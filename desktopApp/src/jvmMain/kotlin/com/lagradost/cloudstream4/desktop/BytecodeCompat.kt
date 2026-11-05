package com.lagradost.cloudstream4.desktop

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.nio.file.FileSystem
import java.nio.file.Files

/**
 * Fixes calls from extensions built against older CloudStream APIs whose JVM signature changed
 * in a compatible way, so they link instead of failing with NoSuchMethodError at runtime.
 * (Signature changes that need a different constructor are handled with hidden constructors in the library.)
 */
object BytecodeCompat {
    private data class Call(val owner: String, val name: String, val oldDesc: String, val newDesc: String)

    private val calls = listOf(
        // dubStatus used to be an EnumSet, now a MutableSet: EnumSet is a Set, so only the descriptor changes
        Call("com/lagradost/cloudstream3/AnimeSearchResponse", "setDubStatus", "(Ljava/util/EnumSet;)V", "(Ljava/util/Set;)V"),
    )

    /** Rewrites every class of a converted jar in place; returns how many call sites changed. */
    fun apply(fs: FileSystem): Int {
        var changed = 0
        Files.walk(fs.getPath("/")).use { paths ->
            paths.filter { it.toString().endsWith(".class") }.toList().forEach { path ->
                val bytes = Files.readAllBytes(path)
                var count = 0
                val writer = ClassWriter(0)
                ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9, writer) {
                    override fun visitMethod(access: Int, name: String?, desc: String?, sig: String?, ex: Array<out String>?): MethodVisitor {
                        val mv = super.visitMethod(access, name, desc, sig, ex)
                        return object : MethodVisitor(Opcodes.ASM9, mv) {
                            override fun visitMethodInsn(op: Int, owner: String, mName: String, mDesc: String, itf: Boolean) {
                                val fix = calls.firstOrNull { it.owner == owner && it.name == mName && it.oldDesc == mDesc }
                                if (fix == null) return super.visitMethodInsn(op, owner, mName, mDesc, itf)
                                count++
                                super.visitMethodInsn(op, owner, mName, fix.newDesc, itf)
                            }
                        }
                    }
                }, 0)
                if (count > 0) {
                    Files.write(path, writer.toByteArray())
                    changed += count
                }
            }
        }
        return changed
    }
}
