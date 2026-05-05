package net.vulkanmod.render.chunk.build.frapi.accessor;


import net.vulkanmod.render.chunk.build.frapi.render.MeshItemCommand;

import java.util.List;

public interface AccessBatchingRenderCommandQueue {
	List<MeshItemCommand> getMeshItemCommands();
}
