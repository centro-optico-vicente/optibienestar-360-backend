# ADR 0001 (local backend) — Jetty embebido sobre Tomcat

**Estado:** Aceptado
**Fecha:** 2026-05-18 (decisión ya tomada en `build.gradle` inicial)

## Contexto

Spring Boot trae Tomcat embebido por default. Se necesita elegir entre Tomcat / Jetty / Undertow.

## Decisión

Usar **Jetty embebido** (excluir Tomcat del starter web).

```groovy
implementation('org.springframework.boot:spring-boot-starter-web') {
    exclude module: 'spring-boot-starter-tomcat'
}
implementation 'org.springframework.boot:spring-boot-starter-jetty'
```

## Por qué

- **Menor consumo de memoria** que Tomcat para perfil similar (relevante en VPS con 12GB compartidos con DB, Redis, frontend, traefik).
- **Mejor performance** en escenarios con muchas conexiones concurrentes (relevante para el validador en tiempo real).
- **Configuración más simple** del thread pool (`server.jetty.threads.min/max`).
- **Igual de maduro** que Tomcat en Spring Boot.

## Alternativas

- **Tomcat:** default, más común, pero más memoria.
- **Undertow:** más rápido en benchmarks, pero menos documentación y experiencia del equipo.

## Consecuencias

- Stack traces de errores HTTP mencionan Jetty (vs Tomcat) — no impacto real.
- Tuning específico de Jetty en `application.properties`:
  ```properties
  server.jetty.threads.min=10
  server.jetty.threads.max=100
  server.jetty.threads.max-queue-capacity=200
  server.jetty.threads.idle-timeout=60000
  ```
