/**
 * A generated building illustration, unique per listing id. Used when a listing has no photo, so
 * the demo needs no image hosting and every card still looks different.
 */
export function ListingImage({ id, floors, label }: { id: string; floors: number; label: string }) {
  const seed = hash(id)
  const hue = 140 + (seed % 40)
  const sky = `hsl(${hue}, 45%, ${18 + (seed % 8)}%)`
  const building = `hsl(${hue}, 35%, ${30 + (seed % 10)}%)`
  const neighbor = `hsl(${hue}, 30%, 24%)`
  const windowLit = '#2ee59d'
  const rows = Math.min(Math.max(floors, 3), 9)
  const columns = 3 + (seed % 3)
  const width = 34 + columns * 14
  const height = 26 + rows * 14
  const left = 160 - width / 2

  const windows = []
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < columns; c++) {
      const lit = (seed >> ((r * columns + c) % 24)) & 1
      windows.push(
        <rect
          key={`${r}-${c}`}
          x={left + 14 + c * 14}
          y={200 - height + 14 + r * 14}
          width="8"
          height="8"
          rx="1.5"
          fill={lit ? windowLit : '#0a3a2d'}
          opacity={lit ? 0.85 : 1}
        />,
      )
    }
  }

  return (
    <svg className="listing-image" viewBox="0 0 320 200" role="img" aria-label={label} preserveAspectRatio="xMidYMid slice">
      <rect width="320" height="200" fill={sky} />
      <circle cx={60 + (seed % 200)} cy={42} r="16" fill="#2ee59d" opacity="0.25" />
      <path d={`M0 200 L0 ${150 - (seed % 20)} L70 ${120 - (seed % 15)} L130 ${160} L320 ${140 - (seed % 25)} L320 200 Z`} fill={neighbor} opacity="0.6" />
      <rect x={left - 52} y={200 - height * 0.6} width="44" height={height * 0.6} fill={neighbor} />
      <rect x={left + width + 10} y={200 - height * 0.75} width="52" height={height * 0.75} fill={neighbor} />
      <rect x={left} y={200 - height} width={width} height={height} rx="4" fill={building} />
      {windows}
    </svg>
  )
}

function hash(text: string) {
  let h = 2166136261
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}
