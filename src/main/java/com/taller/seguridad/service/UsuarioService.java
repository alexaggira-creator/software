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

    /** Roles permitidos en el sistema. No se crean roles fuera de este conjunto. */
    private static final Set<String> ROLES_VALIDOS = Set.of("ADMIN", "USER");

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

    // ─── Consultas ───────────────────────────────────────────────────────────

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

    // ─── Crear usuario ────────────────────────────────────────────────────────

    @Transactional
    public UsuarioResponseDto registrarUsuario(UsuarioRequestDto dto) {
        // Validar username
        if (dto.getUsername() == null || dto.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de usuario es obligatorio.");
        }
        if (usuarioRepository.existsByUsername(dto.getUsername().trim())) {
            throw new IllegalArgumentException("El nombre de usuario '" + dto.getUsername().trim() + "' ya existe.");
        }
        // Validar contraseña: no usar trim() — se conservan exactamente los caracteres del usuario
        if (dto.getPassword() == null || dto.getPassword().isEmpty() || dto.getPassword().length() < 6) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 6 caracteres.");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean esAdmin = esAdmin(auth);

        Usuario usuario = new Usuario();
        usuario.setUsername(dto.getUsername().trim());
        usuario.setPassword(passwordEncoder.encode(dto.getPassword())); // sin trim(): se cifra tal como viene
        usuario.setEnabled(true);

        Set<Rol> rolesAsignados = new HashSet<>();
        if (esAdmin && dto.getRoles() != null && !dto.getRoles().isEmpty()) {
            // ADMIN puede crear con cualquier rol válido
            for (String rolNombre : dto.getRoles()) {
                Rol rol = resolverRolValido(rolNombre);
                rolesAsignados.add(rol);
            }
        } else if (!esAdmin && dto.getRoles() != null && !dto.getRoles().isEmpty()) {
            // USER no puede autoasignarse ADMIN
            for (String rolNombre : dto.getRoles()) {
                String cleanName = normalizar(rolNombre);
                if ("ADMIN".equals(cleanName)) {
                    throw new AccessDeniedException("Acceso denegado: Un usuario USER no puede crear cuentas con rol ADMIN.");
                }
                // Solo permite USER
                if (!"USER".equals(cleanName)) {
                    throw new IllegalArgumentException("Rol desconocido: '" + rolNombre + "'. Roles válidos: ADMIN, USER.");
                }
            }
            rolesAsignados.add(resolverRolValido("USER"));
        } else {
            // Sin roles especificados → USER por defecto
            rolesAsignados.add(resolverRolValido("USER"));
        }

        usuario.setRoles(rolesAsignados);
        return convertirAResponseDto(usuarioRepository.save(usuario));
    }

    // ─── Actualizar usuario ───────────────────────────────────────────────────

    @Transactional
    public UsuarioResponseDto actualizarUsuario(Long id, UsuarioUpdateDto dto) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado con ID: " + id));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean esAdmin = esAdmin(auth);
        String usuarioActual = (auth != null && auth.getName() != null) ? auth.getName() : "";

        // Control de acceso: USER solo puede modificar su propia cuenta
        if (!esAdmin) {
            if (!existente.getUsername().equalsIgnoreCase(usuarioActual)) {
                throw new AccessDeniedException(
                        "Acceso denegado: Un usuario USER únicamente puede modificar su propia cuenta.");
            }
            // USER no puede modificar una cuenta con rol ADMIN
            boolean esCuentaAdmin = existente.getRoles().stream()
                    .anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getNombre()));
            if (esCuentaAdmin) {
                throw new AccessDeniedException(
                        "Acceso denegado: No tiene permisos para modificar una cuenta con privilegios administrativos.");
            }
        }

        // Actualizar username
        if (dto.getUsername() != null && !dto.getUsername().trim().isEmpty()) {
            String nuevoUsername = dto.getUsername().trim();
            if (!existente.getUsername().equalsIgnoreCase(nuevoUsername)) {
                // Proteger username de cuenta admin principal
                if ("admin".equalsIgnoreCase(existente.getUsername())) {
                    throw new IllegalArgumentException(
                            "No se permite cambiar el username de la cuenta de administración principal.");
                }
                if (usuarioRepository.existsByUsername(nuevoUsername)) {
                    throw new IllegalArgumentException("El nombre de usuario '" + nuevoUsername + "' ya está en uso.");
                }
                existente.setUsername(nuevoUsername);
            }
        }

        // Actualizar contraseña con BCrypt — sin trim(): se cifra exactamente lo que envió el usuario
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            if (dto.getPassword().length() < 6) {
                throw new IllegalArgumentException("La contraseña debe tener al menos 6 caracteres.");
            }
            existente.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        // Actualizar estado: solo ADMIN puede cambiar enabled
        if (dto.getEnabled() != null) {
            if (esAdmin) {
                if ("admin".equalsIgnoreCase(existente.getUsername()) && Boolean.FALSE.equals(dto.getEnabled())) {
                    throw new IllegalArgumentException("No se puede deshabilitar la cuenta del administrador principal.");
                }
                existente.setEnabled(dto.getEnabled());
            } else if (!dto.getEnabled().equals(existente.isEnabled())) {
                throw new AccessDeniedException(
                        "Acceso denegado: Solo el administrador puede modificar el estado de habilitación de cuentas.");
            }
        }

        // Actualizar roles: SOLO ADMIN puede cambiar roles
        if (dto.getRoles() != null && !dto.getRoles().isEmpty()) {
            if (esAdmin) {
                // Proteger rol ADMIN de la cuenta principal
                if ("admin".equalsIgnoreCase(existente.getUsername())) {
                    boolean conservaAdmin = dto.getRoles().stream()
                            .map(this::normalizar)
                            .anyMatch("ADMIN"::equals);
                    if (!conservaAdmin) {
                        throw new IllegalArgumentException(
                                "No se puede quitar el rol ADMIN de la cuenta de administración principal.");
                    }
                }
                Set<Rol> nuevosRoles = new HashSet<>();
                for (String rolNombre : dto.getRoles()) {
                    nuevosRoles.add(resolverRolValido(rolNombre));
                }
                existente.setRoles(nuevosRoles);
            } else {
                // USER intenta modificar roles → siempre denegado
                throw new AccessDeniedException(
                        "Acceso denegado: No tiene permisos para modificar roles de una cuenta.");
            }
        }

        return convertirAResponseDto(usuarioRepository.save(existente));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Normaliza un nombre de rol:
     * - Lanza IllegalArgumentException si el nombre es null o está en blanco.
     * - Convierte a mayúsculas con Locale.ROOT para evitar problemas de localización.
     * - Elimina el prefijo ROLE_ solo si aparece al principio del nombre.
     * - Recorta espacios al inicio y al final.
     */
    private String normalizar(String rolNombre) {
        if (rolNombre == null || rolNombre.isBlank()) {
            throw new IllegalArgumentException(
                    "El nombre del rol no puede ser nulo ni estar vacío.");
        }
        String upper = rolNombre.trim().toUpperCase(java.util.Locale.ROOT);
        if (upper.startsWith("ROLE_")) {
            upper = upper.substring(5); // elimina "ROLE_" solo si está al principio
        }
        return upper;
    }

    /**
     * Busca el rol en BD. Lanza IllegalArgumentException si el nombre no es válido.
     * NUNCA crea roles nuevos con nombres arbitrarios.
     */
    private Rol resolverRolValido(String rolNombre) {
        String cleanName = normalizar(rolNombre);
        if (!ROLES_VALIDOS.contains(cleanName)) {
            throw new IllegalArgumentException(
                    "Rol desconocido: '" + rolNombre + "'. Roles válidos: ADMIN, USER.");
        }
        return rolRepository.findByNombre(cleanName)
                .orElseThrow(() -> new IllegalStateException(
                        "El rol '" + cleanName + "' no existe en la base de datos."));
    }

    private boolean esAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
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

    // Excepción específica para "no encontrado" (diferente de RuntimeException genérica)
    public static class NoSuchElementException extends RuntimeException {
        public NoSuchElementException(String message) {
            super(message);
        }
    }
}
