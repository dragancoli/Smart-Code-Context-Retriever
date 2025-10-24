package com.coderetriever.retrival;

import com.coderetriever.indexer.CodeIndex;
import com.coderetriever.model.CodeElement;

import java.util.List;

/**
 * Interface za različite retrieval strategije
 */
public interface RetrievalStrategy {
    
    /**
     * Naziv strategije
     */
    String getName();
    
    /**
     * Vraća listu relevantnoh code elemenata za dati query
     * 
     * @param query Korisnički upit
     * @param index Index sa svim elementima koda
     * @param maxResults Maksimalan broj rezultata
     * @return Lista relevantnih code elemenata, sortirana po relevantnosti
     */
    List<CodeElement> retrieve(String query, CodeIndex index, int maxResults);
    
    /**
     * Vraća score za dato matching (0.0 do 1.0)
     */
    default double calculateRelevanceScore(String query, CodeElement element) {
        return 0.0;
    }
}