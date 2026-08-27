package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.SoftDeleteAuditEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guard: no repository may bypass soft delete with a bulk JPQL or native DELETE
 * on a SoftDeleteAuditEntity-family table. @SQLDelete only protects managed-entity
 * removal (repository.delete/deleteById); bulk/native DELETE statements execute a
 * real DELETE and permanently lose data. Derived deleteBy... methods are fine
 * (entity-by-entity em.remove rides @SQLDelete); hard-delete entities
 * (GeneratedIdAuditEntity: tokens, logs, record values) and entity-less join
 * tables are out of scope.
 */
class SoftDeleteBulkDeleteScanTest {

    private static final String REPOSITORY_PACKAGE = "com.ibrhalil.forgesys.persistence.repository";
    private static final String ENTITY_PACKAGE = "com.ibrhalil.forgesys.entity";

    private static final Pattern JPQL_BULK_DELETE =
            Pattern.compile("(?is)^\\s*delete\\s+from\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern NATIVE_DELETE_FROM =
            Pattern.compile("(?is)\\bdelete\\s+from\\s+([a-zA-Z_\"`][a-zA-Z0-9_.\"`]*)");

    @Test
    void noBulkOrNativeDeleteBypassesSoftDelete() throws Exception {
        Map<String, Class<?>> entityNames = scanEntityNames();
        List<String> violations = new ArrayList<>();

        for (Class<?> repo : scanClasses(REPOSITORY_PACKAGE)) {
            if (!repo.isInterface()) {
                continue;
            }
            for (Method method : repo.getDeclaredMethods()) {
                Query query = method.getAnnotation(Query.class);
                if (query == null) {
                    continue;
                }
                if (query.nativeQuery()) {
                    checkNativeDelete(repo, method, query.value(), violations);
                } else {
                    checkJpqlBulkDelete(repo, method, query.value(), entityNames, violations);
                }
            }
        }

        assertThat(new TreeSet<>(violations))
                .as("Bulk/native DELETE statements that bypass soft delete (@SQLDelete).%n%s",
                        String.join("%n", violations))
                .isEmpty();
    }

    private void checkJpqlBulkDelete(Class<?> repo, Method method, String value,
                                     Map<String, Class<?>> entityNames, List<String> violations) {
        Matcher matcher = JPQL_BULK_DELETE.matcher(value);
        if (!matcher.find()) {
            return;
        }
        String target = matcher.group(1);
        Class<?> entityClass = entityNames.get(target);
        if (entityClass == null) {
            violations.add("%s#%s — bulk JPQL DELETE targets unknown entity name '%s' (fail-safe report)"
                    .formatted(repo.getSimpleName(), method.getName(), target));
        } else if (SoftDeleteAuditEntity.class.isAssignableFrom(entityClass)) {
            violations.add("%s#%s — bulk JPQL DELETE on soft-delete entity %s: hard row loss, bypasses @SQLDelete"
                    .formatted(repo.getSimpleName(), method.getName(), entityClass.getSimpleName()));
        }
    }

    private void checkNativeDelete(Class<?> repo, Method method, String value, List<String> violations) {
        Matcher matcher = NATIVE_DELETE_FROM.matcher(value);
        if (!matcher.find()) {
            return;
        }
        Class<?> repoEntity = resolveRepositoryEntity(repo);
        if (repoEntity == null || !SoftDeleteAuditEntity.class.isAssignableFrom(repoEntity)) {
            return;
        }
        String deletedTable = stripSchemaAndQuotes(matcher.group(1));
        String entityTable = tableName(repoEntity);
        if (entityTable != null && entityTable.equalsIgnoreCase(deletedTable)) {
            violations.add("%s#%s — native DELETE on soft-delete entity table %s: hard row loss, bypasses @SQLDelete"
                    .formatted(repo.getSimpleName(), method.getName(), entityTable));
        }
    }

    private static Map<String, Class<?>> scanEntityNames() throws Exception {
        Map<String, Class<?>> names = new HashMap<>();
        for (Class<?> clazz : scanClasses(ENTITY_PACKAGE)) {
            Entity annotation = clazz.getAnnotation(Entity.class);
            if (annotation == null) {
                continue;
            }
            String name = annotation.name().isEmpty() ? clazz.getSimpleName() : annotation.name();
            names.put(name, clazz);
        }
        return names;
    }

    private static Class<?> resolveRepositoryEntity(Class<?> repo) {
        return resolveRepositoryEntityRecursive(repo);
    }

    private static Class<?> resolveRepositoryEntityRecursive(Type type) {
        if (type instanceof ParameterizedType parameterized) {
            Class<?> raw = (Class<?>) parameterized.getRawType();
            Type[] args = parameterized.getActualTypeArguments();
            if (org.springframework.data.repository.Repository.class.isAssignableFrom(raw)
                    && args.length > 0 && args[0] instanceof Class<?> entity) {
                return entity;
            }
            for (Type iface : raw.getGenericInterfaces()) {
                Class<?> resolved = resolveRepositoryEntityRecursive(iface);
                if (resolved != null) {
                    return resolved;
                }
            }
        } else if (type instanceof Class<?> clazz) {
            for (Type iface : clazz.getGenericInterfaces()) {
                Class<?> resolved = resolveRepositoryEntityRecursive(iface);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        return null;
    }

    private static String tableName(Class<?> entity) {
        Table table = entity.getAnnotation(Table.class);
        return table == null || table.name().isEmpty() ? null : table.name();
    }

    private static String stripSchemaAndQuotes(String table) {
        String cleaned = table.replace("\"", "").replace("`", "");
        int lastDot = cleaned.lastIndexOf('.');
        return lastDot >= 0 ? cleaned.substring(lastDot + 1) : cleaned;
    }

    private static List<Class<?>> scanClasses(String packageName) throws Exception {
        String path = packageName.replace('.', '/');
        ClassLoader classLoader = SoftDeleteBulkDeleteScanTest.class.getClassLoader();
        List<Class<?>> classes = new ArrayList<>();
        Enumeration<java.net.URL> resources = classLoader.getResources(path);
        while (resources.hasMoreElements()) {
            URI uri = resources.nextElement().toURI();
            Path directory = Paths.get(uri);
            try (var files = Files.walk(directory)) {
                files.filter(file -> file.toString().endsWith(".class"))
                        .map(file -> toClassName(packageName, directory, file))
                        .forEach(name -> {
                            try {
                                classes.add(Class.forName(name, false, classLoader));
                            } catch (ClassNotFoundException e) {
                                throw new IllegalStateException("Cannot load scanned class " + name, e);
                            }
                        });
            }
        }
        return classes;
    }

    private static String toClassName(String packageName, Path directory, Path file) {
        String relative = directory.relativize(file).toString();
        return packageName + '.' + relative.replace(File.separatorChar, '.')
                .replaceAll("\\.class$", "");
    }
}
