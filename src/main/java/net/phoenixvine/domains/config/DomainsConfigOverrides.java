package net.phoenixvine.domains.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.phoenixvine.domains.PhoenixDomains;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DomainsConfigOverrides {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String OVERRIDE_FILE_NAME = "phoenix_domains-server-overrides.toml";

    private static Field cachedValueField;

    private DomainsConfigOverrides() {}

    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != DomainsConfig.SPEC) {
            return;
        }

        Path overridePath = FMLPaths.CONFIGDIR.get().resolve(OVERRIDE_FILE_NAME);
        if (!Files.exists(overridePath)) {
            try {
                generateDefaultOverrideFile(overridePath);
            } catch (Exception e) {
                LOGGER.warn("[{}] Unexpected error auto-generating global config override file '{}' — " +
                        "continuing without one. Cause: {}", PhoenixDomains.MOD_ID, OVERRIDE_FILE_NAME, e.toString());
            }
            if (!Files.exists(overridePath)) {

                return;
            }
        }

        CommentedFileConfig overrideConfig = CommentedFileConfig.builder(overridePath).sync().build();
        try {
            overrideConfig.load();
        } catch (Exception e) {
            LOGGER.warn("[{}] Failed to parse global config override file '{}' — ignoring it. Cause: {}",
                    PhoenixDomains.MOD_ID, OVERRIDE_FILE_NAME, e.toString());
            return;
        }

        try {
            Map<String, ForgeConfigSpec.ConfigValue<?>> valuesByPath = collectConfigValues();
            applyOverrides(overrideConfig, new ArrayDeque<>(), valuesByPath);
        } catch (Exception e) {
            LOGGER.warn("[{}] Unexpected error applying global config overrides from '{}' — some or all " +
                    "overrides may not have been applied. Cause: {}", PhoenixDomains.MOD_ID, OVERRIDE_FILE_NAME,
                    e.toString());
        } finally {
            overrideConfig.close();
        }
    }

    private static void applyOverrides(UnmodifiableConfig node, Deque<String> pathSoFar,
                                       Map<String, ForgeConfigSpec.ConfigValue<?>> valuesByPath) {
        for (Map.Entry<String, Object> entry : node.valueMap().entrySet()) {
            Object rawValue = entry.getValue();
            pathSoFar.addLast(entry.getKey());
            try {
                if (rawValue instanceof UnmodifiableConfig nested) {
                    applyOverrides(nested, pathSoFar, valuesByPath);
                } else {
                    String dottedPath = String.join(".", pathSoFar);
                    applyLeaf(dottedPath, rawValue, valuesByPath);
                }
            } finally {
                pathSoFar.removeLast();
            }
        }
    }

    private static void applyLeaf(String dottedPath, Object rawValue,
                                  Map<String, ForgeConfigSpec.ConfigValue<?>> valuesByPath) {
        ForgeConfigSpec.ConfigValue<?> configValue = valuesByPath.get(dottedPath);
        if (configValue == null) {
            LOGGER.warn("[{}] '{}' has override key '{}' which does not match any known {} config path — " +
                    "ignoring it (check for a typo).", PhoenixDomains.MOD_ID, OVERRIDE_FILE_NAME, dottedPath,
                    PhoenixDomains.MOD_ID);
            return;
        }

        Object defaultValue = configValue.getDefault();
        Object coerced;
        try {
            coerced = coerce(rawValue, defaultValue);
        } catch (Exception e) {
            LOGGER.warn("[{}] Override key '{}' in '{}' has a value of the wrong type ({}) — ignoring it.",
                    PhoenixDomains.MOD_ID, dottedPath, OVERRIDE_FILE_NAME, rawValue, e);
            return;
        }

        if (Objects.equals(coerced, defaultValue)) {

            return;
        }

        try {
            setCachedValue(configValue, coerced);
            LOGGER.info("[{}] Applied global config override: {} = {}", PhoenixDomains.MOD_ID, dottedPath, coerced);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("[{}] Failed to apply global config override for '{}' due to a reflection error: {}",
                    PhoenixDomains.MOD_ID, dottedPath, e.toString());
        }
    }

    private static Object coerce(Object rawValue, Object defaultValue) {
        if (defaultValue instanceof Integer && rawValue instanceof Number n) {
            return n.intValue();
        }
        if (defaultValue instanceof Long && rawValue instanceof Number n) {
            return n.longValue();
        }
        if (defaultValue instanceof Double && rawValue instanceof Number n) {
            return n.doubleValue();
        }
        if (defaultValue instanceof Float && rawValue instanceof Number n) {
            return n.floatValue();
        }
        if (defaultValue instanceof Boolean && rawValue instanceof Boolean) {
            return rawValue;
        }
        if (defaultValue instanceof String && rawValue instanceof String) {
            return rawValue;
        }
        if (defaultValue instanceof Enum<?> && rawValue instanceof String s) {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            Enum<?> matched = Enum.valueOf((Class<? extends Enum>) defaultValue.getClass(),
                    s.toUpperCase(java.util.Locale.ROOT));
            return matched;
        }
        if (defaultValue != null && defaultValue.getClass().isInstance(rawValue)) {
            return rawValue;
        }
        throw new IllegalArgumentException("cannot coerce " + rawValue + " (" +
                (rawValue == null ? "null" : rawValue.getClass()) + ") to " +
                (defaultValue == null ? "null" : defaultValue.getClass()));
    }

    private static void setCachedValue(ForgeConfigSpec.ConfigValue<?> configValue, Object value)
                                                                                                 throws ReflectiveOperationException {
        if (cachedValueField == null) {
            Field field = ForgeConfigSpec.ConfigValue.class.getDeclaredField("cachedValue");
            field.setAccessible(true);
            cachedValueField = field;
        }
        cachedValueField.set(configValue, value);
    }

    private static void generateDefaultOverrideFile(Path overridePath) {
        if (overridePath.getParent() != null) {
            try {
                Files.createDirectories(overridePath.getParent());
            } catch (Exception e) {
                LOGGER.warn("[{}] Failed to create the config directory for '{}' — skipping " +
                        "auto-generation of the global override file. Cause: {}", PhoenixDomains.MOD_ID,
                        OVERRIDE_FILE_NAME, e.toString());
                return;
            }
        }

        CommentedFileConfig fileConfig = CommentedFileConfig.builder(overridePath).sync().build();
        try {
            UnmodifiableConfig specTree = DomainsConfig.SPEC.getSpec();
            Map<String, ForgeConfigSpec.ConfigValue<?>> valuesByPath = collectConfigValues();
            for (ForgeConfigSpec.ConfigValue<?> configValue : valuesByPath.values()) {
                List<String> path = configValue.getPath();
                fileConfig.set(path, configValue.getDefault());

                Object specNode = specTree.get(path);
                if (specNode instanceof ForgeConfigSpec.ValueSpec valueSpec && valueSpec.getComment() != null) {
                    fileConfig.setComment(path, valueSpec.getComment());
                }
            }
            fileConfig.save();
        } catch (Exception e) {
            LOGGER.warn("[{}] Failed to auto-generate global config override file '{}' — continuing " +
                    "without one this load. Cause: {}", PhoenixDomains.MOD_ID, OVERRIDE_FILE_NAME, e.toString());
            return;
        } finally {
            fileConfig.close();
        }

        try {
            prependHeaderComment(overridePath);
        } catch (Exception e) {
            LOGGER.warn("[{}] Generated '{}' but failed to write its explanatory header comment — the " +
                    "file is still complete and usable. Cause: {}", PhoenixDomains.MOD_ID, OVERRIDE_FILE_NAME,
                    e.toString());
        }

        LOGGER.info("[{}] Generated global config override file '{}' with every key at its current default.",
                PhoenixDomains.MOD_ID, OVERRIDE_FILE_NAME);
    }

    private static void prependHeaderComment(Path overridePath) throws IOException {
        List<String> body = Files.readAllLines(overridePath);
        List<String> header = List.of(
                "# " + OVERRIDE_FILE_NAME,
                "#",
                "# Every value below is currently set to its shipped DEFAULT. This file is auto-generated the",
                "# first time it's missing, purely so it reads like a normal, complete config you can browse",
                "# and edit in place — not an empty stub you have to fill in from documentation.",
                "#",
                "# Editing a value here and saving is what makes it an ACTIVE override: on every subsequent",
                "# world/server load or config reload, any key whose value here differs from " +
                        PhoenixDomains.MOD_ID + "'s",
                "# current built-in default for that key is force-applied on top of that world's own",
                "# serverconfig. A key left untouched (still equal to the default) has no effect — that",
                "# world's own serverconfig (or the built-in default) applies normally.",
                "");
        List<String> combined = new ArrayList<>(header.size() + body.size());
        combined.addAll(header);
        combined.addAll(body);
        Files.write(overridePath, combined);
    }

    private static Map<String, ForgeConfigSpec.ConfigValue<?>> collectConfigValues() throws IllegalAccessException {
        Map<String, ForgeConfigSpec.ConfigValue<?>> result = new HashMap<>();
        for (Field field : DomainsConfig.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !Modifier.isPublic(field.getModifiers())) {
                continue;
            }
            if (!ForgeConfigSpec.ConfigValue.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            ForgeConfigSpec.ConfigValue<?> configValue = (ForgeConfigSpec.ConfigValue<?>) field.get(null);
            if (configValue == null) {
                continue;
            }
            List<String> path = configValue.getPath();
            result.put(String.join(".", path), configValue);
        }
        return result;
    }
}
