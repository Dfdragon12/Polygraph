package com.polygraph.erp.modules.clientes.entity;

import com.polygraph.erp.shared.enums.EstadoSoporte;
import com.polygraph.erp.shared.enums.TipoSoporte;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Documento legal/soporte que el cliente carga (cédula, RUT, cámara de comercio, habeas data). */
@Entity
@Table(name = "soportes_clientes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoporteCliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_soporte")
    private Integer idSoporte;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_soporte", nullable = false, length = 100)
    private TipoSoporte tipoSoporte;

    @Column(name = "fecha_solicitud")
    private LocalDate fechaSolicitud;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @Column(name = "fecha_entrega")
    private LocalDate fechaEntrega;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 50)
    private EstadoSoporte estado;

    @Column(name = "archivo_adjunto", length = 255)
    private String archivoAdjunto;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "usuario_registra", length = 50)
    private String usuarioRegistra;

    @Column(name = "validado_por", length = 50)
    private String validadoPor;

    @Column(name = "fecha_validacion")
    private LocalDateTime fechaValidacion;
}
