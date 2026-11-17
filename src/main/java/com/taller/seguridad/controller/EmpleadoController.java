package com.taller.seguridad.controller;

import com.taller.seguridad.model.Empleado;
import com.taller.seguridad.service.EmpleadoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/empleados")
public class EmpleadoController {

    private final EmpleadoService empleadoService;

    public EmpleadoController(EmpleadoService empleadoService) {
        this.empleadoService = empleadoService;
    }

    // Consultar todos (público / cualquier rol) → 200 OK
    @GetMapping
    public ResponseEntity<List<Empleado>> listar() {
        return ResponseEntity.ok(empleadoService.listarTodos());
    }

    // Consultar por ID (público / cualquier rol) → 200 OK | 404 Not Found
    @GetMapping("/{id}")
    public ResponseEntity<Empleado> buscarPorId(@PathVariable Long id) {
        return empleadoService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Crear empleado (ADMIN o USER) → 201 Created | 400 Bad Request (email duplicado / datos inválidos)
    @PostMapping
    public ResponseEntity<?> crear(@Valid @RequestBody Empleado empleado) {
        try {
            Empleado nuevo = empleadoService.guardar(empleado);
            URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                    .path("/{id}")
                    .buildAndExpand(nuevo.getId())
                    .toUri();
            return ResponseEntity.created(location).body(nuevo);
        } catch (IllegalArgumentException e) {
            // Email duplicado u otro dato inválido de negocio
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Editar empleado (ADMIN o USER) → 200 OK | 404 Not Found | 400 Bad Request
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id,
                                        @Valid @RequestBody Empleado empleado) {
        try {
            Empleado actualizado = empleadoService.actualizar(id, empleado);
            return ResponseEntity.ok(actualizado);
        } catch (NoSuchElementException e) {
            // Empleado no encontrado
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            // Email duplicado u otro dato inválido de negocio
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        // RuntimeException genérica NO se captura: sube como 500 para detectarla
    }

    // Eliminar empleado (SOLO ADMIN) → 204 No Content | 404 Not Found
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        try {
            empleadoService.eliminar(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            // Empleado no encontrado
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
        // RuntimeException genérica NO se captura: sube como 500 para detectarla
    }
}
