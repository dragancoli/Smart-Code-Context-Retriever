package com.coderetriever.parser;

import com.coderetriever.model.CodeElement;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Interfejs za parsere specifične za određeni jezik.
 */
public interface CodeParser {

    /**
     * Proverava da li ovaj parser podržava dati fajl (npr. na osnovu ekstenzije)
     * @param file Fajl koji treba proveriti
     * @return true ako podržava, false ako ne
     */
    boolean supportsFile(Path file);

    /**
     * Parsira jedan fajl i vraća listu svih pronađenih elemenata koda
     * @param file Fajl za parsiranje
     * @return Lista CodeElement-a
     * @throws IOException Ako dođe do greške pri čitanju fajla
     */
    List<CodeElement> parseFile(File file) throws IOException;
    
}