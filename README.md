# Smart Code Retriever

**Smart Code Retriever** je alat (napisan u Javi) koji koristi Retrieval-Augmented Generation (RAG) da vam pomogne da razumete, analizirate i postavljate pitanja o Java projektima.

Umesto da ručno pretražujete hiljade linija koda, možete da postavite pitanja na prirodnom jeziku, kao što su:
* *"Kako se rukuje konekcijama ka bazi podataka?"*
* *"Objasni svrhu 'HybridRetrievalStrategy' klase."*
* *"Gde se sve koristi 'User' model?"*

Alat zatim pronalazi najrelevantnije delove koda i koristi Google Gemini da generiše precizan odgovor zasnovan na kontekstu.

## Glavne Funkcije

* **Puno Parsiranje Koda:** Rekurzivno parsira ceo Java projekat koristeći `javaparser` da izvuče klase, metode, interfejse i polja.
* **Inteligentni Indeks:** Kreira višeslojni indeks koda za brzu pretragu po imenu, tipu, paketu i ključnim rečima.
* **Hibridna Pretraga (Retrieval):** Koristi hibridnu strategiju koja kombinuje pretragu po ključnim rečima (TF-IDF/Jaro-Winkler) i analizu zavisnosti (dependency graph) da pronađe najrelevantniji kod.
* **RAG Pipeline:** Automatski "pojačava" (augment) upit korisnika sa pronađenim kontekstom koda pre nego što ga pošalje LLM-u.
* **LLM Integracija:** Povezan sa Google Gemini API-jem (ili OpenAI) za generisanje odgovora zasnovanih na kodu.
* **Interaktivni CLI:** Jednostavan komandni interfejs za pretragu i postavljanje pitanja.

## Kako Radi (Arhitektura)

Sistem funkcioniše kroz 4 glavne faze kada postavite pitanje (`ask` komanda):

1.  **Faza 1: Parsiranje (Parse)**
    * Prilikom pokretanja, `JavaCodeParser` analizira direktorijum projekta, čita sve `.java` fajlove i kreira listu `CodeElement` objekata (klasa, metoda, itd.).

2.  **Faza 2: Indeksiranje (Index)**
    * Svi `CodeElement` objekti se ubacuju u `CodeIndex`. Ovo kreira više mapa za brzi pristup:
        * Invertni indeks (reč -> ID elementa) za pretragu ključnih reči.
        * Graf zavisnosti (ko koga poziva / nasleđuje).
        * Mape po paketu i tipu.

3.  **Faza 3: Pretraga (Retrieve)**
    * Kada unesete upit, `HybridRetrievalStrategy` se aktivira.
    * Ona kombinuje rezultate dve strategije:
        1.  `KeywordRetrievalStrategy`: Traži elemente koji se poklapaju sa ključnim rečima.
        2.  `DependencyRetrievalStrategy`: Traži elemente koji su povezani sa rezultatima pretrage (npr. klase koje ih koriste).
    * Vraća listu od 5-10 najrelevantnijih `CodeElement` objekata.

4.  **Faza 4: Generisanje (Generate)**
    * Tvoj originalni upit i tekstualni sadržaj pronađenih elemenata koda se spajaju u jedan veliki "prompt".
    * Taj prompt se šalje Google Gemini API-ju preko `GeminiClient`-a.
    * Vi dobijate odgovor koji je generisao AI, ali je direktno zasnovan na sadržaju *vašeg* koda.

## Zahtevi

* **Java JDK 17** (ili noviji)
* **Maven** (za upravljanje zavisnostima)
* **Google AI API Ključ** ili  **Open AI API Ključ**

## Podešavanje i Instalacija

1.  **Klonirajte Repozitorijum**
    ```sh
    git clone https://github.com/dragancoli/Smart-Code-Context-Retriever
    cd smart-code-retriever
    ```

