/**
 * @file PanelRio.jsx
 * Componente de panel lateral que muestra la información detallada de un río:
 * niveles de alerta, caudal, acumulados de lluvia, medidor gráfico, pronósticos y opciones de seguimiento.
 * Exporta el componente PanelRio por defecto.
 */

import { useMemo } from 'react'
import { useAuth } from '../auth.jsx'
import { fechaCorta, fmt, visual } from '../estados.js'
import BotonPush from './BotonPush.jsx'
import BotonSimular from './BotonSimular.jsx'
import Medidor from './Medidor.jsx'
import Muestras from './Muestras.jsx'
import Pronostico from './Pronostico.jsx'
import PronosticoLluvia from './PronosticoLluvia.jsx'
import TituloRio from './TituloRio.jsx'

function Lista({ titulo, items, clase }) {
  if (!items?.length) return null
  return (
    <section className={`lista ${clase ?? ''}`}>
      <h3>{titulo}</h3>
      <ul>
        {items.map((t) => (
          <li key={t}>{t}</li>
        ))}
      </ul>
    </section>
  )
}

/**
 * Panel informativo y de control para un río seleccionado.
 *
 * @param {Object} props - Propiedades del componente.
 * @param {number|string} props.rioId - Identificador del río en la base de datos.
 * @param {string} props.nombre - Nombre del río a mostrar como respaldo mientras carga.
 * @param {Object|null} props.estado - DTO con el estado hidrológico, pronósticos y umbrales del río.
 * @param {boolean} [props.cargando=false] - Indica si la información del río está siendo consultada.
 * @param {string} [props.error] - Mensaje de error en caso de fallo al cargar los datos.
 * @param {function(): void} [props.onReintentar] - Función para reintentar la carga del estado.
 * @param {boolean} [props.siguiendo=false] - Indica si el usuario autenticado sigue actualmente este río.
 * @param {boolean} [props.ocupado=false] - Indica si hay una acción en curso (por ejemplo, alternar seguimiento).
 * @param {function(): void} [props.onAlternar] - Función invocada para seguir o dejar de seguir el río.
 * @param {boolean} [props.mostrarPush=false] - Indica si se debe mostrar el botón para configurar notificaciones push.
 * @param {boolean} [props.mostrarSimulacion=true] - Indica si se permite mostrar el botón de simulación a administradores.
 * @param {function(string): Promise<void>} [props.onRenombrar] - Guarda un nuevo nombre; solo se ofrece a ADMIN e INVESTIGADOR.
 * @returns {JSX.Element} Contenedor con la ficha técnica y controles del río.
 */
export default function PanelRio({
  rioId,
  nombre,
  estado: d,
  cargando,
  error,
  onReintentar,
  siguiendo,
  ocupado,
  onAlternar,
  mostrarPush = false,
  mostrarSimulacion = true,
  onRenombrar,
}) {
  const { usuario, token } = useAuth()
  const v = visual(d)
  const diaPico = useMemo(
    () => (d?.pronosticoDiario ?? []).reduce((m, x) => (!m || x.caudalMaximo > m.caudalMaximo ? x : m), null),
    [d],
  )

  const peligro = Boolean(d && (d.nivelAlerta === 'ALERTA' || d.nivelAlerta === 'EMERGENCIA'))
  const caudalGrafico = diaPico?.caudalMaximo ?? d?.caudalMaximoPronosticado
  const esAdmin = usuario?.rol === 'ADMIN'
  const puedeRenombrar = Boolean(onRenombrar) && (esAdmin || usuario?.rol === 'INVESTIGADOR')
  const titulo = <TituloRio key={rioId} nombre={nombre || d?.nombreRio} onRenombrar={puedeRenombrar ? onRenombrar : undefined} />

  return (
    <div className="panel__cuerpo" style={d ? { '--est-color': v.color, '--est-fondo': v.fondo } : undefined}>
      {!d ? (
        <>
          {titulo}
          {cargando && (
            <>
              <p className="aviso">Consultando el pronóstico… puede tardar unos segundos.</p>
              <div className="carga" role="progressbar" aria-label="Consultando el pronóstico" aria-busy="true"><div className="carga__barra" /></div>
            </>
          )}
          {error && (
            <>
              <p className="error" role="alert">{error}</p>
              <button type="button" className="btn btn--sec" onClick={onReintentar}>Reintentar</button>
            </>
          )}
        </>
      ) : (
        <>
          {peligro && d.datosConfiables !== false ? (
            <div className="banner" role="alert">
              <span aria-hidden="true">⚠</span> {v.etiqueta.toUpperCase()}
            </div>
          ) : (
            <span className="insignia">{v.etiqueta.toUpperCase()}</span>
          )}
          {d.simulacion && <span className="insignia insignia--sim">SIMULACIÓN</span>}
          {titulo}
          <p className="k">El pronóstico corresponde a un punto de referencia del modelo GEOGLOWS (círculo en el mapa, ubicación aproximada), no a todo el cauce.</p>

          {esAdmin && mostrarSimulacion && (
            <BotonSimular reachId={d.reachId} nombre={nombre || d.nombreRio} nota />
          )}

          <div className="cifras">
            <div><p className="k">Caudal actual</p><p className="v">{fmt(d.caudalActualOInicial)} m³/s</p></div>
            <div><p className="k">Pico pronosticado</p><p className="v">{fmt(d.caudalMaximoPronosticado)} m³/s</p></div>
            <div><p className="k">Lluvia 24 h</p><p className="v v--chico">{fmt(d.lluvia24hMm, 0)} mm</p></div>
            <div><p className="k">Lluvia 72 h</p><p className="v v--chico">{fmt(d.lluvia72hMm, 0)} mm</p></div>
          </div>

          <p className="k">
            Día de mayor caudal{diaPico ? `: ${fechaCorta(diaPico.fecha)}` : ''} (nivel relativo)
          </p>
          <Medidor caudal={caudalGrafico} t1={d.umbralVigilancia} t2={d.umbralAlerta} t3={d.umbralEmergencia} color={v.trazo} />

          <Lista titulo="Recomendaciones" items={d.recomendaciones} clase={peligro ? 'lista--peligro' : ''} />
          <Lista titulo="Nota sobre los datos" items={d.advertencias} />

          <p className="k">Caudal máximo previsto por día</p>
          <Pronostico dias={d.pronosticoDiario} t1={d.umbralVigilancia} t2={d.umbralAlerta} t3={d.umbralEmergencia} />

          <p className="k">Lluvia total prevista por día (mm)</p>
          <PronosticoLluvia puntos={d.pronosticoLluvia} />

          {onAlternar && (
            <button
              type="button"
              className={`btn ${siguiendo ? 'btn--sec' : peligro ? 'btn--peligro' : ''}`}
              disabled={ocupado}
              onClick={onAlternar}
            >
              {ocupado
                ? 'Guardando…'
                : !usuario || !token
                  ? 'Inicia sesión para seguir este río y recibir avisos'
                  : siguiendo
                    ? 'Dejar de seguir este río'
                    : 'Suscribirme a este río'}
            </button>
          )}

          {mostrarPush && <BotonPush />}
        </>
      )}

      {rioId && <Muestras key={rioId} rioId={rioId} />}
    </div>
  )
}
