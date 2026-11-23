package com.fenixcore.optibienestar360.common.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Extension/size policy (V55, widened in V264) plus the magic-number check: the declared
 * extension must match the real bytes for the formats we can recognise.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileValidationServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    private static final byte[] PDF = "%PDF-1.7\n%âãÏÓ\n".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] ZIP = {'P', 'K', 0x03, 0x04, 0x14, 0, 0x06, 0};
    private static final byte[] EXE = {'M', 'Z', (byte) 0x90, 0, 0x03, 0, 0, 0};
    private static final byte[] OLE2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0, 0};
    private static final byte[] RTF = "{\\rtf1\\ansi Hola}".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ODS = openDocument("spreadsheet");
    private static final byte[] ODT = openDocument("text");
    private static final byte[] HTML = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

    @Mock private FileTypePolicyRepository policyRepository;

    private FileValidationService service;

    @BeforeEach
    void setUp() {
        service = new FileValidationService(policyRepository);
        when(policyRepository.findByVisibility(FileVisibility.CONFIDENTIAL))
                .thenReturn(Optional.of(policy(FileVisibility.CONFIDENTIAL, FileTypePolicy.Mode.ALLOWLIST,
                        List.of("pdf", "jpg", "jpeg", "png", "doc", "docx", "odt", "rtf",
                                "xls", "xlsx", "ods", "csv", "tsv", "txt", "heic"), 10L * 1024 * 1024)));
        when(policyRepository.findByVisibility(FileVisibility.TEMPORARY))
                .thenReturn(Optional.of(policy(FileVisibility.TEMPORARY, FileTypePolicy.Mode.ALLOWLIST,
                        List.of("pdf", "xlsx", "csv"), 20L * 1024 * 1024)));
        when(policyRepository.findByVisibility(FileVisibility.INTERNAL))
                .thenReturn(Optional.of(policy(FileVisibility.INTERNAL, FileTypePolicy.Mode.DENYLIST,
                        List.of("exe", "sh"), 20L * 1024 * 1024)));
    }

    @Test
    void acceptsFilesWhoseBytesMatchTheExtension() {
        assertThatCode(() -> service.validate(FileVisibility.CONFIDENTIAL, file("comprobante.pdf", PDF)))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.validate(FileVisibility.CONFIDENTIAL, file("foto.PNG", PNG)))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.validate(FileVisibility.CONFIDENTIAL, file("foto.jpeg", JPEG)))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.validate(FileVisibility.TEMPORARY, file("reporte.xlsx", ZIP)))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.validate(FileVisibility.TEMPORARY,
                file("aliados.csv", "rif;nombre\nJ-123;Óptica\n".getBytes(StandardCharsets.UTF_8))))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsOfficeAndTextDocuments() {
        assertThatCode(() -> service.validate(FileVisibility.CONFIDENTIAL, file("informe.docx", ZIP)))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.validate(FileVisibility.CONFIDENTIAL, file("informe.doc", OLE2)))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.validate(FileVisibility.CONFIDENTIAL, file("cuentas.xls", OLE2)))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.validate(FileVisibility.CONFIDENTIAL, file("cuentas.ods", ODS)))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.validate(FileVisibility.CONFIDENTIAL, file("informe.odt", ODT)))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.validate(FileVisibility.CONFIDENTIAL, file("carta.rtf", RTF)))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.validate(FileVisibility.CONFIDENTIAL,
                file("datos.tsv", "rif\tnombre\nJ-123\tÓptica\n".getBytes(StandardCharsets.UTF_8))))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.validate(FileVisibility.CONFIDENTIAL,
                file("nota.txt", "Pago del mes".getBytes(StandardCharsets.UTF_8))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAnOpenDocumentOfTheWrongKind() {
        assertThatThrownBy(() -> service.validate(FileVisibility.CONFIDENTIAL, file("cuentas.ods", ODT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("file.validation.content_mismatch");
        assertThatThrownBy(() -> service.validate(FileVisibility.CONFIDENTIAL, file("informe.odt", ZIP)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("file.validation.content_mismatch");
    }

    @Test
    void rejectsABinaryNamedAsTsvOrRtf() {
        assertThatThrownBy(() -> service.validate(FileVisibility.CONFIDENTIAL, file("datos.tsv", EXE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("file.validation.content_mismatch");
        assertThatThrownBy(() -> service.validate(FileVisibility.CONFIDENTIAL, file("carta.rtf", HTML)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("file.validation.content_mismatch");
    }

    @Test
    void refusesAnAllowlistedExtensionWithoutAKnownSignature() {
        assertThatThrownBy(() -> service.validate(FileVisibility.CONFIDENTIAL, file("foto.heic", PNG)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("file.validation.content_unverifiable");
    }

    @Test
    void acceptsAPdfHeaderPrecededByJunkBytes() {
        byte[] content = new byte[PDF.length + 3];
        content[0] = (byte) 0xEF;
        content[1] = (byte) 0xBB;
        content[2] = (byte) 0xBF;
        System.arraycopy(PDF, 0, content, 3, PDF.length);

        assertThatCode(() -> service.validate(FileVisibility.CONFIDENTIAL, file("comprobante.pdf", content)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsARenamedExecutable() {
        assertThatThrownBy(() -> service.validate(FileVisibility.CONFIDENTIAL, file("comprobante.pdf", EXE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("file.validation.content_mismatch");
    }

    @Test
    void rejectsAnHtmlPageNamedAsAnImage() {
        assertThatThrownBy(() -> service.validate(FileVisibility.CONFIDENTIAL, file("foto.png", HTML)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("file.validation.content_mismatch");
    }

    @Test
    void rejectsAPngNamedAsJpeg() {
        assertThatThrownBy(() -> service.validate(FileVisibility.CONFIDENTIAL, file("foto.jpg", PNG)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("file.validation.content_mismatch");
    }

    @Test
    void rejectsABinaryNamedAsCsv() {
        assertThatThrownBy(() -> service.validate(FileVisibility.TEMPORARY, file("aliados.csv", EXE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("file.validation.content_mismatch");
    }

    @Test
    void doesNotSniffExtensionsWithoutAKnownSignature() {
        assertThatCode(() -> service.validate(FileVisibility.INTERNAL, file("notas.md", EXE)))
                .doesNotThrowAnyException();
    }

    @Test
    void extensionPolicyIsCheckedBeforeTheContent() {
        assertThatThrownBy(() -> service.validate(FileVisibility.CONFIDENTIAL, file("virus.exe", EXE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("file.validation.extension_not_allowed");
    }

    @Test
    void sizeLimitStillApplies() {
        when(policyRepository.findByVisibility(FileVisibility.PUBLIC))
                .thenReturn(Optional.of(policy(FileVisibility.PUBLIC, FileTypePolicy.Mode.ALLOWLIST,
                        List.of("png"), 4L)));

        assertThatThrownBy(() -> service.validate(FileVisibility.PUBLIC, file("logo.png", PNG)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("file.validation.size_exceeded");
    }

    @Test
    void emptyOrMissingFileIsIgnored() {
        assertThatCode(() -> service.validate(FileVisibility.CONFIDENTIAL, (org.springframework.web.multipart.MultipartFile) null))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.validate(FileVisibility.CONFIDENTIAL, file("vacio.pdf", new byte[0])))
                .doesNotThrowAnyException();
    }

    /** ZIP local header followed by the stored {@code mimetype} entry, as LibreOffice writes it. */
    private static byte[] openDocument(String kind) {
        byte[] header = new byte[30];
        header[0] = 'P';
        header[1] = 'K';
        header[2] = 0x03;
        header[3] = 0x04;
        byte[] entry = ("mimetypeapplication/vnd.oasis.opendocument." + kind)
                .getBytes(StandardCharsets.US_ASCII);
        byte[] content = new byte[header.length + entry.length];
        System.arraycopy(header, 0, content, 0, header.length);
        System.arraycopy(entry, 0, content, header.length, entry.length);
        return content;
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    private static FileTypePolicy policy(FileVisibility visibility, FileTypePolicy.Mode mode,
                                         List<String> extensions, long maxSizeBytes) {
        FileTypePolicy policy = new FileTypePolicy();
        policy.setVisibility(visibility);
        policy.setMode(mode);
        policy.setExtensions(extensions);
        policy.setMaxSizeBytes(maxSizeBytes);
        return policy;
    }
}
