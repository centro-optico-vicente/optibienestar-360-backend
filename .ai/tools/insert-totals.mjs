#!/usr/bin/env node
// Regenera la tabla de totales al inicio de cada archivo de checklist.
//
// Cada archivo en `.ai/checklists/*.md` abre con un resumen (Tareas / Hechas /
// Pendientes / % avance / Estado) entre los marcadores
// `<!-- resumen-totales:start -->` … `<!-- resumen-totales:end -->`.
// Este script recalcula esos totales desde los checkboxes reales del archivo y
// reescribe el bloque en su lugar. Es idempotente: correrlo dos veces no
// duplica nada.
//
// Uso (desde cualquier lado):
//   node .ai/tools/insert-totals.mjs
//
// Cuándo correrlo: después de marcar tareas `- [x]`, para que el resumen de
// cada archivo deje de derivar. NOTA: esto NO actualiza la tabla-dashboard de
// `.ai/checklist.md` — esos totales se mantienen aparte (ver la nota al pie de
// ese archivo). Mantener ambos con el mismo método de redondeo (round).
//
// El % usa el MISMO redondeo que el dashboard: round(100 × hechas / tareas).

import { readFileSync, writeFileSync, readdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve, basename } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const CHECKLISTS_DIR = resolve(__dirname, '..', 'checklists')

const START = '<!-- resumen-totales:start -->'
const END = '<!-- resumen-totales:end -->'

// Texto del "Estado" por archivo — espeja el dashboard de checklist.md. Las
// notas cortas ("hardening/tests", "read perms") son curadas; el resto se deja
// solo con la banda de color. Un archivo sin entrada acá cae al emoji por banda.
const ESTADO = {
  'fase-0-bootstrap': '✅ Completa',
  'fase-1-bootstrap-backend-spring-boot': '✅ Completa',
  'fase-2-afiliaciones-y-membresias': '🟡',
  'vertical-1-seguridad-y-autenticacion': '🟡 hardening/tests',
  'vertical-2-catalogos': '✅ Completa',
  'vertical-3-aliados': '🟡',
  'vertical-4-afiliados-y-familia': '🟠',
  'vertical-5-planes-y-membresias': '🟡',
  'vertical-6-pagos-manuales': '✅ Completa',
  'vertical-7-validador': '🟡',
  'vertical-8-promotores-comisiones-referidos': '🟠',
  'vertical-9-notificaciones-y-carnet': '🟠',
  'vertical-10-optimizacion-reportes-hardening': '🔴',
  'vertical-11-i18n': '✅ Completa',
  'vertical-12-subsidios-y-exoneraciones': '🔲',
}

/** Banda de color por % (fallback cuando el archivo no está en ESTADO). */
function band(pct, done) {
  if (done === 0) return '🔲'
  if (pct === 100) return '✅ Completa'
  if (pct >= 50) return '🟡'
  if (pct >= 10) return '🟠'
  return '🔴'
}

function block(done, open, estado) {
  const total = done + open
  const pct = total > 0 ? Math.round((100 * done) / total) : 0
  return [
    START,
    '| Tareas | Hechas | Pendientes | % avance | Estado |',
    '|---|---|---|---|---|',
    `| ${total} | ${done} | ${open} | ${pct}% | ${estado} |`,
    '',
    "_Snapshot — recontar con `grep -c '^- \\[x\\]'`. Panorama global: [checklist.md](../checklist.md)._",
    END,
  ].join('\n')
}

let touched = 0
for (const file of readdirSync(CHECKLISTS_DIR).filter(f => f.endsWith('.md')).sort()) {
  const key = file.replace(/\.md$/, '')
  const path = resolve(CHECKLISTS_DIR, file)
  let src = readFileSync(path, 'utf8')

  const done = (src.match(/^- \[x\]/gm) || []).length
  const open = (src.match(/^- \[ \]/gm) || []).length
  if (done + open === 0) { console.log(`skip (sin checkboxes): ${file}`); continue }

  const total = done + open
  const pct = Math.round((100 * done) / total)
  const estado = ESTADO[key] ?? band(pct, done)
  const blk = block(done, open, estado)

  if (src.includes(START)) {
    // Regenerar en su lugar.
    src = src.replace(new RegExp(`${START}[\\s\\S]*?${END}`), blk)
  } else {
    // Primera vez: insertar al inicio del contenido — antes de la primera
    // sección "## ", o si no hay, antes del primer checkbox.
    const lines = src.split('\n')
    let idx = lines.findIndex(l => /^## /.test(l))
    if (idx === -1) idx = lines.findIndex(l => /^- \[[ x]\]/.test(l))
    if (idx === -1) { console.log(`skip (sin ancla de inserción): ${file}`); continue }
    lines.splice(idx, 0, blk, '')
    src = lines.join('\n')
  }

  writeFileSync(path, src, 'utf8')
  console.log(`ok: ${file}  → ${done}/${total} (${pct}%)`)
  touched++
}
console.log(`\n${touched} archivos actualizados.`)
