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

-- 2. Tabla de Publicaciones (Muro)
CREATE TABLE IF NOT EXISTS posts (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    content_text TEXT NULL,
    feeling VARCHAR(100) NULL,
    location VARCHAR(100) NULL,
    is_pinned TINYINT(1) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_post_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_created (created_at DESC)
) ENGINE=InnoDB;

-- 3. Tabla de Multimedia (Fotos y Vídeos en el NAS)
CREATE TABLE IF NOT EXISTS media (
    id INT AUTO_INCREMENT PRIMARY KEY,
    post_id INT NOT NULL,
    media_type ENUM('IMAGE', 'VIDEO') NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    thumb_path VARCHAR(255) NOT NULL,
    duration_label VARCHAR(20) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_media_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
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

-- Miembros iniciales reales
INSERT IGNORE INTO users (id, username, display_name, role, pin_hash, avatar_emoji, avatar_color_hex, bio) VALUES
(1, 'papa', 'Carlos (Papá)', 'Papá', '1234', '👨‍💼', 4280391411, 'Organizador de los asados de domingo 🍖'),
(2, 'mama', 'Laura (Mamá)', 'Mamá', '1234', '👩‍🌾', 4293077672, 'Fotógrafa de las plantas y viajes familiares 🌸'),
(3, 'hija', 'Sofía', 'Hija Mayor', '1234', '👧', 4287518975, 'Estudiante y fan de la música 🎸'),
(4, 'hijo', 'Mateo', 'Hijo Menor', '1234', '👦', 4283151817, 'Fútbol y ciencia ⚽'),
(5, 'abuela', 'Rosa (Abuela)', 'Abuela', '1234', '👵', 4294432139, 'Recetas tradicionales de la familia 🥧');
