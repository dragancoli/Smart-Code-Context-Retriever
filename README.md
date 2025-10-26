# Smart Code Retriever

**Smart Code Retriever** is a Java-based tool that uses Retrieval-Augmented Generation (RAG) to help you understand, analyze, and ask questions about your Java codebases.

Instead of manually searching through thousands of lines of code, you can ask questions in natural language, such as:
* *"How are database connections handled?"*
* *"Explain the purpose of the 'HybridRetrievalStrategy' class."*
* *"Where is the 'User' model used?"*

The tool finds the most relevant code snippets from your project and uses an LLM (Google Gemini or OpenAI) to generate a precise, context-aware answer.

## Demo

Here is the application in action, analyzingsource code of my project.


<img width="1697" height="728" alt="image" src="https://github.com/user-attachments/assets/d4294523-6bce-4709-8aec-2707259bcaf1" />

<img width="1693" height="772" alt="image" src="https://github.com/user-attachments/assets/3fb1461b-fdf9-4f5f-9bfb-af04ee67d2a2" />

<img width="1699" height="777" alt="image" src="https://github.com/user-attachments/assets/65ab9ff7-18ef-4a99-a460-982f943d8d72" />




## Core Features

* **Full Code Parsing:** Recursively parses an entire Java project using `javaparser` to extract classes, methods, interfaces, and fields.
* **Intelligent Indexing:** Creates a multi-layer code index for fast retrieval by name, type, package, and keywords.
* **Hybrid Retrieval:** Uses an advanced hybrid strategy that combines:
    1.  **Keyword Search**
    2.  **Dependency Graph Analysis**
    3.  **(Optional) Semantic Search** via vector embeddings.
* **RAG Pipeline:** Automatically augments the user's query with retrieved code context before sending it to the LLM.
* **Modular LLM Integration:** Includes clients for both Google Gemini and OpenAI APIs.
* **Interactive CLI:** A simple command-line interface for searching and asking questions.

## How it Works

The system operates in two stages: **Startup** and **Runtime**.

### At Startup

1.  **Parse:** `JavaCodeParser` analyzes the target project directory, reading all `.java` files and creating a `CodeElement` object for each class, method, etc.
2.  **Index:** All `CodeElement` objects are loaded into the `CodeIndex`, which builds maps (inverted index, dependency graph) for fast lookup.
3.  **User Choice:** The app asks you if you want to use semantic search.
4.  **(Optional) Embedding Generation:** If you answer `yes`, the tool calls the configured LLM's embedding API (e.g., Google's `embedding-001` or OpenAI's `text-embedding-ada-002`) to generate vector embeddings for every code element.
    * **Note:** This provides much higher accuracy but may take time and requires a **paid API plan** for embedding models.

### At Runtime (using the `ask` command)

1.  **Retrieve:** Your query is sent to the `HybridRetrievalStrategy`.
    * It gathers results from the `KeywordRetrievalStrategy` and `DependencyRetrievalStrategy`.
    * If embeddings are enabled, it also runs the `EmbeddingRetrievalStrategy` to find semantically similar code.
    * All results are combined and scored to find the best possible context.
2.  **Augment & Generate:** The tool combines your query with the (up to) top 5-10 retrieved code snippets into a large prompt.
3.  **Respond:** This augmented prompt is sent to the LLM (e.g., `gemini-pro-latest`), which generates an answer based *specifically* on the code provided.

## Requirements

* **Java JDK 17** (or newer)
* **Maven** (to build)
* **Google AI API Key** or **OpenAI API Key**

## Setup and Installation

1.  **Clone the Repository**
    ```sh
    git clone [https://github.com/dragancoli/Smart-Code-Context-Retriever](https://github.com/dragancoli/Smart-Code-Context-Retriever)
    cd Smart-Code-Context-Retriever
    ```

2.  **Set Up Your API Key**
    This tool reads your API key from an environment variable. You must set this before running the application.

    **For Google Gemini (Default):**
    * On macOS/Linux:
        ```sh
        export GOOGLE_API_KEY="YOUR_API_KEY_HERE"
        ```
    * On Windows (CMD):
        ```sh
        set GOOGLE_API_KEY=YOUR_API_KEY_HERE
        ```
    * On Windows (PowerShell):
        ```sh
        $env:GOOGLE_API_KEY="YOUR_API_KEY_HERE"
        ```

    **To use OpenAI:**
    * Set the `OPENAI_API_KEY` environment variable instead.
    * You must also edit `Main.java` to instantiate `OpenAIClient` instead of `GeminiClient`.