2.  **Podesite API Ključ**
    Ovaj alat čita vaš API ključ iz varijabli okruženja (environment variables). Morate ga postaviti pre pokretanja.

    * **Na macOS/Linux:**
        ```sh
        export GOOGLE_API_KEY="VAŠ_API_KLJUČ_OVDE"
        ```
    * **Na Windows (Command Prompt):**
        ```sh
        set GOOGLE_API_KEY=VAŠ_API_KLJUČ_OVDE
        ```
    * **Na Windows (PowerShell):**
        ```sh
        $env:GOOGLE_API_KEY="VAŠ_API_KLJUČ_OVDE"
        ```

    Postoji i klijent za Open AI Api ključ. U tom slučaju moramo imati podešenu OPEN_AI_API_KEY vrijablu okruzenja. I u Main.java klasi potrebno je koristiti OpenAiClienta.

3.  **Instalirajte Zavisnosti (Dependencies)**
    Projekat koristi Maven za upravljanje zavisnostima. Pokrenite:
    ```sh
    mvn clean install
    ```
    Ovo će preuzeti sve potrebne biblioteke (kao što su `javaparser`, `okhttp`, `gson`, `slf4j` itd.) i napraviti izvršni `.jar` fajl.

    *(Ako nemate `pom.xml`, evo minimalnog primera koji treba da dodate):*
    <details>
      <summary>Minimalni primer pom.xml</summary>
      
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


## Korišćenje

Nakon `mvn package`, izvršni JAR fajl će se nalaziti u `target/` folderu.

1.  **Pokrenite Alat**
    Pokrenite JAR fajl i prosledite mu putanju do Java projekta koji želite da analizirate. 

    ```sh
    # Primer
    java -jar target/smart-code-retriever-1.0.0-jar-with-dependencies.jar /putanja/do/mog/java/projekta
    ```
    Ukoliko koristite razvojno okruzenje potrebno je podesiti parametar komandne linije koji predstavlja putanju do Java projekta.

2.  **Koristite Interaktivne Komande**
    Kada se program pokrene, videćete `>` prompt. Dostupne komande su:

    * `search <upit>`:
        Pronalazi relevantne delove koda koristeći `HybridRetrievalStrategy`. Ne koristi LLM.
        *Primer: `search database connection`*

    * `ask <pitanje>`:
        Pokreće pun RAG pipeline. Pronalazi kontekst i šalje pitanje i kontekst LLM-u.
        *Primer: `ask how is the dependency retrieval strategy implemented?`*

    * `show <broj>`:
        Prikazuje detaljan sadržaj i metapodatke za rezultat iz *poslednje `search` komande*.
        *Primer: `show 0`*

    * `list`:
        Izlistava prvih 50 elemenata koda koji su pronađeni u projektu.

    * `quit`:
        Izlazi iz programa.

## Struktura Koda

* `src/main/java/com/coderetriever/`
    * `Main.java`: Glavna ulazna tačka, rukuje CLI-jem i pokreće RAG pipeline.
    * `/model/CodeElement.java`: Model podataka koji predstavlja jedan element koda (klasu, metodu...).
    * `/parser/JavaCodeParser.java`: Logika za parsiranje `.java` fajlova.
    * `/indexer/CodeIndex.java`: Skladište za sve elemente koda i indekse za pretragu.
    * `/retrival/`: Sadrži strategije za pretragu:
        * `RetrievalStrategy.java`: Interfejs.
        * `KeywordRetrievalStrategy.java`: Pretraga po ključnim rečima.
        * `DependencyRetrievalStrategy.java`: Pretraga po grafu zavisnosti.
        * `HybridRetrievalStrategy.java`: Kombinuje prethodne dve.
    * `/llm/`: Sadrži klijente za LLM API-je:
        * `LLMClient.java`: Interfejs.
        * `GeminiClient.java`: Implementacija za Google Gemini API.
        * `OpenAIClient.java`: Implementacija za OpenAI API.
