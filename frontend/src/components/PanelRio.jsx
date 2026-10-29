import { useMemo } from 'react'
import { useAuth } from '../auth.jsx'
import { fechaCorta, fmt, visual } from '../estados.js'
import BotonPush from './BotonPush.jsx'
import BotonSimular from './BotonSimular.jsx'
import Medidor from './Medidor.jsx'
import Pronostico from './Pronostico.jsx'
import PronosticoLluvia from './PronosticoLluvia.jsx'

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

export default function PanelRio({
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
}) {
  const { usuario } = useAuth()
  const v = visual(d)
  const diaPico = useMemo(
    () => (d?.pronosticoDiario ?? []).reduce((m, x) => (!m || x.caudalMaximo > m.caudalMaximo ? x : m), null),
    [d],
  )

  if (!d) {
    return (
      <div className="panel__cuerpo">
        <h2 className="panel__titulo">{nombre}</h2>
        {cargando && <p className="aviso">Consultando el pronóstico… puede tardar unos segundos.</p>}
        {error && (
          <>
            <p className="error" role="alert">{error}</p>
            <button type="button" className="btn btn--sec" onClick={onReintentar}>Reintentar</button>
          </>
        )}
      </div>
    )
  }

  const peligro = d.nivelAlerta === 'ALERTA' || d.nivelAlerta === 'EMERGENCIA'
  const caudalGrafico = diaPico?.caudalMaximo ?? d.caudalMaximoPronosticado
  const esAdmin = usuario?.rol === 'ADMIN'

  return (
    <div className="panel__cuerpo" style={{ '--est-color': v.color, '--est-fondo': v.fondo }}>
      {peligro && d.datosConfiables !== false ? (
        <div className="banner" role="alert">
          <span aria-hidden="true">⚠</span> {v.etiqueta.toUpperCase()}
        </div>
      ) : (
        <span className="insignia">{v.etiqueta.toUpperCase()}</span>
      )}
      {d.simulacion && <span className="insignia insignia--sim">SIMULACIÓN</span>}
      <h2 className="panel__titulo">{d.nombreRio || nombre}</h2>
      <p className="k">El pronóstico corresponde al punto de referencia del modelo GEOGLOWS (círculo en el mapa), no a todo el cauce.</p>

      {esAdmin && mostrarSimulacion && (
        <BotonSimular reachId={d.reachId} nombre={d.nombreRio || nombre} nota />
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
          {ocupado ? 'Guardando…' : siguiendo ? 'Dejar de seguir este río' : 'Suscribirme a este río'}
        </button>
      )}

      {mostrarPush && <BotonPush />}
    </div>
  )
}
