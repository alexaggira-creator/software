-- ====================================================================
-- TALLER FINAL: DESARROLLO DE SOFTWARE SEGURO
-- Script DDL de Base de Datos para MySQL Server (Motor Oficial)
-- Tablas requeridas: usuarios, roles, roles_usuarios, empleados
-- ====================================================================
-- NOTA: Este script está preparado para una ejecución segura y no destructiva.
-- Utiliza "CREATE TABLE IF NOT EXISTS" e inserciones controladas ("ON DUPLICATE KEY UPDATE"
-- e "INSERT IGNORE") para no borrar datos existentes en caso de re-ejecución accidental.
-- ====================================================================

-- 1. Creación segura de la base de datos
CREATE DATABASE IF NOT EXISTS seguridaddb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE seguridaddb;

-- 2. Tabla: roles
CREATE TABLE IF NOT EXISTS roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE
) ENGINE=InnoDB;

-- 3. Tabla: usuarios
CREATE TABLE IF NOT EXISTS usuarios (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

-- 4. Tabla intermedia: roles_usuarios (Relación Muchos a Muchos)
CREATE TABLE IF NOT EXISTS roles_usuarios (
    usuario_id BIGINT NOT NULL,
    rol_id BIGINT NOT NULL,
    PRIMARY KEY (usuario_id, rol_id),
    CONSTRAINT fk_roles_usuarios_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE,
    CONSTRAINT fk_roles_usuarios_rol FOREIGN KEY (rol_id) REFERENCES roles (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 5. Tabla: empleados (Para el CRUD con control de acceso)
CREATE TABLE IF NOT EXISTS empleados (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100) NOT NULL,
    email VARCHAR(120) NOT NULL UNIQUE,
    cargo VARCHAR(100) NOT NULL,
    salario DECIMAL(12, 2) NOT NULL
) ENGINE=InnoDB;

-- ====================================================================
-- DATOS INICIALES (SEMILLA NO DESTRUCTIVA)
-- Hashes reales generados con BCryptPasswordEncoder (fuerza 10) de Spring Security:
-- admin / admin123 -> $2a$10$dZ7PhN9QGnh1zruy2oMpL.budVTSJrHQJVG/NPqubK7Ll5xmJtZNa
-- user / user123   -> $2a$10$K1hLdiMJ7kErWNENPjLb2eNTnZd8Kjr4I5l9rfZj6BiW/JdT2l1ia
-- ====================================================================

-- Inserción segura de Roles requeridos
INSERT INTO roles (id, nombre) VALUES 
(1, 'ADMIN'),
(2, 'USER')
ON DUPLICATE KEY UPDATE nombre = VALUES(nombre);

-- Inserción segura de Usuarios con contraseñas encriptadas en BCrypt
INSERT INTO usuarios (id, username, password, enabled) VALUES 
(1, 'admin', '$2a$10$dZ7PhN9QGnh1zruy2oMpL.budVTSJrHQJVG/NPqubK7Ll5xmJtZNa', 1),
(2, 'user',  '$2a$10$K1hLdiMJ7kErWNENPjLb2eNTnZd8Kjr4I5l9rfZj6BiW/JdT2l1ia', 1)
ON DUPLICATE KEY UPDATE password = VALUES(password), enabled = VALUES(enabled);

-- Asignación segura de Roles a Usuarios
INSERT IGNORE INTO roles_usuarios (usuario_id, rol_id) VALUES 
(1, 1), -- admin tiene rol ADMIN
(2, 2); -- user tiene rol USER (usuario normal)

-- Inserción segura de Empleados iniciales
INSERT INTO empleados (id, nombre, apellido, email, cargo, salario) VALUES 
(1, 'Carlos', 'Gómez', 'carlos.gomez@empresa.com', 'Desarrollador Backend', 4500000.00),
(2, 'Ana', 'Martínez', 'ana.martinez@empresa.com', 'Líder Técnico de Seguridad', 7200000.00),
(3, 'Juan', 'Pérez', 'juan.perez@empresa.com', 'Analista QA', 3200000.00)
ON DUPLICATE KEY UPDATE cargo = VALUES(cargo), salario = VALUES(salario);
