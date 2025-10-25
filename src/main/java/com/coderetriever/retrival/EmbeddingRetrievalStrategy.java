package com.coderetriever.retrival;

import com.coderetriever.indexer.CodeIndex;
import com.coderetriever.llm.LLMClient;
import com.coderetriever.model.CodeElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Semantička retrieval strategija koja koristi vektorske embedinge
 */
public class EmbeddingRetrievalStrategy implements RetrievalStrategy {
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingRetrievalStrategy.class);
    private final LLMClient llmClient;

    /**
     * Konstruktor zahteva LLM klijenta da bi mogao da generiše embeding za upit
     */
    public EmbeddingRetrievalStrategy(LLMClient client) {
        this.llmClient = client;
    }

    @Override
    public String getName() {
        return "Embedding-Based (Semantic)";
    }

    @Override
    public List<CodeElement> retrieve(String query, CodeIndex index, int maxResults) {
        if(!llmClient.isAvailable()) {
            logger.warn("LLM client is not available. Cannot perform embedding-based retrieval.");
            return Collections.emptyList();
        }

        try {
            List<double[]> queryEmbeddings = llmClient.generateEmbeddings(List.of(query));
            if(queryEmbeddings.isEmpty() || queryEmbeddings.get(0).length == 0) {
                logger.warn("Could not generate embedding for query: {}", query);
                return Collections.emptyList();
            }

            double[] queryEmbedding = queryEmbeddings.get(0);

            Map<CodeElement, Double> scores = new HashMap<>();
            for(CodeElement element : index.getAllElements()) {
                double[] elementEmbedding = element.getEmbedding();

                if(elementEmbedding != null && elementEmbedding.length > 0) {
                    double similarity = cosineSimilarity(queryEmbedding, elementEmbedding);
                    scores.put(element, similarity);
                }
            }

            return scores.entrySet().stream()
                    .sorted(Map.Entry.<CodeElement, Double> comparingByValue().reversed())
                    .limit(maxResults)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }
        catch(Exception e) {
            logger.error("Error during semantic retrival", e);
            return Collections.emptyList();
        }
    }

    @Override
    public double calculateRelevanceScore(String query, CodeElement element) {
        return 0.0;
    }

    /**
     * Izračunava kosinusnu sličnost između dva vektora
     */
    private double cosineSimilarity(double[] vecA, double[] vecB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vecA.length; i++) {
            dotProduct += vecA[i] * vecB[i];
            normA += vecA[i] * vecA[i];
            normB += vecB[i] * vecB[i];
        }

        if (normA == 0 || normB == 0) {
            return 0.0; // Izbegavamo deljenje sa nulom
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
