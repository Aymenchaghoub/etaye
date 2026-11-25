import type { Plot, PlotVariant } from './measurements';

/**
 * One chart of S-11: an SVG built by hand from the `.etaye-courbe` recipe. It is an image with a text alternative that states
 * every plotted value; the table below it is the reference. Markers are the observations, a segment joins two consecutive
 * points and carries no value, and nothing is smoothed, extended or compared with a threshold.
 */
export function Courbe({ plot, variant, label }: { plot: Plot; variant: PlotVariant; label: string }) {
  return (
    <svg aria-label={label} className={`etaye-courbe etaye-courbe--${variant === 'wide' ? 'large' : 'etroite'}`} role="img" viewBox={plot.viewBox}>
      {plot.ticks.map((tick, index) => (
        <g key={index}>
          <line className="grille" x1={plot.x0} x2={plot.x1} y1={tick.y} y2={tick.y} />
          {tick.label !== undefined && <text className="axe" textAnchor="end" x={plot.x0 - 8} y={tick.y + 4}>{tick.label}</text>}
        </g>
      ))}
      {plot.lines.map((line) => (
        <polyline key={line.className} className={line.className} points={line.points.map((point) => `${point.x},${point.y}`).join(' ')} />
      ))}
      {plot.lines.map((line, index) => line.points.map((point, at) => (
        <g key={`${line.className}-${at}`}>
          <circle className={index === 0 ? 'point' : 'point-2'} cx={point.x} cy={point.y} r={4} />
          {point.label !== undefined && <text className="val" textAnchor="middle" x={point.x} y={point.labelY}>{point.label}</text>}
        </g>
      )))}
      {plot.dates.map((date, index) => <text key={index} className="axe" textAnchor="middle" x={date.x} y={plot.dateY}>{date.text}</text>)}
      {plot.unit !== undefined && <text className="axe" x={plot.x0} y={14}>{plot.unit}</text>}
    </svg>
  );
}
