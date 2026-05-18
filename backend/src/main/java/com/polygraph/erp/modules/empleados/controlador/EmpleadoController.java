package com.polygraph.erp.modules.empleados.controlador;

import com.polygraph.erp.modules.empleados.entity.Empleados;
import com.polygraph.erp.modules.empleados.repository.EmpleadoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/empleados")
@CrossOrigin(origins = "*") 
public class EmpleadoController {

    private final EmpleadoRepository empleadoRepository;

    public EmpleadoController(EmpleadoRepository empleadoRepository) {
        this.empleadoRepository = empleadoRepository;
    }

    // GET - Listar todos
    @GetMapping
    public List<Empleados> listarTodos() {
        return empleadoRepository.findAll();
    }

    // GET - Buscar por email
    @GetMapping("/email/{email}")
    public ResponseEntity<Empleados> buscarPorEmail(@PathVariable String email) {
        Optional<Empleados> empleado = empleadoRepository.findByEmail(email);
        return empleado.map(ResponseEntity::ok)
                       .orElse(ResponseEntity.notFound().build());
    }

    // POST - Crear empleado
    @PostMapping
    public ResponseEntity<Empleados> crear(@RequestBody Empleados empleado) {
        Empleados nuevo = empleadoRepository.save(empleado);
        return ResponseEntity.ok(nuevo);
    }

    // PUT - Actualizar
    @PutMapping("/{id}")
    public ResponseEntity<Empleados> actualizar(@PathVariable Integer id, @RequestBody Empleados empleado) {
        if (!empleadoRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        empleado.setIdEmpleado(id);
        Empleados actualizado = empleadoRepository.save(empleado);
        return ResponseEntity.ok(actualizado);
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        if (!empleadoRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        empleadoRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
