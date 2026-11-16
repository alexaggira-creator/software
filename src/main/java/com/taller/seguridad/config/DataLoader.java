package com.taller.seguridad.config;

import com.taller.seguridad.model.Empleado;
import com.taller.seguridad.model.Rol;
import com.taller.seguridad.model.Usuario;
import com.taller.seguridad.repository.EmpleadoRepository;
import com.taller.seguridad.repository.RolRepository;
import com.taller.seguridad.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Set;

@Configuration
public class DataLoader {

    @Bean
    public CommandLineRunner initData(
            RolRepository rolRepository,
            UsuarioRepository usuarioRepository,
            EmpleadoRepository empleadoRepository,
            PasswordEncoder passwordEncoder) {
        return args -> {
            // 1. Crear roles: ADMIN y USER si no existen
            Rol rolAdmin = rolRepository.findByNombre("ADMIN")
                    .orElseGet(() -> rolRepository.save(new Rol("ADMIN")));

            Rol rolUser = rolRepository.findByNombre("USER")
                    .orElseGet(() -> rolRepository.save(new Rol("USER")));

            // 2. Crear usuario ADMIN si no existe (contraseña: admin123)
            if (!usuarioRepository.existsByUsername("admin")) {
                Usuario admin = new Usuario();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setEnabled(true);
                admin.setRoles(Set.of(rolAdmin));
                usuarioRepository.save(admin);
            }

            // 3. Crear usuario normal si no existe (contraseña: user123)
            if (!usuarioRepository.existsByUsername("user")) {
                Usuario user = new Usuario();
                user.setUsername("user");
                user.setPassword(passwordEncoder.encode("user123"));
                user.setEnabled(true);
                user.setRoles(Set.of(rolUser));
                usuarioRepository.save(user);
            }

            // 4. Cargar empleados iniciales si la tabla está vacía
            if (empleadoRepository.count() == 0) {
                empleadoRepository.save(new Empleado(
                        "Carlos",
                        "Gómez",
                        "carlos.gomez@empresa.com",
                        "Desarrollador Backend",
                        new BigDecimal("4500000.00")
                ));
                empleadoRepository.save(new Empleado(
                        "Ana",
                        "Martínez",
                        "ana.martinez@empresa.com",
                        "Líder Técnico de Seguridad",
                        new BigDecimal("7200000.00")
                ));
                empleadoRepository.save(new Empleado(
                        "Juan",
                        "Pérez",
                        "juan.perez@empresa.com",
                        "Analista QA",
                        new BigDecimal("3200000.00")
                ));
            }
        };
    }
}
