package com.example.plagiarism.model;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public enum Language {
    JAVA, PYTHON, CPP;

    public static Language fromName(String name) {
        if (name == null) throw invalid();
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "java" -> JAVA;
            case "python", "py" -> PYTHON;
            case "c", "cpp", "c++" -> CPP;
            default -> throw invalid();
        };
    }

    public static Language fromFilename(String name) {
        if (name == null || !name.contains(".")) throw invalid();
        return switch (name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)) {
            case "java" -> JAVA;
            case "py" -> PYTHON;
            case "c", "h", "cc", "cpp", "cxx", "hpp", "hh", "hxx" -> CPP;
            default -> throw invalid();
        };
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Supported languages: Java (.java), Python (.py), C/C++ (.c, .h, .cc, .cpp, .cxx, .hpp, .hh, .hxx).");
    }
}
