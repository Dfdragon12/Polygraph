package com.polygraph.erp.modules.servicios.entity;

import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.shared.enums.EstadoServicio;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "servicios")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"cliente", "candidato", "proceso"})
public class Servicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_servicio")
    private Integer idServicio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_candidato")
    private Candidato candidato;

    @Column(name = "fecha_solicitud")
    private LocalDate fechaSolicitud;

    @Column(name = "hora_solicitud")
    private LocalTime horaSolicitud;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_proceso", nullable = false)
    private Proceso proceso;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 40)
    private EstadoServicio estado;

    @Column(name = "resultado", length = 100)
    private String resultado;

    @Column(name = "observacion", columnDefinition = "TEXT")
    private String observacion;

    @Column(name = "fecha_entrega_estudio")
    private LocalDate fechaEntregaEstudio;

    @Column(name = "fecha_de_envio")
    private LocalDate fechaDeEnvio;
}
