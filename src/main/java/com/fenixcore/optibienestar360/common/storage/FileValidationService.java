package com.fenixcore.optibienestar360.common.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Validates an upload's extension and size against the DB-configurable
 * policy for its {@link FileVisibility} scope — spec §2.1, migration V55 —
 * and, for the formats we know, that the bytes really are that format
 * (magic-number check): a renamed {@code .exe} or HTML page uploaded as
 * {@code comprobante.pdf} is rejected instead of being stored. In an
 * {@code ALLOWLIST} scope every accepted extension must have a known
 * signature: one without it is refused (fail-closed) instead of stored unchecked.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileValidationService {

    private final FileTypePolicyRepository policyRepository;

    public void validate(FileVisibility visibility, MultipartFile file) {
        if (file == null || file.isEmpty()) return;
        FileTypePolicy policy = checkPolicy(visibility, file.getOriginalFilename(), file.getSize());
        verifyContent(file, policy.getMode());
    }

    public void validate(FileVisibility visibility, String fileName, long sizeBytes) {
        checkPolicy(visibility, fileName, sizeBytes);
    }

    private FileTypePolicy checkPolicy(FileVisibility visibility, String fileName, long sizeBytes) {
        FileTypePolicy policy = policyRepository.findByVisibility(visibility)
                .orElseThrow(() -> new NoSuchElementException("file.type_policy.not_found"));

        String extension = extensionOf(fileName);
        boolean listed = extension != null && policy.getExtensions().stream()
                .anyMatch(e -> e.equalsIgnoreCase(extension));

        boolean allowed = switch (policy.getMode()) {
            case ALLOWLIST -> listed;
            case DENYLIST -> !listed;
        };
        if (!allowed) {
            throw new IllegalArgumentException("file.validation.extension_not_allowed");
        }
        if (sizeBytes > policy.getMaxSizeBytes()) {
            throw new IllegalArgumentException("file.validation.size_exceeded");
        }
        return policy;
    }

    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("webp", "image/webp"),
            Map.entry("gif", "image/gif"),
            Map.entry("csv", "text/csv"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("doc", "application/msword"),
            Map.entry("txt", "text/plain"),
            Map.entry("tsv", "text/tab-separated-values"),
            Map.entry("rtf", "application/rtf"),
            Map.entry("odt", "application/vnd.oasis.opendocument.text"),
            Map.entry("ods", "application/vnd.oasis.opendocument.spreadsheet"),
            Map.entry("odp", "application/vnd.oasis.opendocument.presentation"));

    /**
     * Content-Type to store/serve a file with, derived from its (already
     * validated) extension — never the client's declared type: a {@code .png}
     * uploaded as {@code text/html} would otherwise be served as a page from
     * the storage domain when staff open it. Unknown extensions fall back to
     * {@code application/octet-stream}, which browsers download instead of render.
     */
    public static String safeContentType(String fileName) {
        String extension = extensionOf(fileName);
        return extension == null ? "application/octet-stream"
                : CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
    }

    /** Bytes read from the start of the upload to check its signature. */
    private static final int HEAD_BYTES = 1024;

    private static final byte[] PDF = ascii("%PDF-");
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] GIF87A = ascii("GIF87a");
    private static final byte[] GIF89A = ascii("GIF89a");
    private static final byte[] RIFF = ascii("RIFF");
    private static final byte[] WEBP = ascii("WEBP");
    /** ZIP container: OOXML ({@code .xlsx}, {@code .docx}) and OpenDocument. */
    private static final byte[] ZIP = {'P', 'K', 0x03, 0x04};
    /** OLE2 compound file: legacy Office ({@code .xls}, {@code .doc}). */
    private static final byte[] OLE2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final byte[] RTF = ascii("{\\rtf");
    /**
     * OpenDocument files are ZIPs whose first entry is an uncompressed
     * {@code mimetype} file, so its value sits in the first bytes. Checking it
     * tells an {@code .ods} from an {@code .odt} or any other ZIP.
     */
    private static final String ODF_MIMETYPE = "mimetypeapplication/vnd.oasis.opendocument.";

    private static final Set<String> TEXT_EXTENSIONS = Set.of("csv", "tsv", "txt");

    /**
     * Expected content per extension. Extensions not listed here are not
     * sniffed in a {@code DENYLIST} scope and are refused in an
     * {@code ALLOWLIST} scope — there is no signature to compare against.
     */
    private static final Map<String, Predicate<byte[]>> SIGNATURES = Map.ofEntries(
            // The PDF header may follow a few junk bytes; readers accept it within the first 1 KB.
            Map.entry("pdf", head -> indexOf(head, PDF) >= 0),
            Map.entry("jpg", head -> startsWith(head, JPEG, 0)),
            Map.entry("jpeg", head -> startsWith(head, JPEG, 0)),
            Map.entry("png", head -> startsWith(head, PNG, 0)),
            Map.entry("gif", head -> startsWith(head, GIF87A, 0) || startsWith(head, GIF89A, 0)),
            Map.entry("webp", head -> startsWith(head, RIFF, 0) && startsWith(head, WEBP, 8)),
            Map.entry("xlsx", head -> startsWith(head, ZIP, 0)),
            Map.entry("docx", head -> startsWith(head, ZIP, 0)),
            Map.entry("xls", head -> startsWith(head, OLE2, 0)),
            Map.entry("doc", head -> startsWith(head, OLE2, 0)),
            Map.entry("rtf", head -> startsWith(head, RTF, 0)),
            Map.entry("odt", head -> isOpenDocument(head, "text")),
            Map.entry("ods", head -> isOpenDocument(head, "spreadsheet")),
            Map.entry("odp", head -> isOpenDocument(head, "presentation")));

    /**
     * Rejects an upload whose first bytes do not match its extension. Text
     * formats have no signature: they only must not contain NUL bytes, which
     * no CSV or plain-text file has but every binary does. An extension with
     * no known signature passes only in a {@code DENYLIST} scope.
     */
    void verifyContent(MultipartFile file, FileTypePolicy.Mode mode) {
        String extension = extensionOf(file.getOriginalFilename());
        Predicate<byte[]> signature = extension == null ? null : SIGNATURES.get(extension);
        boolean text = extension != null && TEXT_EXTENSIONS.contains(extension);
        if (signature == null && !text) {
            if (mode == FileTypePolicy.Mode.ALLOWLIST) {
                throw new IllegalArgumentException("file.validation.content_unverifiable");
            }
            return;
        }

        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(HEAD_BYTES);
        } catch (IOException e) {
            throw new IllegalArgumentException("file.validation.content_mismatch", e);
        }
        boolean matches = text ? indexOf(head, new byte[]{0}) < 0 : signature.test(head);
        if (!matches) {
            throw new IllegalArgumentException("file.validation.content_mismatch");
        }
    }

    private static boolean isOpenDocument(byte[] head, String kind) {
        return startsWith(head, ZIP, 0) && indexOf(head, ascii(ODF_MIMETYPE + kind)) >= 0;
    }

    private static boolean startsWith(byte[] data, byte[] prefix, int offset) {
        if (data.length < offset + prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) return false;
        }
        return true;
    }

    private static int indexOf(byte[] data, byte[] needle) {
        for (int i = 0; i + needle.length <= data.length; i++) {
            if (startsWith(data, needle, i)) return i;
        }
        return -1;
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) return null;
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return null;
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
