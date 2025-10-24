package com.coderetriever.llm;

import com.coderetriever.model.CodeElement;

import java.util.List;

/**
 * Interface za komunikaciju sa LLM API-jima
 */
public interface LLMClient {
    
    /**
     * Šalje query sa kontekstom LLM-u i vraća odgovor
     * 
     * @param query Korisničko pitanje
     * @param context Lista relevantnih code elemenata
     * @return LLM odgovor
     */
    String queryWithContext(String query, List<CodeElement> context) throws Exception;
    
    /**
     * Provjerava da li je API konfigurisan i dostupan
     */
    boolean isAvailable();
    
    /**
     * Vraća ime LLM provider-a
     */
    String getProviderName();
}