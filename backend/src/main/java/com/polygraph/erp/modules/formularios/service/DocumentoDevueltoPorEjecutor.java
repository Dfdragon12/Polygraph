package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.shared.enums.EstadoRevisionDocumento;

/** Analista/poligrafista/visitador devolvió un documento del candidato (Denegado, Ilegible,
 *  Vencido, Incompleto o No corresponde) desde el paso "Soportes" de SU subproceso. Lo escucha
 *  NovedadSubprocesoService para registrar la novedad automática del tipo correspondiente (va por
 *  evento para no acoplar formularios ↔ gestor en un ciclo de dependencias). Se atiende en la MISMA
 *  transacción: si la novedad falla, no queda la devolución a medias. */
public record DocumentoDevueltoPorEjecutor(Long idServicioSubproceso, String nombreDocumento,
                                           EstadoRevisionDocumento resultado, String motivo, Usuario actor) {}
