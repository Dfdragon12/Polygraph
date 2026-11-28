package com.polygraph.erp.modules.evaluados.service;

import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Documentos del candidato: solo PDF o imagen, revisando el contenido real y el tamaño")
class ValidadorArchivoCandidatoTest {

    private final ValidadorArchivoCandidato validador = new ValidadorArchivoCandidato(5, 5);

    private static byte[] con(byte[] cabecera, int tamano) {
        byte[] datos = Arrays.copyOf(cabecera, Math.max(tamano, cabecera.length));
        return datos;
    }

    private static final byte[] PDF = "%PDF-1.7\n".getBytes();
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] EXE = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0};

    @Test
    @DisplayName("PDF, JPG y PNG reales dentro del límite pasan")
    void validos() {
        assertThatCode(() -> validador.validar(new MockMultipartFile("archivo", "cedula.pdf", "application/pdf", con(PDF, 1000)))).doesNotThrowAnyException();
        assertThatCode(() -> validador.validar(new MockMultipartFile("archivo", "foto.JPG", "image/jpeg", con(JPG, 1000)))).doesNotThrowAnyException();
        assertThatCode(() -> validador.validar(new MockMultipartFile("archivo", "foto.png", "image/png", con(PNG, 1000)))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Word, Excel u otras extensiones se rechazan")
    void otraExtension() {
        assertThatThrownBy(() -> validador.validar(new MockMultipartFile("archivo", "hoja.docx", null, con(PDF, 100))))
                .isInstanceOf(ApiException.class).hasMessageContaining("PDF o imágenes");
    }

    @Test
    @DisplayName("Un ejecutable renombrado a .pdf se rechaza (se revisa la firma del archivo)")
    void renombrado() {
        assertThatThrownBy(() -> validador.validar(new MockMultipartFile("archivo", "cedula.pdf", "application/pdf", con(EXE, 100))))
                .isInstanceOf(ApiException.class).hasMessageContaining("no es un PDF");
        // y una imagen con extensión .pdf tampoco
        assertThatThrownBy(() -> validador.validar(new MockMultipartFile("archivo", "cedula.pdf", "application/pdf", con(JPG, 100))))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Un PDF de más de 5 MB se rechaza con el tamaño en el mensaje")
    void muyGrande() {
        assertThatThrownBy(() -> validador.validar(new MockMultipartFile("archivo", "escaneo.pdf", "application/pdf", con(PDF, 6 * 1024 * 1024))))
                .isInstanceOf(ApiException.class).hasMessageContaining("máximo es 5 MB");
    }

    @Test
    @DisplayName("Un archivo vacío se rechaza")
    void vacio() {
        assertThatThrownBy(() -> validador.validar(new MockMultipartFile("archivo", "cedula.pdf", "application/pdf", new byte[0])))
                .isInstanceOf(ApiException.class).hasMessageContaining("vacío");
    }
}
