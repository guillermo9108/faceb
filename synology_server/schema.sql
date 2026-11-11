-- =========================================================
-- BASE DE DATOS PARA MURO FAMILIAR LAN EN SYNOLOGY MARIADB 10
-- =========================================================
CREATE DATABASE IF NOT EXISTS family_feed 
CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

USE family_feed;

-- 1. Tabla de Miembros Familiares
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    role VARCHAR(50) NOT NULL,
    pin_hash VARCHAR(255) NOT NULL,
    avatar_emoji VARCHAR(10) DEFAULT '👤',
    avatar_color_hex BIGINT DEFAULT 4280391411,
    bio VARCHAR(255) DEFAULT '',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 2. Tabla de Grupos Familiares (Carpetas del NAS)
CREATE TABLE IF NOT EXISTS family_groups (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    folder_path VARCHAR(190) NOT NULL,
    description TEXT NULL,
    cover_photo_url VARCHAR(255) NULL,
    created_by_user_id INT DEFAULT 1,
    posts_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_group_folder (folder_path)
) ENGINE=InnoDB;

-- Vista para compatibilidad total con consultas a 'groups'
CREATE OR REPLACE VIEW `groups` AS SELECT * FROM family_groups;

-- 3. Tabla de Publicaciones (Muro)
CREATE TABLE IF NOT EXISTS posts (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    group_id INT NULL DEFAULT NULL,
    content_text TEXT NULL,
    feeling VARCHAR(100) NULL,
    location VARCHAR(100) NULL,
    is_pinned TINYINT(1) DEFAULT 0,
    is_short TINYINT(1) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_post_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_post_group FOREIGN KEY (group_id) REFERENCES family_groups(id) ON DELETE SET NULL,
    INDEX idx_created (created_at DESC),
    INDEX idx_group (group_id)
) ENGINE=InnoDB;

-- 3. Tabla de Multimedia (Fotos y Vídeos en el NAS)
CREATE TABLE IF NOT EXISTS media (
    id INT AUTO_INCREMENT PRIMARY KEY,
    post_id INT NOT NULL,
    media_type ENUM('IMAGE', 'VIDEO') NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    thumb_path VARCHAR(255) NOT NULL,
    duration_label VARCHAR(20) NULL,
    is_chunked TINYINT(1) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_media_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 3.1. Tabla de Fragmentos de Vídeo (Chunked Uploads)
CREATE TABLE IF NOT EXISTS media_chunks (
    id INT AUTO_INCREMENT PRIMARY KEY,
    post_id INT NOT NULL,
    chunk_index INT NOT NULL,
    total_chunks INT NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    duration_ms INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_chunk_post (post_id),
    CONSTRAINT fk_chunk_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 4. Tabla de Comentarios
CREATE TABLE IF NOT EXISTS comments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    post_id INT NOT NULL,
    user_id INT NOT NULL,
    comment_text TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_comm_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_comm_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 5. Tabla de Reacciones
CREATE TABLE IF NOT EXISTS reactions (
    post_id INT NOT NULL,
    user_id INT NOT NULL,
    reaction_type VARCHAR(20) DEFAULT 'LIKE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id, user_id),
    CONSTRAINT fk_rx_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_rx_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 6. Tabla de Historias Independientes (Stories)
CREATE TABLE IF NOT EXISTS stories (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    caption VARCHAR(255) NULL,
    story_type ENUM('IMAGE', 'TEXT', 'VIDEO') DEFAULT 'IMAGE',
    file_path VARCHAR(255) NULL,
    thumb_path VARCHAR(255) NULL,
    bg_color_hex BIGINT DEFAULT 4280171146,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_story_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_story_created (created_at DESC)
) ENGINE=InnoDB;

-- Usuario administrador inicial por defecto
INSERT IGNORE INTO users (id, username, display_name, role, pin_hash, avatar_emoji, avatar_color_hex, bio) VALUES
(1, 'admin', 'Administrador', 'Administrador', 'admin', '👑', 4280391411, 'Cuenta administradora familiar');
