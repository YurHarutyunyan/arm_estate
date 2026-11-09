import { useEffect, useState } from 'react'
import type { Bucket, SearchResult } from '../api/types'

export interface FilterState {
  district: string[]
  rooms: string[]
  buildingType: string[]
  minPrice: string
  maxPrice: string
  furnished: string
  petsAllowed: string
}

interface Props {
  value: FilterState
  facets: SearchResult['facets'] | undefined
  open: boolean
  onChange: (next: Partial<FilterState>) => void
  onClear: () => void
}

/** Filter sidebar. Counts come from facets that ignore their own filter, so options never vanish. */
export function Filters({ value, facets, open, onChange, onClear }: Props) {
  const toggle = (key: 'district' | 'rooms' | 'buildingType', item: string) => {
    const current = value[key]
    onChange({ [key]: current.includes(item) ? current.filter((v) => v !== item) : [...current, item] })
  }

  return (
    <aside className={`card filters${open ? ' filters--open' : ''}`} aria-label="Filters" id="filters">
      <FacetGroup
        legend="District"
        buckets={facets?.districts}
        selected={value.district}
        onToggle={(v) => toggle('district', v)}
      />
      <FacetGroup
        legend="Rooms"
        buckets={facets?.rooms}
        selected={value.rooms}
        onToggle={(v) => toggle('rooms', v)}
        format={(b) => `${b.label} ${b.label === '1' ? 'room' : 'rooms'}`}
      />
      <PriceFilter
        min={value.minPrice}
        max={value.maxPrice}
        range={facets?.price}
        onChange={(minPrice, maxPrice) => onChange({ minPrice, maxPrice })}
      />
      <FacetGroup
        legend="Building"
        buckets={facets?.buildingTypes}
        selected={value.buildingType}
        onToggle={(v) => toggle('buildingType', v)}
      />
      <fieldset>
        <legend>Other</legend>
        <label className="check">
          <input
            type="checkbox"
            checked={value.furnished === 'true'}
            onChange={(e) => onChange({ furnished: e.target.checked ? 'true' : '' })}
          />
          Furnished
        </label>
        <label className="check">
          <input
            type="checkbox"
            checked={value.petsAllowed === 'true'}
            onChange={(e) => onChange({ petsAllowed: e.target.checked ? 'true' : '' })}
          />
          Pets allowed
        </label>
      </fieldset>
      <button type="button" className="button button--ghost" onClick={onClear}>
        Clear filters
      </button>
    </aside>
  )
}

function FacetGroup({
  legend,
  buckets,
  selected,
  onToggle,
  format = (b) => b.label,
}: {
  legend: string
  buckets: Bucket[] | undefined
  selected: string[]
  onToggle: (value: string) => void
  format?: (bucket: Bucket) => string
}) {
  // Keep selected options visible even when they have no matches right now
  const shown = [...(buckets ?? [])]
  for (const value of selected) {
    if (!shown.some((b) => b.value === value)) shown.push({ value, label: value, count: 0 })
  }
  if (shown.length === 0) return null

  return (
    <fieldset>
      <legend>{legend}</legend>
      {shown.map((bucket) => (
        <label className="check" key={bucket.value}>
          <input type="checkbox" checked={selected.includes(bucket.value)} onChange={() => onToggle(bucket.value)} />
          {format(bucket)}
          <span className="check__count" aria-label={`${bucket.count} listings`}>
            {bucket.count}
          </span>
        </label>
      ))}
    </fieldset>
  )
}

function PriceFilter({
  min,
  max,
  range,
  onChange,
}: {
  min: string
  max: string
  range: { min: number | null; max: number | null } | undefined
  onChange: (min: string, max: string) => void
}) {
  const [draftMin, setDraftMin] = useState(min)
  const [draftMax, setDraftMax] = useState(max)

  // Follow the URL when it changes from elsewhere (Clear filters, Back button). Adjusting state
  // during render keeps the inputs mounted, so typing never loses focus.
  const [seen, setSeen] = useState({ min, max })
  if (seen.min !== min || seen.max !== max) {
    setSeen({ min, max })
    setDraftMin(min)
    setDraftMax(max)
  }

  // Apply after typing stops, so every keystroke doesn't start a search
  useEffect(() => {
    if (draftMin === min && draftMax === max) return
    const timer = window.setTimeout(() => onChange(draftMin, draftMax), 500)
    return () => window.clearTimeout(timer)
  }, [draftMin, draftMax, min, max, onChange])

  const invalid = draftMin !== '' && draftMax !== '' && Number(draftMin) > Number(draftMax)

  return (
    <fieldset>
      <legend>Monthly price (USD)</legend>
      <div className="price-inputs">
        <input
          className="input"
          type="number"
          inputMode="numeric"
          min={0}
          aria-label="Minimum price"
          placeholder={range?.min != null ? `from ${range.min}` : 'Min'}
          value={draftMin}
          onChange={(e) => setDraftMin(e.target.value)}
        />
        <input
          className="input"
          type="number"
          inputMode="numeric"
          min={0}
          aria-label="Maximum price"
          placeholder={range?.max != null ? `to ${range.max}` : 'Max'}
          value={draftMax}
          onChange={(e) => setDraftMax(e.target.value)}
        />
      </div>
      {invalid && <span className="field__error">Minimum is higher than maximum</span>}
    </fieldset>
  )
}
