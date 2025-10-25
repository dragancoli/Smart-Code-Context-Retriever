package com.coderetriever.llm;

import com.coderetriever.model.CodeElement;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import okhttp3.*; // Importovan
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException; // Importovan
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI API client implementacija
 */
public class OpenAIClient implements LLMClient {

    private static final Logger logger = LoggerFactory.getLogger(OpenAIClient.class);

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-3.5-turbo"; // ili "gpt-4" ako imaš access

    private static final String EMBEDDING_API_URL = "https://api.openai.com/v1/embeddings";
    private static final String EMBEDDING_MODEL = "text-embedding-ada-002";

    private final String apiKey;
    private final OkHttpClient httpClient;
    private final Gson gson;

    public OpenAIClient(String apiKey) {
        this.apiKey = apiKey;
        this.gson = new Gson();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String queryWithContext(String query, List<CodeElement> context) throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("OpenAI API key not configured");
        }

        // Konstruiši prompt sa kontekstom
        logger.info("Sending query to OpenAI (context size: {} elements)", context.size());

        // Kreiraj request body
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", MODEL);
        requestBody.addProperty("temperature", 0.7);

        // --- POČETAK DODATOG KODA ---

        // Sastavi kontekst string
        StringBuilder contextString = new StringBuilder();
        contextString.append("Here is the relevant code context from the project:\n\n");
        for (CodeElement element : context) {
            contextString.append(element.toContextString());
            contextString.append("--------------------\n");
        }

        // Sastavi poruke
        JsonArray messages = new JsonArray();

        // 1. System message (definiše ulogu AI)
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", "You are an expert Java programming assistant. Your task is to answer questions about a user's codebase. Use the provided code context to give a precise and helpful answer.");
        messages.add(systemMessage);

        // 2. User message (sadrži kontekst i korisnički query)
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", contextString.toString() + "\n\nUser Query: " + query);
        messages.add(userMessage);

        // Dodaj poruke u request body
        requestBody.add("messages", messages);

        // Kreiraj HTTP request
        String jsonBody = gson.toJson(requestBody);
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        // Izvrši poziv
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                logger.error("OpenAI API request failed: {} - {}", response.code(), errorBody);
                throw new IOException("Unexpected code " + response.code() + " - " + errorBody);
            }

            String responseBody = response.body().string();

            // Parsiraj odgovor
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            JsonArray choices = jsonResponse.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) {
                throw new IOException("Invalid response from OpenAI: No 'choices' found.");
            }

            JsonObject firstChoice = choices.get(0).getAsJsonObject();
            JsonObject message = firstChoice.getAsJsonObject("message");
            if (message == null || !message.has("content")) {
                throw new IOException("Invalid response from OpenAI: No 'content' in message.");
            }

            // Vrati odgovor
            return message.get("content").getAsString();

        } catch (IOException e) {
            logger.error("Error during OpenAI API call", e);
            throw new Exception("Failed to communicate with OpenAI API", e);
        }
        // --- KRAJ DODATOG KODA ---
    }

    /**
     * Provjerava da li je API konfigurisan i dostupan
     */
    @Override
    public boolean isAvailable() {
        // --- DODAT KOD ---
        return this.apiKey != null && !this.apiKey.isEmpty();
    }

    /**
     * Vraća ime LLM provider-a
     */
    @Override
    public String getProviderName() {
        // --- DODAT KOD ---
        return "OpenAI (" + MODEL + ")";
    }

    /**
     * Pretvara listu stringova u listu embeding vektora
     * * @param texts Lista tekstova (npr. sadržaj metoda)
     *
     * @return Lista embeding (double[]) vektora
     * @throws Exception Ako dođe do greške
     */
    @Override
    public List<double[]> generateEmbeddings(List<String> texts) throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("OpenAI API key not configured");
        }

        logger.info("Generating {} embeddings using model {}", texts.size(), EMBEDDING_MODEL);

        // OpenAI prima listu tekstova direktno
        // Struktura: {"input": ["text1", "text2", ...], "model": "..."}

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", EMBEDDING_MODEL);

        JsonArray inputArray = new JsonArray();
        for (String text : texts) {
            inputArray.add(text);
        }
        requestBody.add("input", inputArray);

        // Kreiraj HTTP request
        String jsonBody = gson.toJson(requestBody);
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(EMBEDDING_API_URL) // Koristi URL za embedinge
                .header("Authorization", "Bearer " + this.apiKey)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "No response body";

            if (!response.isSuccessful()) {
                logger.error("OpenAI Embedding API request failed: {} - {}", response.code(), responseBody);
                throw new IOException("Unexpected code " + response.code() + " - " + responseBody);
            }

            // Parsiraj odgovor
            // Struktura: {"data": [{"embedding": [0.1, 0.2, ...]}, ...], ...}
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            JsonArray dataArray = jsonResponse.getAsJsonArray("data");

            List<double[]> embeddingsList = new ArrayList<>();
            for (int i = 0; i < dataArray.size(); i++) {
                JsonObject embeddingData = dataArray.get(i).getAsJsonObject();
                JsonArray valuesArray = embeddingData.getAsJsonArray("embedding");

                double[] embedding = new double[valuesArray.size()];
                for (int j = 0; j < valuesArray.size(); j++) {
                    embedding[j] = valuesArray.get(j).getAsDouble();
                }
                embeddingsList.add(embedding);
            }

            return embeddingsList;

        } catch (IOException e) {
            logger.error("Error during OpenAI Embedding API call", e);
            throw new Exception("Failed to communicate with OpenAI Embedding API", e);
        }
    }
}