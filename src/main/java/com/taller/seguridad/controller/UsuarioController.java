package com.taller.seguridad.controller;

import com.taller.seguridad.dto.UsuarioRequestDto;
import com.taller.seguridad.dto.UsuarioResponseDto;
import com.taller.seguridad.dto.UsuarioUpdateDto;
import com.taller.seguridad.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    // Listar usuarios: ADMIN o USER autenticados
    @GetMapping
    public ResponseEntity<List<UsuarioResponseDto>> listar() {
        return ResponseEntity.ok(usuarioService.listarTodos());
    }

    // Consultar usuario por ID
    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponseDto> buscarPorId(@PathVariable Long id) {
        return usuarioService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Crear usuario: ADMIN y USER (Usuario normal)
    @PostMapping
    public ResponseEntity<?> registrar(@Valid @RequestBody UsuarioRequestDto dto) {
        try {
            UsuarioResponseDto usuario = usuarioService.registrarUsuario(dto);
            return new ResponseEntity<>(usuario, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Editar usuario: ADMIN y USER (Usuario normal)
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @Valid @RequestBody UsuarioUpdateDto dto) {
        try {
            UsuarioResponseDto usuario = usuarioService.actualizarUsuario(id, dto);
            return ResponseEntity.ok(usuario);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
}