3.  **Build with Maven**
    The project uses Maven to manage dependencies. Run:
    ```sh
    mvn clean package
    ```
    This will download all required libraries (like `javaparser`, `okhttp`, `gson`, etc.) and create an executable `.jar` file in the `target/` directory.

    *(The `pom.xml` is already included in the repository. The snippet below is for reference.)*
    <details>
      <summary>Minimal pom.xml</summary>
      
      ```xml
      <project xmlns="[http://maven.apache.org/POM/4.0.0](http://maven.apache.org/POM/4.0.0)"
               xmlns:xsi="[http://www.w3.org/2001/XMLSchema-instance](http://www.w3.org/2001/XMLSchema-instance)"
               xsi:schemaLocation="[http://maven.apache.org/POM/4.0.0](http://maven.apache.org/POM/4.0.0) [http://maven.apache.org/xsd/maven-4.0.0.xsd](http://maven.apache.org/xsd/maven-4.0.0.xsd)">
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.coderetriever</groupId>
          <artifactId>smart-code-retriever</artifactId>
          <version>1.0.0</version>
      
          <properties>
              <maven.compiler.source>17</maven.compiler.source>
              <maven.compiler.target>17</maven.compiler.target>
          </properties>
      
          <dependencies>
              <dependency>
                  <groupId>com.github.javaparser</groupId>
                  <artifactId>javaparser-core</artifactId>
                  <version>3.25.10</version>
              </dependency>
              <dependency>
                  <groupId>com.squareup.okhttp3</groupId>
                  <artifactId>okhttp</artifactId>
                  <version>4.12.0</version>
              </dependency>
              <dependency>
                  <groupId>com.google.code.gson</groupId>
                  <artifactId>gson</artifactId>
                  <version>2.10.1</version>
              </dependency>
              <dependency>
                  <groupId>org.slf4j</groupId>
                  <artifactId>slf4j-api</artifactId>
                  <version>2.0.13</version>
              </dependency>
              <dependency>
                  <groupId>org.slf4j</groupId>
                  <artifactId>slf4j-simple</artifactId>
                  <version>2.0.13</version>
              </dependency>
              <dependency>
                  <groupId>org.apache.commons</groupId>
                  <artifactId>commons-text</artifactId>
                  <version>1.12.0</version>
              </dependency>
          </dependencies>
          
           <build>
                <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-assembly-plugin</artifactId>
                        <version>3.3.0</version>
                        <configuration>
                            <archive>
                                <manifest>
                                    <mainClass>com.coderetriever.Main</mainClass>
                                </manifest>
                            </archive>
                            <descriptorRefs>
                                <descriptorRef>jar-with-dependencies</descriptorRef>
                            </descriptorRefs>
                        </configuration>
                        <executions>
                            <execution>
                                <id>make-assembly</id>
                                <phase>package</phase>
                                <goals>
                                    <goal>single</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
      </project>
      ```
    </details>


## Usage

After `mvn package`, the executable JAR file will be in the `target/` folder.

1.  **Run the Tool**
    Run the JAR file, passing the path to the Java project you want to analyze as an argument.

    ```sh
    # Example
    java -jar target/smart-code-retriever-1.0.0-jar-with-dependencies.jar /path/to/my/java/project
    ```
    To analyze this project's own source code, you can use `.`:
    ```sh
    java -jar target/smart-code-retriever-1.0.0-jar-with-dependencies.jar .
    ```
    *(If running from your IDE, just set the command-line argument to the project path.)*

2.  **Choose Embedding Mode**
    The tool will ask you if you want to enable semantic search.
    ```
    Do you wish to use Embeddings for semantic search? (yes/no):
    ```
    * **`yes`**: Enables highly accurate semantic search. Requires a **paid API plan** for embedding generation.
    * **`no`**: Disables semantic search. The tool will fall back to Keyword + Dependency search, which is fast and free.

3.  **Use Interactive Commands**
    Once loaded, you will see the `>` prompt. Available commands:

    * `search <query>`:
        Finds relevant code using the `HybridRetrievalStrategy` (does not use the LLM for *answering*).
        *Example: `search database connection`*

    * `ask <question>`:
        Executes the full RAG pipeline. Finds context and sends the question + context to the LLM.
        *Example: `ask how is the dependency retrieval strategy implemented?`*

    * `show <number>`:
        Shows the full content and metadata for a result from the *last `search` command*.
        *Example: `show 0`*

    * `list`:
        Lists the first 50 code elements found in the project.

    * `quit`:
        Exits the application.

## Code Structure

* `src/main/java/com/coderetriever/`
    * `Main.java`: The main entry point. Handles the CLI and orchestrates the RAG pipeline.
    * `/model/CodeElement.java`: Data model representing a single piece of code (class, method, etc.).
    * `/parser/JavaCodeParser.java`: Logic for parsing `.java` files using `javaparser`.
    * `/indexer/CodeIndex.java`: In-memory database holding all `CodeElement` objects and search indexes.
    * `/retrival/`: Contains the retrieval strategies:
        * `RetrievalStrategy.java`: The common interface.
        * `KeywordRetrievalStrategy.java`: Searches by matching keywords.
        * `DependencyRetrievalStrategy.java`: Searches using the dependency graph.
        * `EmbeddingRetrievalStrategy.java`: Searches for semantic similarity using vectors.
        * `HybridRetrievalStrategy.java`: Combines all strategies with weighted scoring.
    * `/llm/`: Contains the clients for LLM APIs:
        * `LLMClient.java`: The common interface (supports `queryWithContext` and `generateEmbeddings`).
        * `GeminiClient.java`: Implementation for Google Gemini API.
        * `OpenAIClient.java`: Implementation for OpenAI API.
