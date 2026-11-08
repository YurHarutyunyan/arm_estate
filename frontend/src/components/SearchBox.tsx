import { useEffect, useId, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'

interface Option {
  key: string
  kind: 'district' | 'listing'
  label: string
  detail: string
  value: string
}

interface Props {
  initialValue: string
  onSearch: (text: string) => void
  onPickDistrict: (district: string) => void
}

/**
 * Search input with autocomplete, following the ARIA combobox pattern: arrow keys move through the
 * suggestions, Enter picks one (or searches the typed text), Escape closes the list.
 */
export function SearchBox({ initialValue, onSearch, onPickDistrict }: Props) {
  const navigate = useNavigate()
  const listId = useId()
  const [text, setText] = useState(initialValue)
  const [debounced, setDebounced] = useState('')
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const wrapper = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(text.trim()), 200)
    return () => window.clearTimeout(timer)
  }, [text])

  const { data } = useQuery({
    queryKey: ['suggest', debounced],
    queryFn: ({ signal }) => api.suggest(debounced, signal),
    enabled: open && debounced.length >= 2,
    staleTime: 60_000,
  })

  const options: Option[] = [
    ...(data?.districts ?? []).map((d) => ({ key: `d-${d.value}`, kind: 'district' as const, label: d.label, detail: 'District', value: d.value })),
    ...(data?.listings ?? []).map((l) => ({ key: `l-${l.id}`, kind: 'listing' as const, label: l.title, detail: l.districtName, value: l.id })),
  ]
  const showList = open && debounced.length >= 2 && options.length > 0

  // Close when clicking outside
  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      if (!wrapper.current?.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onClick)
    return () => document.removeEventListener('mousedown', onClick)
  }, [])

  const pick = (option: Option) => {
    setOpen(false)
    if (option.kind === 'district') {
      setText('')
      onPickDistrict(option.value)
    } else {
      navigate(`/listings/${option.value}`)
    }
  }

  const onKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setOpen(true)
      setActive((i) => (options.length === 0 ? -1 : (i + 1) % options.length))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActive((i) => (options.length === 0 ? -1 : (i - 1 + options.length) % options.length))
    } else if (e.key === 'Enter') {
      e.preventDefault()
      if (showList && active >= 0) pick(options[active])
      else {
        setOpen(false)
        onSearch(text.trim())
      }
    } else if (e.key === 'Escape') {
      setOpen(false)
      setActive(-1)
    }
  }

  let lastKind: Option['kind'] | null = null

  return (
    <div className="searchbox" ref={wrapper}>
      <svg className="searchbox__icon" width="20" height="20" viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="11" cy="11" r="7" fill="none" stroke="currentColor" strokeWidth="2" />
        <path d="m20 20-4-4" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
      </svg>
      <input
        className="searchbox__input"
        type="search"
        role="combobox"
        aria-label="Search listings"
        aria-autocomplete="list"
        aria-expanded={showList}
        aria-controls={listId}
        aria-activedescendant={showList && active >= 0 ? `${listId}-${active}` : undefined}
        placeholder="Try “balcony Kentron”, “2 rooms near metro”, “квартира”…"
        value={text}
        maxLength={200}
        onChange={(e) => {
          setText(e.target.value)
          setOpen(true)
          setActive(-1)
        }}
        onFocus={() => setOpen(true)}
        onKeyDown={onKeyDown}
      />
      {showList && (
        <ul className="suggestions" id={listId} role="listbox" aria-label="Suggestions">
          {options.map((option, i) => {
            const header = option.kind !== lastKind
            lastKind = option.kind
            return (
              <li key={option.key} role="presentation">
                {header && (
                  <div className="suggestions__group" aria-hidden="true">
                    {option.kind === 'district' ? 'Districts' : 'Listings'}
                  </div>
                )}
                <div
                  id={`${listId}-${i}`}
                  role="option"
                  aria-selected={i === active}
                  className="suggestions__option"
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => pick(option)}
                  onMouseEnter={() => setActive(i)}
                >
                  <span>{option.label}</span>
                  <span className="muted">{option.detail}</span>
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
