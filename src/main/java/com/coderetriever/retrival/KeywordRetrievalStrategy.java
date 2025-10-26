package com.coderetriever.retrival;

import com.coderetriever.indexer.CodeIndex;
import com.coderetriever.model.CodeElement;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Keyword-based retrieval strategija
 * Kombinuje exact matching, partial matching i string similarity
 */
public class KeywordRetrievalStrategy implements RetrievalStrategy {
    
    private final JaroWinklerSimilarity similarity = new JaroWinklerSimilarity();

    @Override
    public String getName() {
        return "Keyword-Based";
    }

    @Override
    public List<CodeElement> retrieve(String query, CodeIndex index, int maxResults) {
        Map<CodeElement, Double> scores = new HashMap<>();
        
        String normalizedQuery = query.toLowerCase().trim();
        String[] queryTerms = normalizedQuery.split("\\s+");

        for (CodeElement element : index.getAllElements()) {
            double score = calculateScore(queryTerms, element);
            if (score > 0) {
                scores.put(element, score);
            }
        }

        return scores.entrySet().stream()
            .sorted(Map.Entry.<CodeElement, Double>comparingByValue().reversed())
            .limit(maxResults)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    private double calculateScore(String[] queryTerms, CodeElement element) {
        double score = 0.0;
        
        String elementName = element.getName().toLowerCase();
        String elementContent = getSearchableContent(element).toLowerCase();
        
        for (String term : queryTerms) {
            if (elementName.equals(term)) {
                score += 10.0;
            } else if (elementName.contains(term)) {
                score += 5.0;
            }

            double nameSim = similarity.apply(term, elementName);
            if (nameSim > 0.8) {
                score += nameSim * 3.0;
            }

            if (elementContent.contains(term)) {
                int firstOccurrence = elementContent.indexOf(term);
                double positionFactor = 1.0 - (firstOccurrence / (double) elementContent.length());
                score += 2.0 * (1.0 + positionFactor);
            }

            if (element.getType() == CodeElement.ElementType.CLASS) {
                score *= 1.2; // Klase su obično važnije
            } else if (element.getType() == CodeElement.ElementType.METHOD) {
                score *= 1.1;
            }
        }

        if (element.getJavadoc() != null && !element.getJavadoc().isEmpty()) {
            score *= 1.1;
        }
        
        return score;
    }

    @Override
    public double calculateRelevanceScore(String query, CodeElement element) {
        String[] terms = query.toLowerCase().split("\\s+");
        return calculateScore(terms, element) / 10.0; // Normalize
    }

    private String getSearchableContent(CodeElement element) {
        StringBuilder sb = new StringBuilder();
        sb.append(element.getName()).append(" ");
        
        if (element.getSignature() != null) {
            sb.append(element.getSignature()).append(" ");
        }
        
        if (element.getJavadoc() != null) {
            sb.append(element.getJavadoc()).append(" ");
        }

        if (element.getContent() != null) {
            String content = element.getContent();
            sb.append(content.substring(0, Math.min(500, content.length())));
        }
        
        return sb.toString();
    }
}