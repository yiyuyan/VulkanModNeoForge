package net.vulkanmod.render.chunk.build.frapi;

import java.util.HashMap;

import net.minecraft.resources.ResourceLocation;

import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.material.MaterialFinder;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableMesh;
import net.vulkanmod.render.chunk.build.frapi.material.MaterialFinderImpl;
import net.vulkanmod.render.chunk.build.frapi.material.RenderMaterialImpl;
import net.vulkanmod.render.chunk.build.frapi.mesh.MutableMeshImpl;

public class VulkanModRenderer implements Renderer {
	public static final VulkanModRenderer INSTANCE = new VulkanModRenderer();

	public static final RenderMaterial STANDARD_MATERIAL = INSTANCE.materialFinder().find();

	static {
		INSTANCE.registerMaterial(RenderMaterial.STANDARD_ID, STANDARD_MATERIAL);
	}

	private final HashMap<ResourceLocation, RenderMaterial> materialMap = new HashMap<>();

	private VulkanModRenderer() {}

	@Override
	public MutableMesh mutableMesh() {
		return new MutableMeshImpl();
	}

	@Override
	public MaterialFinder materialFinder() {
		return new MaterialFinderImpl();
	}

	@Override
	public RenderMaterial materialById(ResourceLocation id) {
		return materialMap.get(id);
	}

	@Override
	public boolean registerMaterial(ResourceLocation id, RenderMaterial material) {
		if (materialMap.containsKey(id)) return false;

		materialMap.put(id, material);
		return true;
	}
}
