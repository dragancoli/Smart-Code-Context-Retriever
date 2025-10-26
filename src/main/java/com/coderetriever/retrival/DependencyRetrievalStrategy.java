package com.coderetriever.retrival;

import com.coderetriever.indexer.CodeIndex;
import com.coderetriever.model.CodeElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Dependency-based retrieval strategija
 * Pronalazi kod koji je povezan kroz dependency relationships
 */
public class DependencyRetrievalStrategy implements RetrievalStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(DependencyRetrievalStrategy.class);

    @Override
    public String getName() {
        return "Dependency-Based";
    }

    @Override
    public List<CodeElement> retrieve(String query, CodeIndex index, int maxResults) {
        List<CodeElement> seedElements = findSeedElements(query, index);
        
        if (seedElements.isEmpty()) {
            logger.warn("No seed elements found for query: {}", query);
            return Collections.emptyList();
        }

        Set<CodeElement> relatedElements = expandThroughDependencies(seedElements, index);

        Map<CodeElement, Double> scores = scoreByProximity(seedElements, relatedElements);
        
        return scores.entrySet().stream()
            .sorted(Map.Entry.<CodeElement, Double>comparingByValue().reversed())
            .limit(maxResults)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    private List<CodeElement> findSeedElements(String query, CodeIndex index) {
        List<CodeElement> results = index.searchByKeywords(query);
        return results.stream().limit(3).collect(Collectors.toList());
    }

    private Set<CodeElement> expandThroughDependencies(List<CodeElement> seeds, CodeIndex index) {
        Set<CodeElement> expanded = new HashSet<>(seeds);
        Queue<CodeElement> toProcess = new LinkedList<>(seeds);
        Set<String> visited = new HashSet<>();
        
        int maxDepth = 2;
        Map<String, Integer> depthMap = new HashMap<>();
        
        for (CodeElement seed : seeds) {
            depthMap.put(seed.getId(), 0);
        }
        
        while (!toProcess.isEmpty()) {
            CodeElement current = toProcess.poll();
            String currentId = current.getId();
            
            if (visited.contains(currentId)) continue;
            visited.add(currentId);
            
            int currentDepth = depthMap.get(currentId);
            if (currentDepth >= maxDepth) continue;

            Set<CodeElement> dependencies = index.findDependencies(currentId);
            for (CodeElement dep : dependencies) {
                if (!visited.contains(dep.getId())) {
                    expanded.add(dep);
                    depthMap.put(dep.getId(), currentDepth + 1);
                    toProcess.offer(dep);
                }
            }

            Set<CodeElement> dependents = index.findDependents(currentId);
            for (CodeElement dependent : dependents) {
                if (!visited.contains(dependent.getId())) {
                    expanded.add(dependent);
                    depthMap.put(dependent.getId(), currentDepth + 1);
                    toProcess.offer(dependent);
                }
            }

            List<CodeElement> siblings = index.findSiblings(current);
            for (CodeElement sibling : siblings.stream().limit(3).toList()) {
                if (!visited.contains(sibling.getId())) {
                    expanded.add(sibling);
                    depthMap.put(sibling.getId(), currentDepth + 1);
                }
            }
        }
        
        logger.info("Expanded from {} seeds to {} related elements", seeds.size(), expanded.size());
        return expanded;
    }

    private Map<CodeElement, Double> scoreByProximity(List<CodeElement> seeds, 
                                                      Set<CodeElement> related) {
        Map<CodeElement, Double> scores = new HashMap<>();
        
        for (CodeElement element : related) {
            double score = 0.0;

            if (seeds.contains(element)) {
                score = 10.0;
            } else {
                for (CodeElement seed : seeds) {
                    if (areDirectlyConnected(element, seed)) {
                        score += 5.0;
                    } else {
                        score += 2.0;
                    }
                }
            }

            if (element.getType() == CodeElement.ElementType.CLASS) {
                score *= 1.3;
            } else if (element.getType() == CodeElement.ElementType.METHOD) {
                score *= 1.2;
            }
            
            scores.put(element, score);
        }
        
        return scores;
    }

    private boolean areDirectlyConnected(CodeElement a, CodeElement b) {
        return a.getDependencies().contains(b.getName()) ||
               b.getDependencies().contains(a.getName()) ||
               (a.getPackageName() != null && 
                a.getPackageName().equals(b.getPackageName()));
    }

    @Override
    public double calculateRelevanceScore(String query, CodeElement element) {
        return element.getDependencies().isEmpty() ? 0.3 : 0.7;
    }
}