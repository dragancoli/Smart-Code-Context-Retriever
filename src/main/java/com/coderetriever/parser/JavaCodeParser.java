package com.coderetriever.parser;

import com.coderetriever.model.CodeElement;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Parser za Java kod koji ekstraktuje klase, metode i fieldove
 */
public class JavaCodeParser implements CodeParser {
    private static final Logger logger = LoggerFactory.getLogger(JavaCodeParser.class);
    private final JavaParser javaParser;

    public JavaCodeParser() {
        this.javaParser = new JavaParser();
    }

    /**
     * Parsira sve Java fajlove u direktorijumu
     */
    public List<CodeElement> parseDirectory(String directoryPath) {
        List<CodeElement> elements = new ArrayList<>();
        
        try (Stream<Path> paths = Files.walk(Path.of(directoryPath))) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(path -> {
                     try {
                         elements.addAll(parseFile(path.toFile()));
                     } catch (Exception e) {
                         logger.error("Error parsing file: " + path, e);
                     }
                 });
        } catch (IOException e) {
            logger.error("Error walking directory: " + directoryPath, e);
        }
        
        logger.info("Parsed {} code elements from {}", elements.size(), directoryPath);
        return elements;
    }

    /**
     * Parsira pojedinačni Java fajl
     */
    public List<CodeElement> parseFile(File file) throws IOException {
        List<CodeElement> elements = new ArrayList<>();
        
        ParseResult<CompilationUnit> parseResult = javaParser.parse(file);
        
        if (!parseResult.isSuccessful()) {
            logger.warn("Failed to parse file: {}", file.getPath());
            return elements;
        }
        
        CompilationUnit cu = parseResult.getResult().orElse(null);
        if (cu == null) return elements;
        
        String packageName = cu.getPackageDeclaration()
                              .map(pd -> pd.getNameAsString())
                              .orElse("");
        
        // Ekstraktuj klase i interface-e
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            CodeElement element = createClassElement(cls, file.getPath(), packageName);
            elements.add(element);
            
            // Ekstraktuj metode iz klase
            cls.findAll(MethodDeclaration.class).forEach(method -> {
                CodeElement methodElement = createMethodElement(method, file.getPath(), 
                                                                packageName, cls.getNameAsString());
                elements.add(methodElement);
            });
            
            // Ekstraktuj fieldove
            cls.findAll(FieldDeclaration.class).forEach(field -> {
                field.getVariables().forEach(variable -> {
                    CodeElement fieldElement = createFieldElement(field, variable.getNameAsString(),
                                                                  file.getPath(), packageName, 
                                                                  cls.getNameAsString());
                    elements.add(fieldElement);
                });
            });
        });
        
        return elements;
    }

    private CodeElement createClassElement(ClassOrInterfaceDeclaration cls, String filePath, 
                                          String packageName) {
        String id = packageName + "." + cls.getNameAsString();
        CodeElement.ElementType type = cls.isInterface() ? 
                                       CodeElement.ElementType.INTERFACE : 
                                       CodeElement.ElementType.CLASS;
        
        CodeElement element = new CodeElement(id, type, cls.getNameAsString());
        element.setFilePath(filePath);
        element.setPackageName(packageName);
        element.setContent(cls.toString());
        element.setSignature(getClassSignature(cls));
        
        cls.getRange().ifPresent(range -> {
            element.setStartLine(range.begin.line);
            element.setEndLine(range.end.line);
        });
        
        cls.getJavadoc().ifPresent(javadoc -> 
            element.setJavadoc(javadoc.toText())
        );
        
        // Ekstraktuj dependencies (imports, extended classes, implemented interfaces)
        cls.getExtendedTypes().forEach(ext -> 
            element.addDependency(ext.getNameAsString())
        );
        cls.getImplementedTypes().forEach(impl -> 
            element.addDependency(impl.getNameAsString())
        );
        
        return element;
    }

    private CodeElement createMethodElement(MethodDeclaration method, String filePath,
                                           String packageName, String className) {
        String id = packageName + "." + className + "." + method.getNameAsString();
        CodeElement element = new CodeElement(id, CodeElement.ElementType.METHOD, 
                                             method.getNameAsString());
        element.setFilePath(filePath);
        element.setPackageName(packageName);
        element.setContent(method.toString());
        element.setSignature(method.getDeclarationAsString());
        
        method.getRange().ifPresent(range -> {
            element.setStartLine(range.begin.line);
            element.setEndLine(range.end.line);
        });
        
        method.getJavadoc().ifPresent(javadoc -> 
            element.setJavadoc(javadoc.toText())
        );
        
        return element;
    }

    private CodeElement createFieldElement(FieldDeclaration field, String variableName,
                                          String filePath, String packageName, String className) {
        String id = packageName + "." + className + "." + variableName;
        CodeElement element = new CodeElement(id, CodeElement.ElementType.FIELD, variableName);
        element.setFilePath(filePath);
        element.setPackageName(packageName);
        element.setContent(field.toString());
        element.setSignature(field.toString().split(";")[0] + ";");
        
        field.getRange().ifPresent(range -> {
            element.setStartLine(range.begin.line);
            element.setEndLine(range.end.line);
        });
        
        field.getJavadoc().ifPresent(javadoc -> 
            element.setJavadoc(javadoc.toText())
        );
        
        return element;
    }

    private String getClassSignature(ClassOrInterfaceDeclaration cls) {
        StringBuilder sb = new StringBuilder();
        
        if (cls.isPublic()) sb.append("public ");
        if (cls.isPrivate()) sb.append("private ");
        if (cls.isProtected()) sb.append("protected ");
        if (cls.isAbstract()) sb.append("abstract ");
        if (cls.isFinal()) sb.append("final ");
        
        sb.append(cls.isInterface() ? "interface " : "class ");
        sb.append(cls.getNameAsString());
        
        if (!cls.getExtendedTypes().isEmpty()) {
            sb.append(" extends ");
            sb.append(cls.getExtendedTypes().get(0).getNameAsString());
        }
        
        if (!cls.getImplementedTypes().isEmpty()) {
            sb.append(" implements ");
            sb.append(String.join(", ", 
                cls.getImplementedTypes().stream()
                   .map(t -> t.getNameAsString())
                   .toList()));
        }
        
        return sb.toString();
    }

    @Override
    public boolean supportsFile(Path file) {
        return file.toString().endsWith(".java");
    }
}