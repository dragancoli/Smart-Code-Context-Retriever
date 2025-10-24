package com.coderetriever.indexer;

import com.coderetriever.model.CodeElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Indeks koji čuva sve code elemente i omogućava brzo pretraživanje
 */
public class CodeIndex {
    private static final Logger logger = LoggerFactory.getLogger(CodeIndex.class);
    
    private final List<CodeElement> allElements;
    private final Map<String, CodeElement> elementById;
    private final Map<String, List<CodeElement>> elementsByType;
    private final Map<String, List<CodeElement>> elementsByPackage;
    
    // Inverted index za keyword search
    private final Map<String, Set<String>> wordToElementIds;
    
    // Dependency graph
    private final Map<String, Set<String>> dependencyGraph;

    public CodeIndex(List<CodeElement> elements) {
        this.allElements = new ArrayList<>(elements);
        this.elementById = new HashMap<>();
        this.elementsByType = new HashMap<>();
        this.elementsByPackage = new HashMap<>();
        this.wordToElementIds = new HashMap<>();
        this.dependencyGraph = new HashMap<>();
        
        buildIndex();
        logger.info("Built index with {} elements", allElements.size());
    }

    private void buildIndex() {
        for (CodeElement element : allElements) {
            // ID index
            elementById.put(element.getId(), element);
            
            // Type index
            elementsByType
                .computeIfAbsent(element.getType().name(), k -> new ArrayList<>())
                .add(element);
            
            // Package index
            if (element.getPackageName() != null) {
                elementsByPackage
                    .computeIfAbsent(element.getPackageName(), k -> new ArrayList<>())
                    .add(element);
            }
            
            // Keyword index (inverted index)
            indexKeywords(element);
            
            // Dependency graph
            buildDependencyGraph(element);
        }
    }

    private void indexKeywords(CodeElement element) {
        Set<String> words = extractWords(element);
        for (String word : words) {
            wordToElementIds
                .computeIfAbsent(word.toLowerCase(), k -> new HashSet<>())
                .add(element.getId());
        }
    }

    private Set<String> extractWords(CodeElement element) {
        Set<String> words = new HashSet<>();
        
        // Iz imena
        words.addAll(splitCamelCase(element.getName()));
        
        // Iz signature
        if (element.getSignature() != null) {
            words.addAll(Arrays.asList(element.getSignature().split("\\W+")));
        }
        
        // Iz javadoc
        if (element.getJavadoc() != null) {
            words.addAll(Arrays.asList(element.getJavadoc().split("\\W+")));
        }
        
        // Iz sadržaja (ograničeno da ne bude previše)
        if (element.getContent() != null && element.getContent().length() < 1000) {
            words.addAll(Arrays.asList(element.getContent().split("\\W+")));
        }
        
        // Filtriraj prazne i male stringove
        return words.stream()
            .filter(w -> w.length() > 2)
            .collect(Collectors.toSet());
    }

    private List<String> splitCamelCase(String str) {
        List<String> words = new ArrayList<>();
        String[] parts = str.split("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
        for (String part : parts) {
            if (part.length() > 0) {
                words.add(part.toLowerCase());
            }
        }
        return words;
    }

    private void buildDependencyGraph(CodeElement element) {
        if (!element.getDependencies().isEmpty()) {
            dependencyGraph.put(element.getId(), new HashSet<>(element.getDependencies()));
        }
    }

    // === Query methods ===

    public List<CodeElement> getAllElements() {
        return new ArrayList<>(allElements);
    }

    public CodeElement getElementById(String id) {
        return elementById.get(id);
    }

    public List<CodeElement> getElementsByType(CodeElement.ElementType type) {
        return elementsByType.getOrDefault(type.name(), Collections.emptyList());
    }

    public List<CodeElement> getElementsByPackage(String packageName) {
        return elementsByPackage.getOrDefault(packageName, Collections.emptyList());
    }

    /**
     * Keyword search - vraća elemente koji sadrže date ključne riječi
     */
    public List<CodeElement> searchByKeywords(String query) {
        String[] keywords = query.toLowerCase().split("\\s+");
        Map<String, Integer> elementScores = new HashMap<>();
        
        for (String keyword : keywords) {
            Set<String> matchingIds = wordToElementIds.get(keyword);
            if (matchingIds != null) {
                for (String id : matchingIds) {
                    elementScores.merge(id, 1, Integer::sum);
                }
            }
        }
        
        // Sortiraj po broju matcheva
        return elementScores.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .map(e -> elementById.get(e.getKey()))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * Pronalazi sve elemente koji zavise od datog elementa
     */
    public Set<CodeElement> findDependents(String elementId) {
        Set<CodeElement> dependents = new HashSet<>();
        
        for (Map.Entry<String, Set<String>> entry : dependencyGraph.entrySet()) {
            if (entry.getValue().contains(elementId)) {
                CodeElement dependent = elementById.get(entry.getKey());
                if (dependent != null) {
                    dependents.add(dependent);
                }
            }
        }
        
        return dependents;
    }

    /**
     * Pronalazi sve elemente od kojih dati element zavisi
     */
    public Set<CodeElement> findDependencies(String elementId) {
        Set<String> deps = dependencyGraph.get(elementId);
        if (deps == null) return Collections.emptySet();
        
        return deps.stream()
            .map(elementById::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    /**
     * Pronalazi elemente u istom package-u
     */
    public List<CodeElement> findSiblings(CodeElement element) {
        if (element.getPackageName() == null) return Collections.emptyList();
        
        return elementsByPackage.getOrDefault(element.getPackageName(), Collections.emptyList())
            .stream()
            .filter(e -> !e.getId().equals(element.getId()))
            .collect(Collectors.toList());
    }

    public int size() {
        return allElements.size();
    }
}