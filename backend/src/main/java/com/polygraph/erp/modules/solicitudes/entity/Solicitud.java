package com.polygraph.erp.modules.solicitudes.entity;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.shared.enums.EstadoServicio;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "solicitudes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"cliente", "candidato", "usuarioSolicita", "servicios"})
public class Solicitud {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_solicitud")
    private Long idSolicitud;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_candidato")
    private Candidato candidato;

    @Column(name = "cedula_evaluado", nullable = false, length = 20)
    private String cedulaEvaluado;

    @Column(name = "nombres_evaluado", nullable = false, length = 200)
    private String nombresEvaluado;

    @Column(name = "apellidos_evaluado", nullable = false, length = 200)
    private String apellidosEvaluado;

    @Column(name = "celular_evaluado", length = 20)
    private String celularEvaluado;

    @Column(name = "email_evaluado", length = 150)
    private String emailEvaluado;

    @Column(name = "ciudad_evaluado", length = 150)
    private String ciudadEvaluado;

    @Column(name = "cargo", length = 150)
    private String cargo;

    @Column(name = "notas", columnDefinition = "TEXT")
    private String notas;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 40)
    private EstadoServicio estado;

    @Column(name = "fecha_solicitud")
    private LocalDateTime fechaSolicitud;

    @Column(name = "fecha_entrega_estimada")
    private LocalDate fechaEntregaEstimada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_solicita", nullable = false)
    private Usuario usuarioSolicita;

    @OneToMany(mappedBy = "solicitud", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SolicitudServicio> servicios = new ArrayList<>();
}
