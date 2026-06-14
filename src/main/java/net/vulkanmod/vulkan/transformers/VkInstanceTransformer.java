package net.vulkanmod.vulkan.transformers;

import org.objectweb.asm.*;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

import static org.objectweb.asm.Opcodes.*;

public class VkInstanceTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {

        if (!"org/lwjgl/vulkan/VkInstance".equals(className)) {
            return null;
        }

        System.out.println("[VkInstanceTransformer] Transforming: " + className);

        ClassReader cr = new ClassReader(classfileBuffer);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                if ("getAvailableDeviceExtensions".equals(name) && "(J)Ljava/util/Set;".equals(descriptor)) {
                    return new ReturnEmptySetVisitor(mv, access, name, descriptor);
                }
                return mv;
            }
        };

        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    static class ReturnEmptySetVisitor extends MethodVisitor {

        ReturnEmptySetVisitor(MethodVisitor mv, int access, String name, String descriptor) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            mv.visitTypeInsn(NEW, "java/util/HashSet");
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, "java/util/HashSet", "<init>", "()V", false);
            mv.visitInsn(ARETURN);
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            super.visitMaxs(2, 1);
        }

        @Override
        public void visitInsn(int opcode) {}

        @Override
        public void visitVarInsn(int opcode, int varIndex) {}

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {}

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {}

        @Override
        public void visitJumpInsn(int opcode, Label label) {}

        @Override
        public void visitLabel(Label label) {}

        @Override
        public void visitLdcInsn(Object value) {}

        @Override
        public void visitTypeInsn(int opcode, String type) {}

        @Override
        public void visitIntInsn(int opcode, int operand) {}

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {}

        @Override
        public void visitLineNumber(int line, Label start) {}

        @Override
        public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {}

        @Override
        public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {}
    }
}