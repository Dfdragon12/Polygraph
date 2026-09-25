package com.polygraph.erp.modules.mensajes.entity;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.shared.enums.OrigenMensaje;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "mensajes_cliente_gestor")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"cliente", "emisor", "usuarioCliente"})
public class MensajeClienteGestor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    /** De quién es este hilo (el ADMIN_CLIENTE o ANALISTA_CLIENTE con el que habla el gestor) —
     *  distinto de "emisor": en un mensaje que escribió el gestor, emisor es el gestor pero el
     *  hilo sigue siendo el de este usuario-cliente puntual. Cada usuario del lado cliente tiene
     *  su propio hilo con el gestor, ya no comparten uno solo por empresa. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_cliente", nullable = false)
    private Usuario usuarioCliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_emisor", nullable = false)
    private Usuario emisor;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen", length = 10, nullable = false)
    private OrigenMensaje origen;

    @Column(name = "mensaje", columnDefinition = "TEXT", nullable = false)
    private String mensaje;

    @Column(name = "fecha_envio", nullable = false)
    private LocalDateTime fechaEnvio;

    @Column(name = "fecha_expiracion", nullable = false)
    private LocalDateTime fechaExpiracion;

    @Column(name = "leido", nullable = false)
    private Boolean leido;
}
