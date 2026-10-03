package org.fhirlint.core.rules.custom;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Loads and validates human-authored YAML custom rules and compiles FHIRPath expressions into in-memory ASTs.
 */
public final class CustomRuleLoader {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final FhirContext FHIR_CONTEXT = FhirContext.forR4Cached();

    private CustomRuleLoader() {}

    /**
     * Loads rules from one or more paths (file, directory, or comma-separated string).
     */
    public static List<FhirPathQualityRule> loadRules(String... paths) {
        if (paths == null || paths.length == 0) {
            return Collections.emptyList();
        }
        List<String> pathList = new ArrayList<>();
        for (String p : paths) {
            if (p != null && !p.isBlank()) {
                String[] parts = p.split(",");
                for (String part : parts) {
                    if (!part.trim().isBlank()) {
                        pathList.add(part.trim());
                    }
                }
            }
        }
        return loadRules(pathList);
    }

    /**
     * Loads rules from a list of path strings.
     */
    public static List<FhirPathQualityRule> loadRules(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return Collections.emptyList();
        }

        List<File> filesToParse = new ArrayList<>();
        for (String pathStr : paths) {
            File f = new File(pathStr);
            if (!f.exists()) {
                throw new CustomRuleException("Custom rules file or directory not found: " + pathStr);
            }
            if (f.isDirectory()) {
                try (var stream = Files.walk(f.toPath())) {
                    List<File> dirFiles = stream
                            .filter(Files::isRegularFile)
                            .filter(p -> {
                                String name = p.getFileName().toString().toLowerCase();
                                return name.endsWith(".yaml") || name.endsWith(".yml");
                            })
                            .map(Path::toFile)
                            .sorted()
                            .toList();
                    filesToParse.addAll(dirFiles);
                } catch (IOException e) {
                    throw new CustomRuleException("Failed scanning directory for custom rules: " + f.getAbsolutePath() + ": " + e.getMessage(), e);
                }
            } else {
                filesToParse.add(f);
            }
        }

        List<FhirPathQualityRule> compiledRules = new ArrayList<>();
        Set<String> seenRuleIds = new HashSet<>();

        for (File ruleFile : filesToParse) {
            List<CustomRuleDefinition> definitions = parseDefinitionsFromFile(ruleFile);
            for (CustomRuleDefinition def : definitions) {
                if (!seenRuleIds.add(def.id())) {
                    throw new CustomRuleException("Duplicate custom rule ID discovered: '" + def.id() + "' in " + ruleFile.getName());
                }
                compiledRules.add(compileRule(def));
            }
        }

        return Collections.unmodifiableList(compiledRules);
    }

    /**
     * Loads rules from raw YAML content string.
     */
    public static List<FhirPathQualityRule> loadRulesFromString(String yamlContent) {
        if (yamlContent == null || yamlContent.isBlank()) {
            return Collections.emptyList();
        }
        try {
            CustomRulesFile file = YAML_MAPPER.readValue(yamlContent, CustomRulesFile.class);
            List<FhirPathQualityRule> rules = new ArrayList<>();
            for (CustomRuleDefinition def : file.rules()) {
                rules.add(compileRule(def));
            }
            return Collections.unmodifiableList(rules);
        } catch (Exception e) {
            throw new CustomRuleException("Failed parsing custom rules YAML: " + e.getMessage(), e);
        }
    }

    private static List<CustomRuleDefinition> parseDefinitionsFromFile(File file) {
        try {
            CustomRulesFile customFile = YAML_MAPPER.readValue(file, CustomRulesFile.class);
            return customFile.rules();
        } catch (Exception e) {
            throw new CustomRuleException("Failed parsing custom rules YAML file '" + file.getAbsolutePath() + "': " + e.getMessage(), e);
        }
    }

    private static FhirPathQualityRule compileRule(CustomRuleDefinition def) {
        IFhirPath fhirPath = FHIR_CONTEXT.newFhirPath();
        try {
            IFhirPath.IParsedExpression parsed = fhirPath.parse(def.fhirpath());
            return new FhirPathQualityRule(def, parsed, fhirPath);
        } catch (Exception e) {
            throw new CustomRuleException("Invalid FHIRPath invariant expression in rule '" + def.id() + "': '" + def.fhirpath() + "': " + e.getMessage(), e);
        }
    }
}
