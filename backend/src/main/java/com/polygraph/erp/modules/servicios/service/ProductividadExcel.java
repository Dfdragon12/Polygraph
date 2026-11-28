package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.modules.servicios.dto.ProductividadResponse;
import com.polygraph.erp.shared.enums.Rol;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;

/** El reporte diario de productividad (SE-11) en Excel: una hoja para el personal y otra para los gestores. Fechas como «27 de octubre de 2026». */
public final class ProductividadExcel {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-CO"));

    private ProductividadExcel() {}

    public static byte[] generar(ProductividadResponse r) {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Font negrita = libro.createFont();
            negrita.setBold(true);
            CellStyle titulo = libro.createCellStyle();
            titulo.setFont(negrita);

            Sheet personal = libro.createSheet("Personal");
            fila(personal, 0, titulo, "Productividad del personal", FECHA.format(r.desde()) + " al " + FECHA.format(r.hasta()),
                    "Corte " + r.corte() + " (hora de Colombia)");
            fila(personal, 2, titulo, "Fecha", "Persona", "Perfil", "Asignados", "Finalizados", "Publicados", "Devueltos", "Reprogramados (por motivo)", "Abiertos al cierre");
            int n = 3;
            for (var p : r.personas()) {
                Row row = personal.createRow(n++);
                row.createCell(0).setCellValue(FECHA.format(p.fecha()));
                row.createCell(1).setCellValue(p.nombre());
                row.createCell(2).setCellValue(perfil(p.rol()));
                row.createCell(3).setCellValue(p.asignados());
                row.createCell(4).setCellValue(p.finalizados());
                row.createCell(5).setCellValue(p.publicados());
                row.createCell(6).setCellValue(p.devueltos());
                row.createCell(7).setCellValue(p.reprogramados() == null ? "—"
                        : p.reprogramados().isEmpty() ? "0" : p.reprogramados().entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining("; ")));
                row.createCell(8).setCellValue(p.abiertosAlCierre());
            }

            Sheet gestores = libro.createSheet("Gestores");
            fila(gestores, 0, titulo, "Cierre de los gestores", FECHA.format(r.desde()) + " al " + FECHA.format(r.hasta()),
                    "Corte " + r.corte() + " (hora de Colombia)");
            fila(gestores, 2, titulo, "Fecha", "Gestor", "Por verificar al cierre", "Servicios (total)", "Servicios por estado al cierre", "Estudios completos publicados",
                    "Vencidos al cierre", "Nota");
            n = 3;
            for (var g : r.gestores()) {
                Row row = gestores.createRow(n++);
                row.createCell(0).setCellValue(FECHA.format(g.fecha()));
                row.createCell(1).setCellValue(g.nombre());
                row.createCell(2).setCellValue(g.porVerificarAlCierre());
                row.createCell(3).setCellValue(g.totalServicios());
                row.createCell(4).setCellValue(g.serviciosPorEstado().stream().filter(e -> e.servicios() > 0)
                        .map(e -> e.nombre() + " " + e.servicios()).collect(Collectors.joining(" · ")));
                row.createCell(5).setCellValue(g.estudiosCompletosPublicados());
                row.createCell(6).setCellValue(g.vencidosAlCierre());
                row.createCell(7).setCellValue(g.vencidosAproximado() ? "Vencidos aproximado: usa la fecha de compromiso actual" : "");
            }
            for (int i = 0; i < 9; i++) { personal.autoSizeColumn(i); gestores.autoSizeColumn(i); }
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo armar el Excel de productividad", e);
        }
    }

    private static void fila(Sheet hoja, int indice, CellStyle estilo, String... valores) {
        Row row = hoja.createRow(indice);
        for (int i = 0; i < valores.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(valores[i]);
            c.setCellStyle(estilo);
        }
    }

    private static String perfil(String rol) {
        if (rol == null) return "";
        return switch (Rol.valueOf(rol)) {
            case ANALISTA_INTERNO -> "Analista";
            case POLIGRAFISTA -> "Poligrafista";
            case VISITADOR -> "Visitador";
            case PROGRAMADOR -> "Programador";
            case GESTOR -> "Gestor";
            default -> rol;
        };
    }

}
