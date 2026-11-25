package com.polygraph.erp.modules.tienda.controller;

import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.tienda.service.PrecioPospagoService;
import com.polygraph.erp.modules.tienda.service.PrecioPospagoService.Fila;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** LP-06 #14: el Excel de la lista pospago trae Plan de 100, Origen, Ofrecido, Actualizado por y Actualizado en. */
@DisplayName("LP-06 — exportar la lista pospago")
class LP06ExportarListaPospagoTest {

    @Test
    void elExcelTraeLasColumnasNuevas() throws Exception {
        var servicio = mock(PrecioPospagoService.class);
        var manual = new Fila(1, "Poligrafía Pre Empleo", null, new BigDecimal("168000"), "MANUAL", new BigDecimal("160000"), new BigDecimal("160000"), true,
                new BigDecimal("30400"), new BigDecimal("190400"), true, new BigDecimal("40.0"), false, true, "Ana Admin", LocalDateTime.of(2026, 11, 24, 14, 45));
        var sinPrecio = new Fila(2, "Visita Rutina", null, null, "AUTO", null, null, true, null, null, false, null, false, false, null, null);
        when(servicio.lista()).thenReturn(List.of(manual, sinPrecio));
        var controlador = new PospagoPreciosController(servicio, mock(UsuarioRepository.class), mock(ClienteRepository.class));

        var cuerpo = controlador.exportar().getBody();
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(cuerpo))) {
            Sheet hoja = libro.getSheetAt(0);
            Row cab = hoja.getRow(0);
            var titulos = new java.util.ArrayList<String>();
            cab.forEach(c -> titulos.add(c.getStringCellValue()));
            assertThat(titulos).contains("Plan de 100 (referencia)", "Origen", "Ofrecido", "Actualizado por", "Actualizado en");

            Row a = hoja.getRow(1);
            assertThat(a.getCell(2).getStringCellValue()).isEqualTo("Manual");
            assertThat(a.getCell(5).getStringCellValue()).isEqualTo("Sí");
            assertThat(a.getCell(8).getNumericCellValue()).isEqualTo(168000d);
            assertThat(a.getCell(9).getStringCellValue()).isEqualTo("Ana Admin");
            assertThat(a.getCell(10).getStringCellValue()).isEqualTo("24/11/2026 14:45");

            Row b = hoja.getRow(2);
            assertThat(b.getCell(2).getStringCellValue()).isEqualTo("Automático");
            assertThat(b.getCell(5).getStringCellValue()).isEqualTo("No");
            assertThat(b.getCell(7).getStringCellValue()).isEqualTo("Sin precio");
        }
    }
}
