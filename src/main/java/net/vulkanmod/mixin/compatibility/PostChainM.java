package net.vulkanmod.mixin.compatibility;

import net.minecraft.client.renderer.PostChain;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PostChain.class)
public abstract class PostChainM {

    // TODO: port
//    @Shadow private int screenWidth;
//    @Shadow private int screenHeight;
//
//    @Shadow @Final private Map<String, RenderTarget> customRenderTargets;
//    @Shadow @Final private RenderTarget screenTarget;
//    @Shadow @Final private List<PostPass> passes;
//
//    @Shadow private float lastStamp;
//    @Shadow private float time;
//
//    @Shadow public abstract void addTempTarget(String string, int i, int j);
//    @Shadow protected abstract void parseTargetNode(JsonElement jsonElement) throws ChainedJsonException;
//    @Shadow protected abstract void parseUniformNode(JsonElement jsonElement) throws ChainedJsonException;
//
////    /**
////     * @author
////     * @reason
////     */
////    @Overwrite
////    public void process(float f) {
////        if (f < this.lastStamp) {
////            this.time += 1.0F - this.lastStamp;
////            this.time += f;
////        } else {
////            this.time += f - this.lastStamp;
////        }
////
////        this.lastStamp = f;
////
////        while(this.time > 20.0F) {
////            this.time -= 20.0F;
////        }
////
////        int filterMode = 9728;
////
////        for(PostPass postPass : this.passes) {
////            int passFilterMode = postPass.getFilterMode();
////            if (filterMode != passFilterMode) {
////                this.setFilterMode(passFilterMode);
////                filterMode = passFilterMode;
////            }
////
////            postPass.process(this.time / 20.0F);
////        }
////
////        this.setFilterMode(9728);
////
////        Renderer.resetViewport();
////    }

}
