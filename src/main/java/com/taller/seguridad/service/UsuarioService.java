package com.taller.seguridad.service;

import com.taller.seguridad.dto.UsuarioRequestDto;
import com.taller.seguridad.dto.UsuarioResponseDto;
import com.taller.seguridad.dto.UsuarioUpdateDto;
import com.taller.seguridad.model.Rol;
import com.taller.seguridad.model.Usuario;
import com.taller.seguridad.repository.RolRepository;
import com.taller.seguridad.repository.UsuarioRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository,
                          RolRepository rolRepository,
                          PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponseDto> listarTodos() {
        return usuarioRepository.findAll().stream()
                .map(this::convertirAResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<UsuarioResponseDto> buscarPorId(Long id) {
        return usuarioRepository.findById(id).map(this::convertirAResponseDto);
    }

    @Transactional
    public UsuarioResponseDto registrarUsuario(UsuarioRequestDto dto) {
        if (dto.getUsername() == null || dto.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de usuario es obligatorio.");
        }
        if (usuarioRepository.existsByUsername(dto.getUsername().trim())) {
            throw new IllegalArgumentException("El nombre de usuario '" + dto.getUsername().trim() + "' ya existe.");
        }
        if (dto.getPassword() == null || dto.getPassword().trim().length() < 6) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 6 caracteres.");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean esAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        Usuario usuario = new Usuario();
        usuario.setUsername(dto.getUsername().trim());
        usuario.setPassword(passwordEncoder.encode(dto.getPassword().trim()));
        usuario.setEnabled(true);

        Set<Rol> rolesAsignados = new HashSet<>();
        if (esAdmin && dto.getRoles() != null && !dto.getRoles().isEmpty()) {
            for (String rolNombre : dto.getRoles()) {
                String cleanName = rolNombre.toUpperCase().replace("ROLE_", "").trim();
                Rol rol = rolRepository.findByNombre(cleanName)
                        .orElseGet(() -> rolRepository.save(new Rol(cleanName)));
                rolesAsignados.add(rol);
            }
        } else {
            // Un usuario normal solo puede crear cuentas con rol USER (evita escalamiento de privilegios)
            Rol rolUser = rolRepository.findByNombre("USER")
                    .orElseGet(() -> rolRepository.save(new Rol("USER")));
            rolesAsignados.add(rolUser);
        }

        usuario.setRoles(rolesAsignados);
        Usuario guardado = usuarioRepository.save(usuario);
        return convertirAResponseDto(guardado);
    }

    @Transactional
    public UsuarioResponseDto actualizarUsuario(Long id, UsuarioUpdateDto dto) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + id));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean esAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        String usuarioActual = (auth != null && auth.getName() != null) ? auth.getName() : "";

        // Reglas de Control de Acceso Estricto para Modificación de Cuentas:
        // 1. Si NO es ADMIN, solo puede modificar su PROPIA cuenta
        if (!esAdmin) {
            if (!existente.getUsername().equalsIgnoreCase(usuarioActual)) {
                throw new AccessDeniedException("Acceso denegado: Un usuario con rol USER únicamente puede modificar su propia cuenta.");
            }
            // 2. Un usuario normal nunca puede modificar una cuenta con rol ADMIN
            boolean esCuentaAdmin = existente.getRoles().stream()
                    .anyMatch(r -> r.getNombre().equalsIgnoreCase("ADMIN"));
            if (esCuentaAdmin) {
                throw new AccessDeniedException("Acceso denegado: No tiene permisos para modificar una cuenta con privilegios administrativos.");
            }
        }

        // Actualizar username si se proporciona y cambió
        if (dto.getUsername() != null && !dto.getUsername().trim().isEmpty()) {
            String nuevoUsername = dto.getUsername().trim();
            if (!existente.getUsername().equalsIgnoreCase(nuevoUsername)) {
                if (usuarioRepository.existsByUsername(nuevoUsername)) {
                    throw new IllegalArgumentException("El nombre de usuario '" + nuevoUsername + "' ya está en uso.");
                }
                // Si la cuenta es 'admin' raíz, proteger el username
                if ("admin".equalsIgnoreCase(existente.getUsername()) && !nuevoUsername.equalsIgnoreCase("admin")) {
                    throw new IllegalArgumentException("No se permite cambiar el username de la cuenta de administración principal.");
                }
                existente.setUsername(nuevoUsername);
            }
        }

        // Actualizar contraseña con BCrypt si se proporciona
        if (dto.getPassword() != null && !dto.getPassword().trim().isEmpty()) {
            if (dto.getPassword().trim().length() < 6) {
                throw new IllegalArgumentException("La contraseña debe tener al menos 6 caracteres.");
            }
            existente.setPassword(passwordEncoder.encode(dto.getPassword().trim()));
        }

        // Actualizar estado habilitado (solo ADMIN puede habilitar/deshabilitar)
        if (dto.getEnabled() != null) {
            if (esAdmin) {
                if ("admin".equalsIgnoreCase(existente.getUsername()) && Boolean.FALSE.equals(dto.getEnabled())) {
                    throw new IllegalArgumentException("No se puede deshabilitar la cuenta del administrador principal.");
                }
                existente.setEnabled(dto.getEnabled());
            } else if (!dto.getEnabled().equals(existente.isEnabled())) {
                throw new AccessDeniedException("Acceso denegado: Solo el administrador puede modificar el estado de habilitación de cuentas.");
            }
        }

        // Actualizar roles: SOLO ADMIN puede cambiar roles; USER nunca puede cambiarse a ADMIN ni asignar roles
        if (dto.getRoles() != null && !dto.getRoles().isEmpty()) {
            if (esAdmin) {
                Set<Rol> nuevosRoles = new HashSet<>();
                for (String rolNombre : dto.getRoles()) {
                    String cleanName = rolNombre.toUpperCase().replace("ROLE_", "").trim();
                    Rol rol = rolRepository.findByNombre(cleanName)
                            .orElseGet(() -> rolRepository.save(new Rol(cleanName)));
                    nuevosRoles.add(rol);
                }
                existente.setRoles(nuevosRoles);
            } else {
                // Si un usuario normal intenta alterar roles, se verifica si intenta elevar privilegios
                boolean intentaAdmin = dto.getRoles().stream()
                        .anyMatch(r -> r.toUpperCase().contains("ADMIN"));
                if (intentaAdmin) {
                    throw new AccessDeniedException("Acceso denegado: No tiene permisos para asignarse privilegios administrativos.");
                }
            }
        }

        Usuario actualizado = usuarioRepository.save(existente);
        return convertirAResponseDto(actualizado);
    }

    public UsuarioResponseDto convertirAResponseDto(Usuario usuario) {
        Set<String> rolesNombres = usuario.getRoles().stream()
                .map(Rol::getNombre)
                .collect(Collectors.toSet());
        return new UsuarioResponseDto(
                usuario.getId(),
                usuario.getUsername(),
                usuario.isEnabled(),
                rolesNombres
        );
    }
}
