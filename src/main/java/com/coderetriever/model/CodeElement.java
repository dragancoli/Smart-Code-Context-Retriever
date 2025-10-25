package com.coderetriever.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Predstavlja jedan element koda (klasa, metoda, field)
 */
public class CodeElement {
    private String id;
    private ElementType type;
    private String name;
    private String signature;
    private String content;
    private String filePath;
    private int startLine;
    private int endLine;
    private String packageName;
    private List<String> dependencies;
    private String javadoc;
    
    // Embedding vector (biće popunjen kasnije)
    private double[] embedding;

    public CodeElement(String id, ElementType type, String name) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.dependencies = new ArrayList<>();
    }

    public enum ElementType {
        // Java tipovi
        CLASS,
        METHOD,
        FIELD,
        INTERFACE,
        ENUM
    }

    // Getters i Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public ElementType getType() { return type; }
    public void setType(ElementType type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public int getStartLine() { return startLine; }
    public void setStartLine(int startLine) { this.startLine = startLine; }

    public int getEndLine() { return endLine; }
    public void setEndLine(int endLine) { this.endLine = endLine; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public List<String> getDependencies() { return dependencies; }
    public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }
    public void addDependency(String dependency) { this.dependencies.add(dependency); }

    public String getJavadoc() { return javadoc; }
    public void setJavadoc(String javadoc) { this.javadoc = javadoc; }

    public double[] getEmbedding() { return embedding; }
    public void setEmbedding(double[] embedding) { this.embedding = embedding; }

    /**
     * Vraća string reprezentaciju pogodnu za LLM kontekst
     */
    public String toContextString() {
        StringBuilder sb = new StringBuilder();
        sb.append("// File: ").append(filePath).append("\n");
        sb.append("// Lines: ").append(startLine).append("-").append(endLine).append("\n");
        
        if (javadoc != null && !javadoc.isEmpty()) {
            sb.append(javadoc).append("\n");
        }
        
        if (signature != null && !signature.isEmpty()) {
            sb.append(signature).append("\n");
        } else {
            sb.append(content).append("\n");
        }
        
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("%s: %s (%s)", type, name, filePath);
    }
}