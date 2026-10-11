package com.example.plagiarism.service;

import com.example.plagiarism.model.Language;
import com.example.plagiarism.model.Models.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SourceService {
    public static final int SOURCE_LIMIT = 256 * 1024, ZIP_LIMIT = 2 * 1024 * 1024;

    public Source file(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();
        if (name == null) throw bad("Missing filename.");
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        Language language = Language.fromFilename(name);
        try (InputStream input = file.getInputStream()) { return decode(name, language, read(input, SOURCE_LIMIT)); }
    }

    public Source snippet(String name, String code, Language language) {
        if (code == null || code.isBlank()) throw bad("Both code snippets are required.");
        if (code.length() > SOURCE_LIMIT || code.getBytes(StandardCharsets.UTF_8).length > SOURCE_LIMIT)
            throw large("Each source must be at most 256 KiB.");
        if (code.indexOf('\0') >= 0) throw bad("Binary data is not source code.");
        return new Source(name, language, code);
    }

    public List<Source> archive(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".zip")) throw bad("Upload a .zip archive.");
        byte[] bytes;
        try (InputStream input = file.getInputStream()) { bytes = read(input, ZIP_LIMIT); }
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K' || bytes[2] != 3 || bytes[3] != 4)
            throw bad("ZIP must contain at least two supported source files.");
        List<Source> sources = new ArrayList<>();
        Set<String> names = new HashSet<>();
        int total = 0, entries = 0;
        // Stream in memory; never extract paths to disk. Bound actual expanded bytes, not ZIP metadata.
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 100) throw large("ZIP has too many entries (maximum 100 including directories).");
                String path = entry.getName().replace('\\', '/');
                if (path.length() > 200 || path.startsWith("/") || path.contains(":") ||
                        Arrays.asList(path.split("/")).contains("..")) throw bad("Unsafe ZIP entry path.");
                if (!names.add(path)) throw bad("Duplicate ZIP entry name: " + path);
                if (entry.isDirectory()) {
                    if (zip.read() != -1) throw bad("ZIP directories must not contain data.");
                    continue;
                }
                if (sources.size() >= 20) throw large("ZIP supports at most 20 source files.");
                Language language = Language.fromFilename(path);
                byte[] content = read(zip, Math.min(SOURCE_LIMIT, ZIP_LIMIT - total));
                total += content.length;
                sources.add(decode(path, language, content));
            }
        } catch (ZipException | IllegalArgumentException e) { throw bad("Malformed, encrypted, or unsupported ZIP archive."); }
        if (sources.size() < 2) throw bad("ZIP must contain at least two supported source files.");
        return List.copyOf(sources);
    }

    private Source decode(String name, Language language, byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            return snippet(name, text, language);
        } catch (CharacterCodingException e) { throw bad("Source files must use UTF-8 text encoding."); }
    }

    private byte[] read(InputStream input, int limit) throws IOException {
        byte[] bytes = input.readNBytes(limit + 1);
        if (bytes.length > limit) throw large("Size limit exceeded: 256 KiB per source, 2 MiB per ZIP and total expanded content.");
        return bytes;
    }
    private ResponseStatusException bad(String detail) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail); }
    private ResponseStatusException large(String detail) { return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, detail); }
}
