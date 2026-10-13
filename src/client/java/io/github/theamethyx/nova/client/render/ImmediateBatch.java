package io.github.theamethyx.nova.client.render;

import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Collects vertices for one render pipeline during a frame and draws them in a single render pass.
 * Follows the custom-pipeline pattern from the Fabric documentation for 1.21.11: vertices go into a
 * {@link BufferBuilder}, are copied into a rotating GPU buffer and drawn into the main target.
 */
final class ImmediateBatch implements AutoCloseable {
	private static final Vector4f COLOR_MODULATOR = new Vector4f(1, 1, 1, 1);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

	private final RenderPipeline pipeline;
	private final String name;
	private final ByteBufferBuilder allocator = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
	private BufferBuilder buffer;
	private MappableRingBuffer vertexBuffer;
	private int vertexCount;

	ImmediateBatch(RenderPipeline pipeline, String name) {
		this.pipeline = pipeline;
		this.name = name;
	}

	BufferBuilder buffer() {
		if (buffer == null) {
			buffer = new BufferBuilder(allocator, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
		}

		return buffer;
	}

	void countVertices(int count) {
		vertexCount += count;
	}

	int lastVertexCount() {
		return vertexCount;
	}

	/** Draws everything collected this frame. Does nothing if no geometry was added. */
	void draw() {
		vertexCount = 0;
		if (buffer == null) {
			return;
		}

		MeshData mesh = buffer.build();
		buffer = null;
		if (mesh == null) {
			return;
		}

		try (mesh) {
			MeshData.DrawState state = mesh.drawState();
			VertexFormat format = state.format();
			int size = state.vertexCount() * format.getVertexSize();

			if (vertexBuffer == null || vertexBuffer.size() < size) {
				if (vertexBuffer != null) {
					vertexBuffer.close();
				}

				vertexBuffer = new MappableRingBuffer(() -> "nova " + name, GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
			}

			CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
			try (GpuBuffer.MappedView view = encoder.mapBuffer(vertexBuffer.currentBuffer().slice(0, mesh.vertexBuffer().remaining()), false, true)) {
				MemoryUtil.memCopy(mesh.vertexBuffer(), view.data());
			}

			GpuBuffer indices;
			VertexFormat.IndexType indexType;
			if (pipeline.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
				mesh.sortQuads(allocator, RenderSystem.getProjectionType().vertexSorting());
				indices = pipeline.getVertexFormat().uploadImmediateIndexBuffer(mesh.indexBuffer());
				indexType = mesh.drawState().indexType();
			} else {
				RenderSystem.AutoStorageIndexBuffer shared = RenderSystem.getSequentialBuffer(pipeline.getVertexFormatMode());
				indices = shared.getBuffer(state.indexCount());
				indexType = shared.type();
			}

			GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
					.writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
			Minecraft client = Minecraft.getInstance();

			try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "nova " + name,
					client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
					client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
				pass.setPipeline(pipeline);
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("DynamicTransforms", transforms);
				pass.setVertexBuffer(0, vertexBuffer.currentBuffer());
				pass.setIndexBuffer(indices, indexType);
				pass.drawIndexed(0, 0, state.indexCount(), 1);
			}

			vertexBuffer.rotate();
		}
	}

	@Override
	public void close() {
		if (buffer != null) {
			MeshData leftover = buffer.build();
			if (leftover != null) {
				leftover.close();
			}

			buffer = null;
		}

		allocator.close();
		if (vertexBuffer != null) {
			vertexBuffer.close();
			vertexBuffer = null;
		}
	}
}
