package com.coderetriever.retrival;

import com.coderetriever.indexer.CodeIndex;
import com.coderetriever.model.CodeElement;
import com.coderetriever.llm.LLMClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Hybrid strategija koja kombinuje keyword, dependency i embedding pristupe
 * Koristi weighted scoring za najbolje rezultate
 */
public class HybridRetrievalStrategy implements RetrievalStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(HybridRetrievalStrategy.class);
    
    private final KeywordRetrievalStrategy keywordStrategy;
    private final DependencyRetrievalStrategy dependencyStrategy;
    private final EmbeddingRetrievalStrategy embeddingStrategy;
    private final boolean useEmbeddings;
    
    // Težine za različite strategije
    private final double keywordWeight = 0.3;
    private final double dependencyWeight = 0.2;
    private final double embeddingWeight = 0.5;

    public HybridRetrievalStrategy(LLMClient llmClient, boolean useEmbeddings) {
        this.keywordStrategy = new KeywordRetrievalStrategy();
        this.dependencyStrategy = new DependencyRetrievalStrategy();
        this.embeddingStrategy = new EmbeddingRetrievalStrategy(llmClient);
        this.useEmbeddings = useEmbeddings;
    }

    @Override
    public String getName() {
        return !useEmbeddings ? "Hybrid (Keyword + Dependency)" : "Hybrid (Keyword + Dependency + Semantic)";
    }

    @Override
    public List<CodeElement> retrieve(String query, CodeIndex index, int maxResults) {
        // Dobavi rezultate iz obe strategije
        List<CodeElement> keywordResults = keywordStrategy.retrieve(query, index, maxResults * 2);
        List<CodeElement> dependencyResults = dependencyStrategy.retrieve(query, index, maxResults * 2);

        logger.info("Keyword strategy found {} results", keywordResults.size());
        logger.info("Dependency strategy found {} results", dependencyResults.size());
        
        // Kombinuj rezultate sa ponderisanim scorovima
        Map<CodeElement, Double> combinedScores = new HashMap<>();

        // Dodaj rezultate iz svake strategije sa odgovarajućom težinom
        addResultsToScores(combinedScores, keywordResults, keywordWeight);
        addResultsToScores(combinedScores, dependencyResults, dependencyWeight);

        if (useEmbeddings) {
            List<CodeElement> embeddingResults = embeddingStrategy.retrieve(query, index, maxResults * 2);
            logger.info("Embedding strategy found {} results", embeddingResults.size());
            addResultsToScores(combinedScores, embeddingResults, embeddingWeight);
        }

        // Primjeni dodatne heuristike
        applyContextualBoosts(query, combinedScores, index);
        
        // Sortiraj i vrati top rezultate
        List<CodeElement> results = combinedScores.entrySet().stream()
            .sorted(Map.Entry.<CodeElement, Double>comparingByValue().reversed())
            .limit(maxResults)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        logger.info("Hybrid strategy returning {} results", results.size());
        return results;
    }

    /**
     * Primjenjuje kontekstualne boostove na osnovu query karakteristika
     */
    private void applyContextualBoosts(String query, Map<CodeElement, Double> scores, 
                                      CodeIndex index) {
        String lowerQuery = query.toLowerCase();
        
        for (Map.Entry<CodeElement, Double> entry : scores.entrySet()) {
            CodeElement element = entry.getKey();
            double boost = 1.0;
            
            // Boost za query patterns
            if (lowerQuery.contains("how") || lowerQuery.contains("explain")) {
                // Za objašnjenja, preferiraj klase i metode sa javadoc-om
                if (element.getJavadoc() != null && !element.getJavadoc().isEmpty()) {
                    boost *= 1.5;
                }
                if (element.getType() == CodeElement.ElementType.CLASS) {
                    boost *= 1.3;
                }
            }
            
            if (lowerQuery.contains("implement") || lowerQuery.contains("add")) {
                // Za implementacije, preferiraj metode
                if (element.getType() == CodeElement.ElementType.METHOD) {
                    boost *= 1.4;
                }
            }
            
            if (lowerQuery.contains("bug") || lowerQuery.contains("error") || 
                lowerQuery.contains("fix")) {
                // Za bugove, preferiraj metode sa exception handling-om
                if (element.getContent() != null && 
                    (element.getContent().contains("catch") || 
                     element.getContent().contains("throw"))) {
                    boost *= 1.3;
                }
            }
            
            // Boost za kompleksne elemente (više dependencies = važniji)
            if (element.getDependencies().size() > 3) {
                boost *= 1.2;
            }
            
            // Boost za elemente koji su često referencirani
            Set<CodeElement> dependents = index.findDependents(element.getId());
            if (dependents.size() > 2) {
                boost *= (1.0 + Math.log(dependents.size()) * 0.1);
            }
            
            entry.setValue(entry.getValue() * boost);
        }
    }

    /**
     * Dodaje rezultate u mapu skorova sa odgovarajućom težinom
     */
    public void addResultsToScores(Map<CodeElement, Double> combinedScores, List<CodeElement> results, double weight) {
        for (int i = 0; i < results.size(); i++) {
            CodeElement element = results.get(i);
            double positionScore = 1.0 - (i / (double) results.size());
            double weightedScore = positionScore * weight;
            combinedScores.merge(element, weightedScore, Double::sum);
        }
    }

    @Override
    public double calculateRelevanceScore(String query, CodeElement element) {
        double keywordScore = keywordStrategy.calculateRelevanceScore(query, element);
        double depScore = dependencyStrategy.calculateRelevanceScore(query, element);
        
        return keywordScore * keywordWeight + depScore * dependencyWeight;
    }
}