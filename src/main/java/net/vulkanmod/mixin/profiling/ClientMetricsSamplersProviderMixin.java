// In a new file, e.g., ClientMetricsSamplersProviderMixin.java
package net.vulkanmod.mixin.profiling; // Or an appropriate package

import com.google.common.collect.Sets;
import net.minecraft.client.profiling.ClientMetricsSamplersProvider;
import net.minecraft.util.profiling.metrics.MetricSampler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;
import java.util.Set;

@Mixin(ClientMetricsSamplersProvider.class)
public class ClientMetricsSamplersProviderMixin {

    @Redirect(method = "registerStaticSamplers", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/metrics/profiling/ServerMetricsSamplersProvider;runtimeIndependentSamplers()Ljava/util/Set;"))
    private Set<MetricSampler> preventTimerQuery() {
        return Sets.newHashSet();
    }
}