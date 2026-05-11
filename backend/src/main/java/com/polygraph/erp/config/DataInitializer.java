package com.polygraph.erp.config;

import com.polygraph.erp.modules.servicios.entity.CatalogoServicio;
import com.polygraph.erp.modules.servicios.repository.CatalogoServicioRepository;
import com.polygraph.erp.shared.enums.CategoriaServicio;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private final CatalogoServicioRepository catalogoRepository;

    @PostConstruct
    public void inicializar() {
        if (catalogoRepository.count() > 0) {
            return;
        }
        log.info("Cargando catálogo inicial de servicios Polygraph...");
        catalogoRepository.saveAll(construirCatalogo());
        log.info("Catálogo cargado: {} servicios registrados.", catalogoRepository.count());
    }

    private List<CatalogoServicio> construirCatalogo() {
        return List.of(
                // ── PRUEBAS DE CONFIABILIDAD ────────────────────────────────────────────
                item("Polígrafo Pre-empleo",
                        "Examen poligráfico para candidatos en proceso de selección de personal.",
                        CategoriaServicio.PRUEBAS_CONFIABILIDAD, 1),
                item("Polígrafo Rutina",
                        "Examen poligráfico periódico aplicado a empleados activos de la empresa.",
                        CategoriaServicio.PRUEBAS_CONFIABILIDAD, 2),
                item("Polígrafo Específico",
                        "Examen poligráfico orientado a investigar un hecho o situación concreta.",
                        CategoriaServicio.PRUEBAS_CONFIABILIDAD, 3),
                item("Verifeye",
                        "Prueba de detección de engaño mediante análisis del movimiento ocular.",
                        CategoriaServicio.PRUEBAS_CONFIABILIDAD, 4),

                // ── ESTUDIOS DE SEGURIDAD ────────────────────────────────────────────────
                item("Estudio Básico",
                        "Verificación de antecedentes judiciales y referencias básicas del candidato.",
                        CategoriaServicio.ESTUDIOS_SEGURIDAD, 1),
                item("Estudio Avanzado",
                        "Estudio completo: verificación laboral, académica, financiera y personal.",
                        CategoriaServicio.ESTUDIOS_SEGURIDAD, 2),
                item("Estudio Quick",
                        "Validación express de antecedentes con entrega en 24 horas hábiles.",
                        CategoriaServicio.ESTUDIOS_SEGURIDAD, 3),
                item("Estudio OEA",
                        "Estudio de seguridad especial para Operadores Económicos Autorizados (DIAN).",
                        CategoriaServicio.ESTUDIOS_SEGURIDAD, 4),
                item("Combo Básico + Visita",
                        "Estudio básico de antecedentes combinado con visita domiciliaria.",
                        CategoriaServicio.ESTUDIOS_SEGURIDAD, 5),
                item("Combo Avanzado + Polígrafo",
                        "Estudio avanzado completo combinado con examen poligráfico.",
                        CategoriaServicio.ESTUDIOS_SEGURIDAD, 6),

                // ── HOJA DE VIDA ─────────────────────────────────────────────────────────
                item("Verificación de Antecedentes",
                        "Consulta de antecedentes judiciales, penales, fiscales y disciplinarios.",
                        CategoriaServicio.HOJA_VIDA, 1),
                item("Validación Laboral",
                        "Verificación de referencias y experiencia laboral declarada en la hoja de vida.",
                        CategoriaServicio.HOJA_VIDA, 2),
                item("Validación Académica",
                        "Verificación de títulos, diplomas y certificados académicos ante las instituciones.",
                        CategoriaServicio.HOJA_VIDA, 3),
                item("Validación Personal",
                        "Verificación de referencias personales y datos básicos de identificación.",
                        CategoriaServicio.HOJA_VIDA, 4),
                item("Visita Domiciliaria",
                        "Visita al entorno familiar y habitacional del candidato con informe detallado.",
                        CategoriaServicio.HOJA_VIDA, 5),

                // ── OTROS ────────────────────────────────────────────────────────────────
                item("Estudio Persona Jurídica",
                        "Estudio de confiabilidad y seguridad aplicado a personas jurídicas y empresas.",
                        CategoriaServicio.OTROS, 1),
                item("Psicotécnicas",
                        "Aplicación de pruebas psicotécnicas de aptitudes y competencias laborales.",
                        CategoriaServicio.OTROS, 2)
        );
    }

    private CatalogoServicio item(String nombre, String descripcion, CategoriaServicio categoria, int orden) {
        return CatalogoServicio.builder()
                .nombre(nombre)
                .descripcion(descripcion)
                .categoria(categoria)
                .orden(orden)
                .activo(true)
                .build();
    }
}
