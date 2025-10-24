package com.coderetriever.llm;

import com.coderetriever.model.CodeElement;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Google Gemini API klijent implementacija
 */
public class GeminiClient implements LLMClient {

    private static final Logger logger = LoggerFactory.getLogger(GeminiClient.class);
    
    // Model koji ćemo koristiti
    private static final String MODEL = "gemini-pro-latest";
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent";
    
    private final String apiKey;
    private final OkHttpClient httpClient;
    private final Gson gson;

    public GeminiClient(String apiKey) {
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
            throw new IllegalStateException("Google API key not configured");
        }

        logger.info("Sending query to Google Gemini (context size: {} elements)", context.size());

        // Konstruiši prompt. Gemini nema "system" ulogu kao OpenAI,
        // pa ćemo sve spojiti u jedan veliki user prompt.
        String prompt = buildPrompt(query, context);

        // Kreiraj Gemini request body
        // Struktura je: {"contents": [{"parts": [{"text": "..."}]}]}
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", prompt);

        JsonArray partsArray = new JsonArray();
        partsArray.add(textPart);

        JsonObject content = new JsonObject();
        content.add("parts", partsArray);

        JsonArray contentsArray = new JsonArray();
        contentsArray.add(content);

        JsonObject requestBody = new JsonObject();
        requestBody.add("contents", contentsArray);
        
        // Dodaj konfiguraciju (opciono, ali korisno)
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.7);
        generationConfig.addProperty("maxOutputTokens", 2048);
        requestBody.add("generationConfig", generationConfig);


        // Kreiraj HTTP request
        String jsonBody = gson.toJson(requestBody);
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-t"));

        // URL mora da sadrži API ključ kao query parametar
        HttpUrl.Builder urlBuilder = HttpUrl.parse(API_URL).newBuilder();
        urlBuilder.addQueryParameter("key", this.apiKey);
        
        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .post(body)
            .build();

        // Izvrši poziv
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "No response body";

            if (!response.isSuccessful()) {
                logger.error("Gemini API request failed: {} - {}", response.code(), responseBody);
                throw new IOException("Unexpected code " + response.code() + " - " + responseBody);
            }

            // Parsiraj odgovor
            // Struktura je: {"candidates": [{"content": {"parts": [{"text": "..."}]}}]}
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            
            if (!jsonResponse.has("candidates")) {
                 logger.error("Invalid Gemini response: {}", responseBody);
                 throw new IOException("Invalid response from Gemini: No 'candidates' found.");
            }

            JsonArray candidates = jsonResponse.getAsJsonArray("candidates");
            if (candidates == null || candidates.size() == 0) {
                throw new IOException("Invalid response from Gemini: 'candidates' array is empty.");
            }

            JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
            JsonObject contentResponse = firstCandidate.getAsJsonObject("content");
            JsonArray partsResponse = contentResponse.getAsJsonArray("parts");
            JsonObject firstPart = partsResponse.get(0).getAsJsonObject();
            
            if (!firstPart.has("text")) {
                 throw new IOException("Invalid response from Gemini: No 'text' in response part.");
            }

            return firstPart.get("text").getAsString();

        } catch (IOException e) {
            logger.error("Error during Gemini API call", e);
            throw new Exception("Failed to communicate with Gemini API", e);
        }
    }

    /**
     * Pomoćna metoda za kreiranje kompletnog prompta
     */
    private String buildPrompt(String query, List<CodeElement> context) {
        StringBuilder prompt = new StringBuilder();
        
        // 1. Definiši zadatak (slično system poruci)
        prompt.append("You are an expert Java programming assistant.\n");
        prompt.append("Your task is to answer questions about a user's codebase.\n");
        prompt.append("Use the provided code context to give a precise and helpful answer.\n\n");
        
        // 2. Dodaj kontekst
        prompt.append("=== Relevant Code Context ===\n\n");
        if (context.isEmpty()) {
            prompt.append("No relevant code context was found.\n");
        } else {
            for (CodeElement element : context) {
                prompt.append(element.toContextString());
                prompt.append("--------------------\n");
            }
        }
        
        // 3. Dodaj korisnički upit
        prompt.append("\n=== User Query ===\n");
        prompt.append(query);
        
        return prompt.toString();
    }


    @Override
    public boolean isAvailable() {
        return this.apiKey != null && !this.apiKey.isEmpty();
    }

    @Override
    public String getProviderName() {
        return "Google (Gemini Pro)";
    }
}