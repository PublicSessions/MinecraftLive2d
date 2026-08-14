package com.ciallo.live2d.client;

import com.ciallo.live2d.cubism.CubismExpression;
import com.ciallo.live2d.cubism.CubismNativeModel;
import com.ciallo.live2d.cubism.Live2dMotion;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Live2dModelLoader {

	private static final List<String> BUILTIN_MODELS = List.of("kaguya", "noir", "yachi");

	private final MinecraftClient mc;
	private final Path userModelsDir;

	public Live2dModelLoader(MinecraftClient mc) {
		this.mc = mc;
		this.userModelsDir = mc.runDirectory.toPath().resolve("config").resolve("live2d");
	}

	public List<String> getAvailableModels() {
		LinkedHashSet<String> names = new LinkedHashSet<>(BUILTIN_MODELS);
		try {
			if (Files.isDirectory(userModelsDir)) {
				try (var stream = Files.list(userModelsDir)) {
					stream.filter(Files::isDirectory).forEach(dir -> {
						if (findModel3(dir) != null) {
							names.add(dir.getFileName().toString());
						}
					});
				}
			}
		} catch (IOException ignored) {

		}
		return new ArrayList<>(names);
	}

	public boolean isUserModel(String name) {
		return !BUILTIN_MODELS.contains(name);
	}

	public Path userModelsDir() {
		return userModelsDir;
	}

	private Path userModelDir(String name) {
		return userModelsDir.resolve(name);
	}

	private String findModel3(Path dir) {
		try (var stream = Files.list(dir)) {
			return stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".model3.json"))
					.map(p -> p.getFileName().toString())
					.findFirst()
					.orElse(null);
		} catch (IOException e) {
			return null;
		}
	}

	public JsonObject readModel3(String name) {
		if (isUserModel(name)) {
			Path dir = userModelDir(name);
			String file = findModel3(dir);
			if (file == null) {
				return null;
			}
			try (Reader reader = Files.newBufferedReader(dir.resolve(file), StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader).getAsJsonObject();
			} catch (IOException e) {
				return null;
			}
		}
		Identifier id = Identifier.of("live2d", "live2d/" + name + "/" + name + ".model3.json");
		Optional<Resource> res = mc.getResourceManager().getResource(id);
		if (res.isEmpty()) {
			return null;
		}
		try (InputStreamReader reader = new InputStreamReader(res.get().getInputStream(), StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		} catch (IOException e) {
			return null;
		}
	}

	public byte[] readBytes(String name, String relPath) {
		String normalized = relPath.replace('\\', '/');
		if (isUserModel(name)) {
			Path base = userModelDir(name);
			Path p = base.resolve(normalized).normalize();
			try {
				if (p.startsWith(base) && Files.isRegularFile(p)) {
					return Files.readAllBytes(p);
				}
			} catch (IOException ignored) {
			}
			return null;
		}
		Identifier id = Identifier.of("live2d", "live2d/" + name + "/" + normalized);
		Optional<Resource> res = mc.getResourceManager().getResource(id);
		if (res.isEmpty()) {
			return null;
		}
		try (InputStream in = res.get().getInputStream()) {
			return in.readAllBytes();
		} catch (IOException e) {
			return null;
		}
	}

	public boolean hasResource(String name, String relPath) {
		return readBytes(name, relPath) != null;
	}

	public Identifier resolveTexture(String name, String texPath, int index) {
		if (isUserModel(name)) {
			byte[] data = readBytes(name, texPath);
			if (data == null) {
				return null;
			}
			try {
				NativeImage image;
				try (ByteArrayInputStream bin = new ByteArrayInputStream(data)) {
					image = NativeImage.read(bin);
				}
				NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "live2d_user_" + name, image);
				Identifier id = Identifier.of("dynamic", "live2d/user/" + name + "/" + index);
				mc.getTextureManager().registerTexture(id, texture);
				return id;
			} catch (Throwable t) {
				System.err.println("[Live2D] failed to load texture " + texPath + ": " + t);
				return null;
			}
		}
		return Identifier.of("live2d", "live2d/" + name + "/" + texPath.replace('\\', '/'));
	}

	public CubismNativeModel createModel(String name, Path gameDir) throws Exception {
		JsonObject model3 = readModel3(name);
		if (model3 == null || !model3.has("FileReferences")) {
			throw new IllegalStateException("Missing model3.json for '" + name + "'");
		}
		JsonObject refs = model3.getAsJsonObject("FileReferences");
		if (!refs.has("Moc")) {
			throw new IllegalStateException("model3.json of '" + name + "' has no FileReferences.Moc");
		}
		String mocFile = refs.get("Moc").getAsString();
		byte[] mocBytes = readBytes(name, mocFile);
		if (mocBytes == null) {
			throw new IllegalStateException("Missing moc file for '" + name + "': " + mocFile);
		}
		return CubismNativeModel.loadBytes(mocBytes, gameDir);
	}

	public List<Identifier> loadTextures(String name, JsonObject model3) {
		List<Identifier> out = new ArrayList<>();
		if (model3 == null || !model3.has("FileReferences") || !model3.getAsJsonObject("FileReferences").has("Textures")) {
			return out;
		}
		JsonArray arr = model3.getAsJsonObject("FileReferences").getAsJsonArray("Textures");
		int i = 0;
		for (var el : arr) {
			Identifier id = resolveTexture(name, el.getAsString(), i++);
			if (id == null) {
				id = TextureManager.MISSING_IDENTIFIER;
			}
			out.add(id);
		}
		return out;
	}

	public void loadExpressions(CubismNativeModel model, String name, JsonObject model3) {

		if (model3 != null && model3.has("Expressions") && model3.get("Expressions").isJsonArray()) {
			for (var el : model3.getAsJsonArray("Expressions")) {
				if (!el.isJsonObject()) {
					continue;
				}
				JsonObject o = el.getAsJsonObject();
				if (!o.has("Name") || !o.has("File")) {
					continue;
				}
				String exprName = o.get("Name").getAsString();
				String file = o.get("File").getAsString();
				addExpression(model, name, exprName, readBytes(name, file));
			}
		}

		for (String file : listFolderFiles(name, ".exp3.json")) {
			String base = baseName(file);
			if (base != null && !model.hasExpression(base)) {
				addExpression(model, name, base, readBytes(name, file));
			}
		}
	}

	private void addExpression(CubismNativeModel model, String name, String exprName, byte[] data) {
		if (exprName == null || data == null) {
			return;
		}
		try {
			JsonObject exprJson = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
			model.addExpression(exprName, CubismExpression.parse(exprName, exprJson));
		} catch (Throwable t) {
			System.err.println("[Live2D] failed to load expression " + exprName + ": " + t);
		}
	}

	public void loadMotions(CubismNativeModel model, String name, JsonObject model3) {
		if (model3 != null && model3.has("Motions") && model3.get("Motions").isJsonObject()) {
			JsonObject groups = model3.getAsJsonObject("Motions");
			for (var group : groups.entrySet()) {
				if (!group.getValue().isJsonArray()) {
					continue;
				}
				for (var el : group.getValue().getAsJsonArray()) {
					if (!el.isJsonObject()) {
						continue;
					}
					JsonObject o = el.getAsJsonObject();
					if (!o.has("File")) {
						continue;
					}
					String file = o.get("File").getAsString();
					addMotion(model, name, file, readBytes(name, file));
				}
			}
		}

		for (String file : listFolderFiles(name, ".motion3.json")) {
			if (!model.hasMotion(file)) {
				addMotion(model, name, file, readBytes(name, file));
			}
		}
	}

	private void addMotion(CubismNativeModel model, String name, String file, byte[] data) {
		if (file == null || data == null) {
			return;
		}
		try {
			JsonObject motionJson = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
			Live2dMotion motion = Live2dMotion.parse(file, motionJson);
			model.addMotion(motion.getName(), motion);
		} catch (Throwable t) {
			System.err.println("[Live2D] failed to load motion " + file + ": " + t);
		}
	}

	private List<String> listFolderFiles(String name, String suffix) {
		List<String> out = new ArrayList<>();
		if (isUserModel(name)) {
			Path dir = userModelDir(name);
			try (var stream = Files.list(dir)) {
				stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(suffix))
						.map(p -> p.getFileName().toString())
						.forEach(out::add);
			} catch (IOException ignored) {
			}
			return out;
		}
		Identifier root = Identifier.of("live2d", "live2d/" + name);
		Map<Identifier, Resource> found = mc.getResourceManager().findResources(root.getNamespace(),
				id -> id.getPath().startsWith(root.getPath()) && id.getPath().endsWith(suffix));
		for (Identifier id : found.keySet()) {
			String path = id.getPath();
			out.add(path.substring(path.lastIndexOf('/') + 1));
		}
		return out;
	}

	private String baseName(String file) {
		String base = file;
		int slash = base.lastIndexOf('/');
		if (slash >= 0) {
			base = base.substring(slash + 1);
		}
		int dot = base.lastIndexOf('.');
		if (dot > 0) {
			base = base.substring(0, dot);
		}
		return base;
	}
}