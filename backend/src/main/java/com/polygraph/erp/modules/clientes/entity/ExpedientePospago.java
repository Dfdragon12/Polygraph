package com.polygraph.erp.modules.clientes.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Lo que se reúne en la pantalla «Convertir a pospago» (TP-13). Los documentos legales NO están aquí: se leen de Documentos. */
@Entity
@Table(name = "expediente_pospago")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpedientePospago {

    @Id
    @Column(name = "id_cliente")
    private Integer idCliente;

    @Column(name = "empleados_declarados")
    private Integer empleadosDeclarados;

    /** PILA reciente o certificación del revisor fiscal. */
    @Column(name = "soporte_empleados", length = 255)
    private String soporteEmpleados;

    @Column(name = "procesos_proyectados")
    private Integer procesosProyectados;

    /** Propuesta aceptada o proyección del contrato. */
    @Column(name = "soporte_procesos", length = 255)
    private String soporteProcesos;

    @Column(name = "estudio_credito_archivo", length = 255)
    private String estudioCreditoArchivo;

    @Column(name = "estudio_credito_fecha")
    private LocalDate estudioCreditoFecha;

    /** APROBADO o RECHAZADO. */
    @Column(name = "estudio_credito_resultado", length = 20)
    private String estudioCreditoResultado;

    @Column(name = "estudio_credito_por", length = 150)
    private String estudioCreditoPor;

    @Column(name = "contrato_archivo", length = 255)
    private String contratoArchivo;

    @Column(name = "cupo_solicitado", precision = 15, scale = 2)
    private BigDecimal cupoSolicitado;

    @Column(name = "dia_corte")
    private Integer diaCorte;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @Column(name = "actualizado_por", length = 150)
    private String actualizadoPor;
}
