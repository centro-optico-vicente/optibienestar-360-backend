# `.githooks/`

Git hooks versionados con el repo. Implementan la **regla cero** del
[ADR 0004 (PR & branch conventions)](../.ai/decisions/0004-pr-and-branch-conventions.md):
nunca commits directos a `main`.

Como Branch Protection / Rulesets de GitHub son funcionalidades **paid-only**
en repos privados (chocan con [ADR 0004 — Only free tools](../.ai/decisions/0009-code-conventions.md)),
este pre-push hook es el mecanismo client-side gratuito que cierra el agujero.

## Activación (una sola vez por clone)

```bash
git config core.hooksPath .githooks
```

Eso le dice a este clone que use los hooks de `.githooks/` en lugar de los de
`.git/hooks/` (que no se versiona). A partir de ahí, cualquier `git push`
o "Sync Changes" del IDE pasa por el pre-push antes de salir a la red.

Verificá que quedó:

```bash
git config --get core.hooksPath   # esperado: .githooks
```

## Qué hace cada hook

| Hook | Bloquea |
|---|---|
| [`pre-push`](pre-push) | Cualquier `git push` cuyo destino sea `refs/heads/main` (independiente del IDE o del comando exacto que dispare el push). |

## Bypass de emergencia

Si por una emergencia genuina necesitás saltarte el hook:

```bash
git push --no-verify
```

Es intencional que sea **una bandera explícita**, no oculta. Si te encontrás
escribiéndola, parate y preguntate si de verdad no podés crear un PR de una
línea con squash-merge inmediato.
