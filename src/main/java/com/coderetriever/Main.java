package com.coderetriever;

import com.coderetriever.model.CodeElement;
import com.coderetriever.parser.JavaCodeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.coderetriever.indexer.CodeIndex;
import com.coderetriever.llm.LLMClient;
import com.coderetriever.llm.GeminiClient;
import com.coderetriever.retrival.HybridRetrievalStrategy;
import com.coderetriever.retrival.RetrievalStrategy;
import java.util.List;
import java.util.Scanner;

/**
 * Main entry point za Smart Code Retriever
 * (Ažurirana verzija sa Index-om, Retrieval-om i LLM integracijom)
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static CodeIndex codeIndex;
    private static RetrievalStrategy retrievalStrategy;
    private static LLMClient llmClient;
    private static List<CodeElement> lastSearchResults;

    public static void main(String[] args) {
        logger.info("Starting Smart Code Retriever...");

        if (args.length < 1) {
            System.out.println("Usage: java -jar smart-code-retriever.jar <project-directory>");
            System.out.println("\nExample: java -jar smart-code-retriever.jar /path/to/java/project");
            return;
        }

        String projectPath = args[0];
        logger.info("Analyzing project at: {}", projectPath);

        System.out.println("\n[1/4] Parsing Java files...");
        JavaCodeParser parser = new JavaCodeParser();
        List<CodeElement> codeElements = parser.parseDirectory(projectPath);

        if (codeElements.isEmpty()) {
            logger.warn("No code elements found. Exiting.");
            System.out.println("! No Java files found or parsed in directory: " + projectPath);
            return;
        }

        System.out.println("✓ Found " + codeElements.size() + " code elements");
        printStatistics(codeElements);

        System.out.println("Do you with to use Embeddings for semantic search? (yes/no): ");
        Scanner scanner = new Scanner(System.in);
        String useEmbeddings = scanner.nextLine().trim().toLowerCase();
        boolean enableEmbeddings = useEmbeddings.equals("yes") || useEmbeddings.equals("y");
        if (enableEmbeddings) {
            System.out.println("✓ Embeddings will be used for semantic search.");
        } else {
            System.out.println("✓ Embeddings will NOT be used. Search will be based on keyword matching.");
        }
        System.out.println("\n[2/4] Building code index...");
        codeIndex = new CodeIndex(codeElements);
        System.out.println("✓ Index built successfully (" + codeIndex.size() + " elements).");

        if(!enableEmbeddings) {
            System.out.println("Skipping embedding generation as per user choice.");
        } else {
            System.out.println("\n[2b/4] Generating code embeddings...");
        }

        System.out.println("\n[3/4] Initializing services...");

        String googleApiKey = System.getenv("GOOGLE_API_KEY");
        llmClient = new GeminiClient(googleApiKey);

        if (!llmClient.isAvailable()) {
            logger.warn("GOOGLE_API_KEY environment variable not set. 'ask' command will be disabled.");
            System.out.println("! UPOZORENJE: GOOGLE_API_KEY nije postavljen. 'ask' komanda neće raditi.");
            System.out.println("! Postavite ključ (npr. 'export GOOGLE_API_KEY=...') i ponovo pokrenite.");
        } else {
            logger.info("LLM Client initialized: {}", llmClient.getProviderName());
            System.out.println("✓ LLM Client spreman: " + llmClient.getProviderName());
        }

        try {
            if(enableEmbeddings)
                generateEmbeddingsForIndex(codeIndex, llmClient);
        } catch (Exception e) {
            logger.error("Failed to generate embeddings", e);
            System.out.println("! Greška pri generisanju embedinga: " + e.getMessage());
        }

        retrievalStrategy = new HybridRetrievalStrategy(llmClient, enableEmbeddings);
        logger.info("Using retrieval strategy: {}", retrievalStrategy.getName());

        System.out.println("\n[4/4] Starting Interactive Mode...");
        System.out.println("Dostupne komande:");
        System.out.println("  search <upit>   - Pronalazi relevantan kod (Strategija: " + retrievalStrategy.getName() + ")");
        System.out.println("  ask <pitanje>   - Postavlja pitanje LLM-u o kodu (RAG)");
        System.out.println("  show <broj>     - Prikazuje detalje rezultata poslednje 'search' pretrage");
        System.out.println("  list            - Izlistava prvih 50 elemenata koda");
        System.out.println("  quit            - Izlaz\n");

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("quit")) {
                break;
            }

            if (input.startsWith("search ")) {
                String query = input.substring(7);
                if (query.isBlank()) {
                    System.out.println("Unesite pojam za pretragu.");
                } else {
                    searchWithStrategy(query);
                }
            } else if (input.startsWith("ask ")) {
                String query = input.substring(4);
                if (query.isBlank()) {
                    System.out.println("Unesite pitanje za LLM.");
                } else {
                    askLlm(query);
                }
            } else if (input.startsWith("show ")) {
                try {
                    int index = Integer.parseInt(input.substring(5));
                    showCodeElement(index);
                } catch (NumberFormatException e) {
                    System.out.println("Nevažeći format broja. Koristite 'show <broj>'.");
                }
            } else if (input.equals("list")) {
                listCodeElements(codeIndex.getAllElements());
            } else if (input.isEmpty()) {
                // Ignore empty input
            } else {
                System.out.println("Nepoznata komanda. Dostupne komande: search, ask, show, list, quit");
            }
        }

        scanner.close();
        System.out.println("\nGoodbye!");
    }

    private static void printStatistics(List<CodeElement> elements) {
        long classes = elements.stream()
                .filter(e -> e.getType() == CodeElement.ElementType.CLASS)
                .count();
        long methods = elements.stream()
                .filter(e -> e.getType() == CodeElement.ElementType.METHOD)
                .count();
        long fields = elements.stream()
                .filter(e -> e.getType() == CodeElement.ElementType.FIELD)
                .count();
        long interfaces = elements.stream()
                .filter(e -> e.getType() == CodeElement.ElementType.INTERFACE)
                .count();

        System.out.println("\n=== Project Statistics ===");
        System.out.println("Classes:    " + classes);
        System.out.println("Interfaces: " + interfaces);
        System.out.println("Methods:    " + methods);
        System.out.println("Fields:     " + fields);
        System.out.println("Total:      " + elements.size());
    }

    /**
     * NOVA metoda - koristi RetrievalStrategy i CodeIndex za pretragu
     */
    private static void searchWithStrategy(String query) {
        System.out.println("\nPretraga (strategija: " + retrievalStrategy.getName() + ") za: \"" + query + "\"");
        logger.info("Executing search with query: {}", query);

        List<CodeElement> results = retrievalStrategy.retrieve(query, codeIndex, 10);

        lastSearchResults = results;

        if (results.isEmpty()) {
            System.out.println("Nema rezultata.");
            return;
        }

        System.out.println("Pronađeno " + results.size() + " relevantnih rezultata:\n");
        for (int i = 0; i < results.size(); i++) {
            CodeElement element = results.get(i);
            System.out.printf("[%d] %s - %s:%d\n",
                    i, element, element.getFilePath(), element.getStartLine());
        }
        System.out.println("\nKoristite 'show <broj>' za prikaz detalja");
    }

    /**
     * NOVA metoda - implementira RAG pipeline za 'ask' komandu
     */
    private static void askLlm(String query) {
        if (!llmClient.isAvailable()) {
            System.out.println("Greška: LLM klijent nije konfigurisan. Postavite OPENAI_API_KEY.");
            return;
        }

        System.out.println("\nRazmišljam... (Upit: " + query + ")");

        System.out.println("1. Pribavljam kontekst iz koda...");
        logger.info("RAG: Retrieving context for query: {}", query);

        List<CodeElement> context = retrievalStrategy.retrieve(query, codeIndex, 5);

        if (context.isEmpty()) {
            System.out.println("Upozorenje: Nisam uspeo da pronađem relevantan kontekst u kodu. Pitati LLM bez njega...");
            logger.warn("RAG: No context found for query.");
        } else {
            System.out.println("✓ Pronađeno " + context.size() + " relevantnih elemenata koda za kontekst.");
            if (logger.isDebugEnabled()) {
                context.forEach(el -> logger.debug("RAG: Context element: {}", el.getId()));
            }
        }

        try {
            System.out.println("2. Šaljem upit i kontekst na " + llmClient.getProviderName() + "...");
            String answer = llmClient.queryWithContext(query, context);

            System.out.println("\n=== Odgovor Pomoćnika ===\n");
            System.out.println(answer);
            System.out.println("\n=========================\n");

        } catch (Exception e) {
            logger.error("Error during LLM query execution", e);
            System.out.println("\nGreška pri komunikaciji sa LLM servisom: " + e.getMessage());
        }
    }


    /**
     * MODIFIKOVANA metoda - koristi 'lastSearchResults' listu
     */
    private static void showCodeElement(int index) {
        if (lastSearchResults == null) {
            System.out.println("Morate prvo pokrenuti 'search' komandu.");
            return;
        }

        if (index < 0 || index >= lastSearchResults.size()) {
            System.out.println("Nevažeći indeks. Indeks mora biti između 0 i " + (lastSearchResults.size() - 1));
            return;
        }

        CodeElement element = lastSearchResults.get(index);
        System.out.println("\n" + "=".repeat(80));
        System.out.println("Element: " + element.getName() + " (" + element.getId() + ")");
        System.out.println("Tip: " + element.getType());
        System.out.println("Fajl: " + element.getFilePath());
        System.out.println("Linije: " + element.getStartLine() + "-" + element.getEndLine());
        System.out.println("Paket: " + (element.getPackageName() != null ? element.getPackageName() : "n/a"));

        if (element.getJavadoc() != null && !element.getJavadoc().isBlank()) {
            System.out.println("\n--- Javadoc ---");
            System.out.println(element.getJavadoc());
        }

        if (element.getSignature() != null && !element.getSignature().isBlank()) {
            System.out.println("\n--- Signature ---");
            System.out.println(element.getSignature());
        }

        if (element.getDependencies() != null && !element.getDependencies().isEmpty()) {
            System.out.println("\n--- Dependencies ---");
            System.out.println(String.join(", ", element.getDependencies()));
        }

        System.out.println("\n" + "-".repeat(35) + " Sadržaj " + "-".repeat(36));
        System.out.println(element.getContent());
        System.out.println("=".repeat(80) + "\n");
    }

    /**
     * MODIFIKOVANA metoda - prima listu kao argument
     */
    private static void listCodeElements(List<CodeElement> elements) {
        System.out.println("\nSvi elementi koda (prvih 50):");

        if (elements.isEmpty()) {
            System.out.println("Nema elemenata za prikaz.");
            return;
        }

        for (int i = 0; i < Math.min(elements.size(), 50); i++) {
            System.out.printf("[%d] %s\n", i, elements.get(i));
        }
        if (elements.size() > 50) {
            System.out.println("... i još " + (elements.size() - 50));
        }
    }

    /**
     * Prolazi kroz sve elemente u indeksu i generiše embedinge za njih
     */
    private static void generateEmbeddingsForIndex(CodeIndex index, LLMClient client) throws Exception {
        if (!client.isAvailable()) {
            throw new IllegalStateException("LLM Client not available for generating embeddings.");
        }

        List<CodeElement> elements = index.getAllElements();

        List<String> textsToEmbed = elements.stream()
                .map(CodeElement::toContextString)
                .toList();

        int batchSize = 100;
        int elementsProcessed = 0;

        for (int i = 0; i < textsToEmbed.size(); i += batchSize) {
            int end = Math.min(i + batchSize, textsToEmbed.size());
            List<String> batchTexts = textsToEmbed.subList(i, end);

            List<double[]> embeddings = client.generateEmbeddings(batchTexts);

            for (int j = 0; j < embeddings.size(); j++) {
                CodeElement element = elements.get(i + j);
                element.setEmbedding(embeddings.get(j));
            }

            elementsProcessed += batchTexts.size();
            System.out.println("✓ Generated embeddings for " + elementsProcessed + "/" + elements.size() + " elements...");
        }

        logger.info("Successfully generated and set embeddings for all {} elements.", elements.size());
    }
}