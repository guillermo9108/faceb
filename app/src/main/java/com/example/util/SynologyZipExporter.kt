package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.SynologyScripts
import com.example.model.NasConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SynologyZipExporter {

    fun createZipBytes(nasConfig: NasConfig): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zipOut ->
            fun addEntry(filename: String, content: String) {
                val entry = ZipEntry(filename)
                zipOut.putNextEntry(entry)
                zipOut.write(content.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }

            // 1. Script SQL para MariaDB / MySQL
            addEntry("schema.sql", SynologyScripts.schemaSql)

            // 2. Archivo config.php con el puerto y host de base de datos configurados por el usuario
            val customConfigPhp = SynologyScripts.generateConfigPhp(
                dbHost = nasConfig.dbHost,
                dbPort = nasConfig.dbPort,
                dbName = nasConfig.dbName
            )
            addEntry("config.php", customConfigPhp)

            // 3. API REST index.php completa para Web Station
            addEntry("index.php", SynologyScripts.indexPhp)

            // 4. Archivo .htaccess para Web Station / Apache con soporte de subidas pesadas
            addEntry(
                ".htaccess",
                """
                # Configuración recomendada para Synology Web Station (Apache / Nginx)
                # Permite subida de fotos en alta resolución y vídeos familiares
                php_value upload_max_filesize 512M
                php_value post_max_size 512M
                php_value memory_limit 512M
                php_value max_execution_time 300
                php_value max_input_time 300
                """.trimIndent()
            )

            // 5. docker-compose.yml opcional para Container Manager en Synology o PC
            addEntry(
                "docker-compose.yml",
                """
                # Alternativa: Levantar MariaDB y PHP con Docker / Container Manager en Synology
                version: '3.8'

                services:
                  mariadb:
                    image: mariadb:10.11
                    container_name: family_mariadb
                    restart: always
                    environment:
                      MYSQL_ROOT_PASSWORD: TuClaveSynology123
                      MYSQL_DATABASE: ${nasConfig.dbName}
                    ports:
                      - "${nasConfig.dbPort}:3306"
                    volumes:
                      - ./mariadb_data:/var/lib/mysql
                      - ./schema.sql:/docker-entrypoint-initdb.d/schema.sql

                  web_api:
                    image: php:8.2-apache
                    container_name: family_web_api
                    restart: always
                    ports:
                      - "${nasConfig.port}:80"
                    volumes:
                      - ./:/var/www/html
                    depends_on:
                      - mariadb
                """.trimIndent()
            )

            // 6. Guía paso a paso de instalación en Synology DSM
            addEntry(
                "LEEME_INSTRUCCIONES.txt",
                """
                ======================================================================
                   PAQUETE DE CONFIGURACIÓN - MURO FAMILIAR LAN EN SYNOLOGY NAS
                ======================================================================

                Parámetros configurados en este ZIP:
                - Puerto Servidor Web (Web Station): ${nasConfig.port}
                - Ruta relativa API: ${nasConfig.path}
                - Puerto Base de Datos (MariaDB/MySQL): ${nasConfig.dbPort}
                - Host Base de Datos: ${nasConfig.dbHost}
                - Nombre Base de Datos: ${nasConfig.dbName}

                ----------------------------------------------------------------------
                PASO 1: INSTALAR PAQUETES EN SYNOLOGY DSM
                ----------------------------------------------------------------------
                1. Abre el Centro de Paquetes en tu Synology DSM.
                2. Instala los siguientes paquetes:
                   - Web Station
                   - PHP 8.2 (o PHP 8.0/8.1)
                   - MariaDB 10
                   - phpMyAdmin (opcional, para gestionar la base de datos visualmente)

                ----------------------------------------------------------------------
                PASO 2: CREAR LA BASE DE DATOS EN MARIADB (PUERTO ${nasConfig.dbPort})
                ----------------------------------------------------------------------
                1. Abre phpMyAdmin o conéctate vía terminal SSH.
                2. Abre la pestaña 'Importar' o 'SQL' y ejecuta el archivo 'schema.sql'
                   incluido en este archivo ZIP.
                3. Se creará la base de datos '${nasConfig.dbName}' con todas las tablas
                   (usuarios, publicaciones, fotos/vídeos, comentarios y reacciones).

                ----------------------------------------------------------------------
                PASO 3: COPIAR LOS ARCHIVOS AL SERVIDOR WEB DEL NAS
                ----------------------------------------------------------------------
                1. En File Station, ve a la carpeta compartida 'web'.
                2. Crea una subcarpeta llamada 'family_api' (/volume1/web/family_api).
                3. Descomprime y copia en esa carpeta los archivos:
                   - config.php
                   - index.php
                   - .htaccess
                4. Abre 'config.php' y escribe tu contraseña de MariaDB en DB_PASS.
                5. Crea dentro de 'family_api' una carpeta llamada 'uploads'.
                   Asegúrate de otorgarle permisos de lectura y escritura (usuario 'http').

                ----------------------------------------------------------------------
                PASO 4: CONFIGURAR WEB STATION
                ----------------------------------------------------------------------
                1. Abre Web Station -> Servicios web.
                2. En tu perfil de PHP 8.2, asegúrate de activar las extensiones:
                   - pdo_mysql
                   - gd (necesaria para miniaturas WebP automáticas)
                   - openssl
                3. Ve a 'Portal web' -> 'Crear' -> Portal de servicio web:
                   - Tipo: Servicio web
                   - Raíz de documentos: web/family_api
                   - Tipo de portal: Basado en puertos
                   - Puerto HTTP: ${nasConfig.port}

                ----------------------------------------------------------------------
                PASO 5: CONECTAR LA APP ANDROID
                ----------------------------------------------------------------------
                1. Abre Muro Familiar LAN en tu móvil (conectado al Wi-Fi de la casa).
                2. En la pestaña 'Menú' -> 'Configuración Servidor Synology NAS':
                   - IP: ${nasConfig.ip}
                   - Puerto: ${nasConfig.port}
                   - Puerto BD: ${nasConfig.dbPort}
                3. Pulsa 'Probar' y el indicador cambiará a verde en línea.
                ======================================================================
                """.trimIndent()
            )
        }
        return bos.toByteArray()
    }

    fun exportToUri(context: Context, uri: Uri, nasConfig: NasConfig): Boolean {
        return try {
            val bytes = createZipBytes(nasConfig)
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(bytes)
                os.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun shareZip(context: Context, nasConfig: NasConfig): Intent? {
        return try {
            val bytes = createZipBytes(nasConfig)
            val file = File(context.cacheDir, "synology_family_config.zip")
            file.writeBytes(bytes)
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "Configuración Synology NAS - Muro Familiar")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Archivos de configuración (SQL, PHP, API y Guía DSM) para Synology NAS y MariaDB (Puerto ${nasConfig.dbPort})."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Intent.createChooser(sendIntent, "Descargar / Compartir Configuración ZIP")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
