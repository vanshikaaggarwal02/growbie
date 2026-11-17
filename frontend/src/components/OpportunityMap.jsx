const COL = { LOW: 0, MEDIUM: 1, HIGH: 2 }   // effort -> column
const ROW = { HIGH: 0, MEDIUM: 1, LOW: 2 }   // impact -> row
const FILL = { HIGH: '#C8F169', MEDIUM: '#DDE6D5', LOW: '#ECEBE0' }
// Mirrors the backend PriorityMatrix so cell shading matches the stored priority.
function priority(impact, effort) {
  if (impact === 'HIGH') return effort === 'HIGH' ? 'MEDIUM' : 'HIGH'
  if (impact === 'MEDIUM') return effort === 'HIGH' ? 'LOW' : 'MEDIUM'
  return 'LOW'
}
const IMPACTS = ['HIGH', 'MEDIUM', 'LOW']
const EFFORTS = ['LOW', 'MEDIUM', 'HIGH']

export default function OpportunityMap({ items }) {
  const seen = {}
  const dots = items.map((r) => {
    const key = `${r.impact}-${r.effort}`
    const k = (seen[key] = (seen[key] ?? -1) + 1)
    const x0 = 56 + COL[r.effort] * 100, y0 = 14 + ROW[r.impact] * 96
    return { n: r.position, cx: x0 + 28 + (k % 3) * 22, cy: y0 + 28 + Math.floor(k / 3) * 28 + (k % 2) * 6 }
  })
  return (
    <svg className="map" viewBox="0 0 380 350" role="img" aria-label={`Opportunity map placing ${items.length} recommendations by impact and effort`}>
      <g stroke="#17261C" strokeWidth="1.5">
        {IMPACTS.flatMap((im, r) => EFFORTS.map((ef, c) => (
          <rect key={`${im}${ef}`} x={56 + c * 100} y={14 + r * 96} width="100" height="96" fill={FILL[priority(im, ef)]} />)))}
      </g>
      <g fontSize="10.5" fontWeight="700" fill="#44564A" letterSpacing="1">
        {IMPACTS.flatMap((im, r) => EFFORTS.map((ef, c) => (
          <text key={`${im}${ef}`} x={64 + c * 100} y={102 + r * 96}>{priority(im, ef) === 'HIGH' && im === 'HIGH' && ef === 'LOW' ? 'DO FIRST' : priority(im, ef)}</text>)))}
      </g>
      <g fontSize="11" fontWeight="700" fill="#17261C" textAnchor="end"><text x="48" y="66">HIGH</text><text x="48" y="162">MED</text><text x="48" y="258">LOW</text></g>
      <g fontSize="11" fontWeight="700" fill="#17261C" textAnchor="middle"><text x="106" y="320">LOW</text><text x="206" y="320">MEDIUM</text><text x="306" y="320">HIGH</text>
        <text x="206" y="341" letterSpacing="2" fill="#55675A">EFFORT →</text><text transform="translate(12 158) rotate(-90)" letterSpacing="2" fill="#55675A">IMPACT →</text></g>
      {dots.map((d) => (<g key={d.n}><circle cx={d.cx} cy={d.cy} r="13" fill="#17261C" />
        <text x={d.cx} y={d.cy + 4} fill="#C8F169" fontSize="12" fontWeight="800" textAnchor="middle">{d.n}</text></g>))}
    </svg>
  )
}
