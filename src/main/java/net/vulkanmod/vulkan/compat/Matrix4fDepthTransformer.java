package net.vulkanmod.vulkan.compat;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * Retransforms {@code org.joml.Matrix4f} so its OpenGL-default projection builders use the
 * Vulkan depth convention ({@code zZeroToOne = true}, i.e. clip-space depth in [0, 1]).
 *
 * <p>This is the same correction VulkanMod's {@code Matrix4fM} mixin performs, applied here as a
 * direct ASM retransform driven by an {@link java.lang.instrument.Instrumentation} agent INSTEAD
 * of a Mixin. Doing it outside Mixin means Mixin's IllegalClassLoadError check is never touched,
 * so unlike the removed "mr" (MixinRuntime) library this does not disable that check globally and
 * cannot break other mods' mixins (e.g. Cobblemon).
 *
 * <p>Each of the four OpenGL-default float overloads is replaced with a body that simply calls its
 * existing boolean overload with {@code zZeroToOne = true}. Any failure leaves {@code Matrix4f}
 * untouched ({@link #transform} returns {@code null}), so callers can fall back safely.
 */
public class Matrix4fDepthTransformer implements ClassFileTransformer {

    public static final String TARGET = "org/joml/Matrix4f";

    /** Set true once at least one target method was rewritten and the class re-emitted. */
    public volatile boolean applied = false;

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (!TARGET.equals(className)) return null;
        try {
            final int[] count = {0};
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    String boolDesc = redirect(name, descriptor);
                    if (boolDesc == null) return mv;

                    // Replace the body with: return this.<name>(<float args...>, true);
                    int n = Type.getArgumentTypes(descriptor).length;
                    mv.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    for (int i = 0; i < n; i++) mv.visitVarInsn(Opcodes.FLOAD, 1 + i);
                    mv.visitInsn(Opcodes.ICONST_1);
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, name, boolDesc, false);
                    mv.visitInsn(Opcodes.ARETURN);
                    mv.visitMaxs(0, 0); // recomputed by COMPUTE_MAXS
                    mv.visitEnd();
                    count[0]++;

                    // Detached visitor: the reader drives this, discarding the original instructions,
                    // while the writer's MethodVisitor already holds the body we emitted above.
                    return new MethodVisitor(Opcodes.ASM9) {};
                }
            };
            cr.accept(cv, 0);
            if (count[0] == 0) return null; // matched nothing -> leave class unchanged
            byte[] out = cw.toByteArray();
            this.applied = true;
            return out;
        } catch (Throwable t) {
            return null; // fail-safe: never hand back corrupt Matrix4f bytecode
        }
    }

    /** Maps an OpenGL-default float overload to its {@code zZeroToOne} boolean overload descriptor. */
    private static String redirect(String name, String descriptor) {
        switch (name) {
            case "perspective":
            case "setPerspective":
                if (descriptor.equals("(FFFF)Lorg/joml/Matrix4f;")) return "(FFFFZ)Lorg/joml/Matrix4f;";
                break;
            case "ortho":
            case "setOrtho":
                if (descriptor.equals("(FFFFFF)Lorg/joml/Matrix4f;")) return "(FFFFFFZ)Lorg/joml/Matrix4f;";
                break;
        }
        return null;
    }
}
